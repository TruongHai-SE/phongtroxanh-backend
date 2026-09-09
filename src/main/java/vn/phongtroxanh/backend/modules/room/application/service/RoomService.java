package vn.phongtroxanh.backend.modules.room.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.location.GeocodingPort;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.room.domain.*;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.SavedRoomRepository;
import vn.phongtroxanh.backend.modules.room.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final SavedRoomRepository savedRoomRepository;
    private final UserRepository userRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final FileStoragePort fileStoragePort;
    private final GeocodingPort geocodingPort;
    private final RedisTemplate<String, Object> redisTemplate;

    public Page<RoomSummaryResponse> searchRooms(
            String district,
            String roomType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String keyword,
            String sortBy,
            int page,
            int limit) {

        int safeLimit = Math.min(Math.max(1, limit), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeLimit);

        Page<Room> roomPage = roomRepository.searchRooms(
                district, roomType, minPrice, maxPrice, keyword, sortBy, pageable);

        return roomPage.map(this::mapToSummaryResponse);
    }

    public List<MapPinResponse> getRoomsOnMap(
            double lat,
            double lng,
            Double radiusKm,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String district,
            String roomType) {

        double radiusMeters = (radiusKm != null && radiusKm > 0 ? radiusKm : 5.0) * 1000.0;
        List<Room> rooms = roomRepository.findRoomsInRadius(lat, lng, radiusMeters, minPrice, maxPrice, district, roomType);

        return rooms.stream().map(r -> {
            String primaryImage = r.getImages() != null ? r.getImages().stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .findFirst()
                    .map(RoomImage::getImageUrl)
                    .orElse(null) : null;

            return MapPinResponse.builder()
                    .id(r.getId())
                    .title(r.getTitle())
                    .price(r.getPrice())
                    .latitude(r.getLatitude())
                    .longitude(r.getLongitude())
                    .primaryImageUrl(primaryImage)
                    .roomType(r.getRoomType())
                    .isBoosted(r.getIsBoosted())
                    .build();
        }).toList();
    }

    public RoomCompareResponse compareRooms(List<UUID> roomIds) {
        if (roomIds == null || roomIds.size() < 2 || roomIds.size() > 4) {
            throw new BadRequestException("INVALID_COMPARE_COUNT", "Chỉ so sánh từ 2 đến 4 phòng cùng lúc");
        }

        List<RoomDetailResponse> details = roomIds.stream()
                .map(this::getRoomDetail)
                .toList();

        return new RoomCompareResponse(details);
    }

    @Transactional
    public RoomDetailResponse getRoomDetail(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy thông tin phòng trọ"));

        // Increment view count in Redis & sync
        String viewKey = "room:views:" + roomId;
        String dailyViewKey = "room:views:daily:" + roomId + ":" + LocalDate.now().toString();
        redisTemplate.opsForValue().increment(dailyViewKey);
        redisTemplate.expire(dailyViewKey, 30, TimeUnit.DAYS);

        Long currentViews = redisTemplate.opsForValue().increment(viewKey);
        if (currentViews != null && currentViews % 5 == 0) {
            room.setViewCount(room.getViewCount() + 5);
            roomRepository.save(room);
        }

        List<RoomImageDTO> images = room.getImages() != null ? room.getImages().stream()
                .map(img -> RoomImageDTO.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .isPrimary(img.getIsPrimary())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList() : List.of();

        List<RoomFeeDTO> fees = room.getFees() != null ? room.getFees().stream()
                .map(f -> RoomFeeDTO.builder()
                        .id(f.getId())
                        .feeLabel(f.getFeeLabel())
                        .feeValue(f.getFeeValue())
                        .build())
                .toList() : List.of();

        User landlord = userRepository.findById(room.getLandlordId()).orElse(null);
        RoomDetailResponse.LandlordSummaryDTO landlordDTO = null;
        if (landlord != null) {
            landlordDTO = RoomDetailResponse.LandlordSummaryDTO.builder()
                    .id(landlord.getId())
                    .fullName(landlord.getFullName())
                    .phoneNumber(landlord.getPhoneNumber())
                    .avatarUrl(landlord.getAvatarUrl())
                    .trustScore(landlord.getTrustScore())
                    .isVerified(landlord.getIsVerified())
                    .build();
        }

        return RoomDetailResponse.builder()
                .id(room.getId())
                .title(room.getTitle())
                .description(room.getDescription())
                .roomType(room.getRoomType())
                .price(room.getPrice())
                .depositAmount(room.getDepositAmount())
                .areaSqm(room.getAreaSqm())
                .floorNumber(room.getFloorNumber())
                .maxOccupants(room.getMaxOccupants())
                .addressStreet(room.getAddressStreet())
                .district(room.getDistrict())
                .city(room.getCity())
                .latitude(room.getLatitude())
                .longitude(room.getLongitude())
                .status(room.getStatus())
                .isVerified(room.getIsVerified())
                .isBoosted(room.getIsBoosted())
                .viewCount(room.getViewCount())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .images(images)
                .fees(fees)
                .landlord(landlordDTO)
                .build();
    }

    @Transactional
    public RoomDetailResponse createRoom(CreateRoomRequest request) {
        UUID landlordId = SecurityUtils.getCurrentUserId();

        Double lat = request.getLatitude();
        Double lng = request.getLongitude();

        if (lat == null || lng == null) {
            String fullAddress = request.getAddressStreet() + ", " + request.getDistrict() + ", " + (request.getCity() != null ? request.getCity() : "TP.HCM");
            try {
                var coordOpt = geocodingPort.geocodeAddress(fullAddress);
                if (coordOpt.isPresent()) {
                    lat = coordOpt.get().latitude();
                    lng = coordOpt.get().longitude();
                } else {
                    lat = 10.762622;
                    lng = 106.660172;
                }
            } catch (Exception e) {
                lat = 10.762622;
                lng = 106.660172;
            }
        }

        Room room = Room.builder()
                .landlordId(landlordId)
                .title(request.getTitle())
                .description(request.getDescription())
                .roomType(request.getRoomType())
                .price(request.getPrice())
                .depositAmount(request.getDepositAmount())
                .areaSqm(request.getAreaSqm())
                .floorNumber(request.getFloorNumber() != null ? request.getFloorNumber() : 1)
                .maxOccupants(request.getMaxOccupants() != null ? request.getMaxOccupants() : 2)
                .addressStreet(request.getAddressStreet())
                .district(request.getDistrict())
                .city(request.getCity() != null ? request.getCity() : "TP.HCM")
                .latitude(lat)
                .longitude(lng)
                .status(RoomStatus.AVAILABLE)
                .isVerified(false)
                .isBoosted(false)
                .viewCount(0L)
                .images(new ArrayList<>())
                .fees(new ArrayList<>())
                .build();

        if (request.getFees() != null) {
            for (RoomFeeDTO f : request.getFees()) {
                room.getFees().add(RoomFee.builder()
                        .room(room)
                        .feeLabel(f.getFeeLabel())
                        .feeValue(f.getFeeValue())
                        .build());
            }
        }

        room = roomRepository.save(room);
        return getRoomDetail(room.getId());
    }

    @Transactional
    public RoomDetailResponse updateRoom(UUID roomId, UpdateRoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        assertOwnership(room.getLandlordId());

        if (request.getTitle() != null) room.setTitle(request.getTitle());
        if (request.getDescription() != null) room.setDescription(request.getDescription());
        if (request.getRoomType() != null) room.setRoomType(request.getRoomType());
        if (request.getPrice() != null) room.setPrice(request.getPrice());
        if (request.getDepositAmount() != null) room.setDepositAmount(request.getDepositAmount());
        if (request.getAreaSqm() != null) room.setAreaSqm(request.getAreaSqm());
        if (request.getFloorNumber() != null) room.setFloorNumber(request.getFloorNumber());
        if (request.getMaxOccupants() != null) room.setMaxOccupants(request.getMaxOccupants());
        if (request.getAddressStreet() != null) room.setAddressStreet(request.getAddressStreet());
        if (request.getDistrict() != null) room.setDistrict(request.getDistrict());
        if (request.getCity() != null) room.setCity(request.getCity());
        if (request.getLatitude() != null) room.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) room.setLongitude(request.getLongitude());
        if (request.getLatitude() == null && request.getLongitude() == null && (request.getAddressStreet() != null || request.getDistrict() != null)) {
            String fullAddress = room.getAddressStreet() + ", " + room.getDistrict() + ", " + (room.getCity() != null ? room.getCity() : "TP.HCM");
            try {
                var coordOpt = geocodingPort.geocodeAddress(fullAddress);
                if (coordOpt.isPresent()) {
                    room.setLatitude(coordOpt.get().latitude());
                    room.setLongitude(coordOpt.get().longitude());
                }
            } catch (Exception e) {
                log.warn("Could not geocode updated address: {}", e.getMessage());
            }
        }
        if (request.getStatus() != null) room.setStatus(request.getStatus());

        if (request.getFees() != null) {
            room.getFees().clear();
            for (RoomFeeDTO f : request.getFees()) {
                room.getFees().add(RoomFee.builder()
                        .room(room)
                        .feeLabel(f.getFeeLabel())
                        .feeValue(f.getFeeValue())
                        .build());
            }
        }

        room = roomRepository.save(room);
        return getRoomDetail(room.getId());
    }

    @Transactional
    public void deleteRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        assertOwnership(room.getLandlordId());
        room.setStatus(RoomStatus.HIDDEN);
        roomRepository.save(room);
        log.info("Soft deleted room {}", roomId);
    }

    @Transactional
    public List<RoomImageDTO> uploadImages(UUID roomId, List<MultipartFile> files) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        assertOwnership(room.getLandlordId());

        int order = room.getImages().size();
        for (MultipartFile file : files) {
            String url = fileStoragePort.uploadFile(file, "rooms");
            RoomImage img = RoomImage.builder()
                    .room(room)
                    .imageUrl(url)
                    .isPrimary(order == 0)
                    .displayOrder(order++)
                    .build();
            room.getImages().add(img);
        }

        room = roomRepository.save(room);

        return room.getImages().stream()
                .map(img -> RoomImageDTO.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .isPrimary(img.getIsPrimary())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();
    }

    @Transactional
    public void deleteImage(UUID roomId, UUID imageId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        assertOwnership(room.getLandlordId());
        room.getImages().removeIf(img -> img.getId().equals(imageId));
        roomRepository.save(room);
    }

    public List<RoomSummaryResponse> getMyRooms() {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        return roomRepository.findByLandlordIdOrderByCreatedAtDesc(landlordId).stream()
                .map(this::mapToSummaryResponse)
                .toList();
    }

    @Transactional
    public void boostRoom(UUID roomId) {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        assertOwnership(room.getLandlordId());

        var consumable = userConsumableRepository.findById(landlordId)
                .orElseThrow(() -> new BadRequestException("NO_BOOST_BALANCE", "Bạn chưa có lượt đẩy tin. Vui lòng mua thêm gói Boost."));

        if (consumable.getBoostsLeft() <= 0) {
            throw new BadRequestException("INSUFFICIENT_BOOSTS", "Số lượt đẩy tin của bạn đã hết");
        }

        consumable.setBoostsLeft(consumable.getBoostsLeft() - 1);
        userConsumableRepository.save(consumable);

        room.setIsBoosted(true);
        room.setBoostExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        roomRepository.save(room);
        log.info("Landlord {} boosted room {}", landlordId, roomId);
    }

    @Transactional
    public void saveRoom(UUID roomId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("ROOM_NOT_FOUND", "Phòng trọ không tồn tại");
        }
        if (!savedRoomRepository.existsByUserIdAndRoomId(currentUserId, roomId)) {
            savedRoomRepository.save(SavedRoom.builder()
                    .userId(currentUserId)
                    .roomId(roomId)
                    .build());
            log.info("User {} saved room {}", currentUserId, roomId);
        }
    }

    @Transactional
    public void unsaveRoom(UUID roomId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        savedRoomRepository.deleteByUserIdAndRoomId(currentUserId, roomId);
        log.info("User {} unsaved room {}", currentUserId, roomId);
    }

    public List<RoomSummaryResponse> getSavedRooms() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<SavedRoom> savedList = savedRoomRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
        List<UUID> roomIds = savedList.stream().map(SavedRoom::getRoomId).toList();
        if (roomIds.isEmpty()) {
            return List.of();
        }

        List<Room> rooms = roomRepository.findAllById(roomIds);
        Map<UUID, Room> map = new HashMap<>();
        rooms.forEach(r -> map.put(r.getId(), r));

        return savedList.stream()
                .map(sr -> map.get(sr.getRoomId()))
                .filter(Objects::nonNull)
                .map(this::mapToSummaryResponse)
                .toList();
    }

    public LandlordAnalyticsResponse getLandlordAnalytics() {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        List<Room> rooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(landlordId);
        long total = rooms.size();
        long active = rooms.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
        long rented = rooms.stream().filter(r -> r.getStatus() == RoomStatus.RENTED).count();
        long views = rooms.stream().mapToLong(Room::getViewCount).sum();
        double occupancy = total > 0 ? (double) rented / total * 100 : 0.0;

        List<UUID> roomIds = rooms.stream().map(Room::getId).toList();
        long totalContacts = roomIds.isEmpty() ? 0L : savedRoomRepository.countByRoomIdIn(roomIds);

        Map<String, Long> viewsChart7Days = new LinkedHashMap<>();
        if (views > 0L && !rooms.isEmpty()) {
            LocalDate today = LocalDate.now();
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                String dateKey = d.toString(); // Standard ISO-8601: "YYYY-MM-DD"
                long dayViews = 0L;
                for (Room r : rooms) {
                    Object val = redisTemplate.opsForValue().get("room:views:daily:" + r.getId() + ":" + dateKey);
                    if (val != null) {
                        try {
                            dayViews += Long.parseLong(val.toString());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                viewsChart7Days.put(dateKey, dayViews);
            }
        }

        return LandlordAnalyticsResponse.builder()
                .totalRooms(total)
                .activeRooms(active)
                .rentedRooms(rented)
                .totalViews(views)
                .totalContacts(totalContacts)
                .occupancyRate(Math.round(occupancy * 10.0) / 10.0)
                .viewsChart7Days(viewsChart7Days)
                .build();
    }

    private void assertOwnership(UUID resourceOwnerId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (!currentUserId.equals(resourceOwnerId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền thao tác trên phòng này");
        }
    }

    private RoomSummaryResponse mapToSummaryResponse(Room r) {
        String primaryImage = r.getImages() != null ? r.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(RoomImage::getImageUrl)
                .orElse(null) : null;

        return RoomSummaryResponse.builder()
                .id(r.getId())
                .landlordId(r.getLandlordId())
                .title(r.getTitle())
                .roomType(r.getRoomType())
                .price(r.getPrice())
                .depositAmount(r.getDepositAmount())
                .areaSqm(r.getAreaSqm())
                .floorNumber(r.getFloorNumber())
                .maxOccupants(r.getMaxOccupants())
                .addressStreet(r.getAddressStreet())
                .district(r.getDistrict())
                .city(r.getCity())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .status(r.getStatus())
                .isVerified(r.getIsVerified())
                .isBoosted(r.getIsBoosted())
                .viewCount(r.getViewCount())
                .primaryImageUrl(primaryImage)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
