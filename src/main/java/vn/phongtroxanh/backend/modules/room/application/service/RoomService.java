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
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.common.location.GeocodingPort;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.common.storage.FileStoragePort;
import vn.phongtroxanh.backend.modules.room.domain.*;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomSwipeRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.SavedRoomRepository;
import vn.phongtroxanh.backend.modules.room.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomSwipeRepository roomSwipeRepository;
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
            boolean excludeSwiped,
            int page,
            int limit) {

        validatePriceRange(minPrice, maxPrice);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeLimit);

        UUID excludeUserId = excludeSwiped ? SecurityUtils.getCurrentUserIdSafely().orElse(null) : null;

        Page<Room> roomPage = roomRepository.searchRooms(
                district, roomType, minPrice, maxPrice, keyword, sortBy, excludeUserId, pageable);

        return roomPage.map(this::mapToSummaryResponse);
    }

    public Page<RoomSummaryResponse> searchRooms(
            String district,
            String roomType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String keyword,
            String sortBy,
            int page,
            int limit) {
        return searchRooms(district, roomType, minPrice, maxPrice, keyword, sortBy, false, page, limit);
    }

    public List<MapPinResponse> getRoomsOnMap(
            double lat,
            double lng,
            Double radiusKm,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String district,
            String roomType) {

        validateCoordinates(lat, lng);
        validatePriceRange(minPrice, maxPrice);
        if (radiusKm != null && (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 100)) {
            throw new BadRequestException("INVALID_RADIUS", "Bán kính tìm kiếm phải lớn hơn 0 và không vượt quá 100 km");
        }
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
                    .isBoosted(hasActiveBoost(r))
                    .district(r.getDistrict())
                    .areaSqm(r.getAreaSqm())
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

        User landlord = userRepository.findById(room.getLandlordId()).orElse(null);
        if (!isPublicRoom(room, landlord) && !canManage(room)) {
            throw new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy thông tin phòng trọ");
        }

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
                .isBoosted(hasActiveBoost(room))
                .viewCount(room.getViewCount())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .expiresAt(room.getExpiresAt())
                .images(images)
                .fees(fees)
                .amenities(room.getAmenities() != null ? room.getAmenities() : new ArrayList<>())
                .landlord(landlordDTO)
                .build();
    }

    @Transactional
    public RoomDetailResponse createRoom(CreateRoomRequest request) {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        validateDuplicateRoom(landlordId, request);

        Double lat = request.getLatitude();
        Double lng = request.getLongitude();
        validateCoordinates(lat, lng);

        if (lat == null) {
            String fullAddress = request.getAddressStreet() + ", " + request.getDistrict() + ", " + (request.getCity() != null ? request.getCity() : "TP.HCM");
            var coordinates = requireCoordinates(fullAddress);
            lat = coordinates.latitude();
            lng = coordinates.longitude();
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
                .amenities(request.getAmenities() != null ? new ArrayList<>(request.getAmenities()) : new ArrayList<>())
                .images(new ArrayList<>())
                .fees(new ArrayList<>())
                .build();

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            int order = 0;
            for (String imgUrl : request.getImages()) {
                room.getImages().add(RoomImage.builder()
                        .room(room)
                        .imageUrl(imgUrl)
                        .isPrimary(order == 0)
                        .displayOrder(order++)
                        .build());
            }
        }

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
        validateCoordinates(request.getLatitude(), request.getLongitude());

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
        if (request.getLatitude() == null && (request.getAddressStreet() != null || request.getDistrict() != null || request.getCity() != null)) {
            String fullAddress = room.getAddressStreet() + ", " + room.getDistrict() + ", " + (room.getCity() != null ? room.getCity() : "TP.HCM");
            var coordinates = requireCoordinates(fullAddress);
            room.setLatitude(coordinates.latitude());
            room.setLongitude(coordinates.longitude());
        }
        if (request.getStatus() != null) room.setStatus(request.getStatus());

        if (request.getAmenities() != null) {
            room.setAmenities(new ArrayList<>(request.getAmenities()));
        }

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            room.getImages().clear();
            int order = 0;
            for (String imgUrl : request.getImages()) {
                room.getImages().add(RoomImage.builder()
                        .room(room)
                        .imageUrl(imgUrl)
                        .isPrimary(order == 0)
                        .displayOrder(order++)
                        .build());
            }
        }

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

        if (room.getStatus() != RoomStatus.AVAILABLE || room.getExpiresAt() == null || !room.getExpiresAt().isAfter(Instant.now())) {
            throw new BadRequestException("ROOM_NOT_AVAILABLE", "Chỉ có thể đẩy tin phòng đang còn hiệu lực và có sẵn");
        }
        if (userConsumableRepository.decrementBoostAtomic(landlordId) == 0) {
            throw new BadRequestException("INSUFFICIENT_BOOSTS", "Số lượt đẩy tin của bạn đã hết");
        }

        Instant boostFrom = hasActiveBoost(room) ? room.getBoostExpiresAt() : Instant.now();
        room.setIsBoosted(true);
        room.setBoostExpiresAt(boostFrom.plus(7, ChronoUnit.DAYS));
        roomRepository.save(room);
        log.info("Landlord {} boosted room {}", landlordId, roomId);
    }

    @Transactional
    public RoomDetailResponse renewRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng"));

        assertOwnership(room.getLandlordId());

        Instant now = Instant.now();
        Instant currentExpiry = room.getExpiresAt() != null ? room.getExpiresAt() : now;
        Instant baseTime = currentExpiry.isAfter(now) ? currentExpiry : now;
        room.setExpiresAt(baseTime.plus(30, ChronoUnit.DAYS));

        if (room.getStatus() == RoomStatus.EXPIRED) {
            room.setStatus(RoomStatus.AVAILABLE);
        }

        room = roomRepository.save(room);
        log.info("Landlord {} renewed room {} until {}", room.getLandlordId(), room.getId(), room.getExpiresAt());
        return getRoomDetail(room.getId());
    }

    @Transactional
    public void saveRoom(UUID roomId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || !isPublicRoom(room, userRepository.findById(room.getLandlordId()).orElse(null))) {
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
    public void swipeRoom(UUID roomId, RoomSwipeRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || !isPublicRoom(room, userRepository.findById(room.getLandlordId()).orElse(null))) {
            throw new ResourceNotFoundException("ROOM_NOT_FOUND", "Phòng trọ không tồn tại");
        }

        // Deduct 1 swipe consumable atomically if available
        userConsumableRepository.resetDailySwipesAtomic(currentUserId, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        userConsumableRepository.decrementSwipeAtomic(currentUserId);

        String action = request.getAction().toUpperCase();
        RoomSwipe swipe = roomSwipeRepository.findByUserIdAndRoomId(currentUserId, roomId)
                .orElseGet(() -> RoomSwipe.builder()
                        .userId(currentUserId)
                        .roomId(roomId)
                        .build());
        swipe.setAction(action);
        swipe.setCreatedAt(Instant.now());
        roomSwipeRepository.save(swipe);

        if ("LIKE".equals(action)) {
            if (!savedRoomRepository.existsByUserIdAndRoomId(currentUserId, roomId)) {
                savedRoomRepository.save(SavedRoom.builder()
                        .userId(currentUserId)
                        .roomId(roomId)
                        .build());
            }
        }
        log.info("User {} swiped {} on room {}", currentUserId, action, roomId);
    }

    @Transactional
    public void resetSwipes() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        roomSwipeRepository.deleteAllByUserId(currentUserId);
        log.info("User {} reset all room swipes", currentUserId);
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
                .filter(r -> isPublicRoom(r, userRepository.findById(r.getLandlordId()).orElse(null)))
                .map(this::mapToSummaryResponse)
                .toList();
    }

    public LandlordAnalyticsResponse getLandlordAnalytics() {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        List<Room> rooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(landlordId);
        long total = rooms.size();
        long active = rooms.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE
                && r.getExpiresAt() != null && r.getExpiresAt().isAfter(Instant.now())).count();
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
        if (!currentUserId.equals(resourceOwnerId) && !SecurityUtils.hasRole("ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền thao tác trên phòng này");
        }
    }

    private boolean canManage(Room room) {
        if (SecurityUtils.hasRole("ADMIN")) return true;
        try {
            return SecurityUtils.getCurrentUserId().equals(room.getLandlordId());
        } catch (UnauthorizedException ignored) {
            return false;
        }
    }

    private boolean isPublicRoom(Room room, User landlord) {
        return room.getStatus() == RoomStatus.AVAILABLE && room.getExpiresAt() != null
                && room.getExpiresAt().isAfter(Instant.now()) && landlord != null
                && landlord.getStatus() != UserStatus.LOCKED && landlord.getStatus() != UserStatus.DELETED;
    }

    private boolean hasActiveBoost(Room room) {
        return Boolean.TRUE.equals(room.getIsBoosted()) && room.getBoostExpiresAt() != null
                && room.getBoostExpiresAt().isAfter(Instant.now());
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180))) {
            throw new BadRequestException("INVALID_COORDINATES", "Vui lòng cung cấp đầy đủ vĩ độ (-90 đến 90) và kinh độ (-180 đến 180)");
        }
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new BadRequestException("INVALID_PRICE_RANGE", "Giá phải không âm và giá tối thiểu không được lớn hơn giá tối đa");
        }
    }

    private GeocodingPort.GeoCoordinate requireCoordinates(String address) {
        GeocodingPort.GeoCoordinate coordinates;
        try {
            coordinates = geocodingPort.geocodeAddress(address).orElse(null);
        } catch (Exception e) {
            log.warn("Geocoding failed for address '{}': {}", address, e.getMessage());
            coordinates = null;
        }

        if (coordinates == null) {
            throw new BadRequestException("COORDINATES_REQUIRED", "Không thể xác định vị trí qua địa chỉ đã nhập. Vui lòng kiểm tra lại địa chỉ hoặc dịch vụ bản đồ Goong Maps.");
        }

        validateCoordinates(coordinates.latitude(), coordinates.longitude());
        return coordinates;
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
                .isBoosted(hasActiveBoost(r))
                .viewCount(r.getViewCount())
                .primaryImageUrl(primaryImage)
                .amenities(r.getAmenities() != null ? r.getAmenities() : new ArrayList<>())
                .createdAt(r.getCreatedAt())
                .expiresAt(r.getExpiresAt())
                .build();
    }

    private void validateDuplicateRoom(UUID landlordId, CreateRoomRequest request) {
        List<Room> existingRooms = roomRepository.findByLandlordIdOrderByCreatedAtDesc(landlordId);
        Instant now = Instant.now();

        // 1. Anti-spam rapid posting rate limit: at least 10s between creations
        if (!existingRooms.isEmpty()) {
            Room latest = existingRooms.get(0);
            if (latest.getCreatedAt() != null && latest.getCreatedAt().isAfter(now.minusSeconds(10))) {
                throw new BadRequestException("SPAM_RATE_LIMIT", "Bạn vừa đăng một phòng cách đây vài giây. Vui lòng đợi ít nhất 10 giây trước khi đăng tiếp.");
            }
        }

        String normalizedStreet = normalizeAddress(request.getAddressStreet());
        String reqDistrict = request.getDistrict() != null ? request.getDistrict().trim().toLowerCase() : "";
        String reqTitle = request.getTitle() != null ? request.getTitle().trim().toLowerCase() : "";

        for (Room r : existingRooms) {
            // Only compare with rooms currently active/available
            if (r.getStatus() != RoomStatus.AVAILABLE || r.getExpiresAt() == null || !r.getExpiresAt().isAfter(now)) {
                continue;
            }

            String existStreet = normalizeAddress(r.getAddressStreet());
            String existDistrict = r.getDistrict() != null ? r.getDistrict().trim().toLowerCase() : "";
            String existTitle = r.getTitle() != null ? r.getTitle().trim().toLowerCase() : "";

            boolean sameAddress = !normalizedStreet.isEmpty() && normalizedStreet.equals(existStreet)
                    && (reqDistrict.isEmpty() || existDistrict.isEmpty() || reqDistrict.equals(existDistrict));

            // Only block when it is an EXACT clone (same address + same floor + same title + same price)
            // This allows landlords to legitimately rent multiple rooms (e.g. Phòng 101, Phòng 102) in the same building!
            if (sameAddress && Objects.equals(request.getFloorNumber(), r.getFloorNumber())
                    && reqTitle.equals(existTitle)
                    && request.getPrice() != null && r.getPrice() != null
                    && request.getPrice().compareTo(r.getPrice()) == 0) {
                throw new BadRequestException("DUPLICATE_ROOM",
                        "Bạn đã có một tin đăng với tiêu đề và địa chỉ này đang hoạt động. Vui lòng cập nhật hoặc gia hạn tin cũ thay vì đăng trùng lặp.");
            }
        }
    }

    private String normalizeAddress(String raw) {
        if (raw == null) return "";
        String nfd = Normalizer.normalize(raw.toLowerCase(), Normalizer.Form.NFD);
        String noAccents = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("");
        noAccents = noAccents.replace('đ', 'd');
        return noAccents
                .replaceAll("(?i)\\b(duong|pho|hem|ngo|so|nha|ap|khu)\\b", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
