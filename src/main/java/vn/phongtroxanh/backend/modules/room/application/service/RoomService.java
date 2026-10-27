package vn.phongtroxanh.backend.modules.room.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;
import vn.phongtroxanh.backend.modules.room.domain.*;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomSwipeRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.SavedRoomRepository;
import vn.phongtroxanh.backend.modules.room.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.domain.UserProfile;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserProfileRepository;
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

    @Autowired(required = false)
    private UserProfileRepository userProfileRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Transactional
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

        // Smart Cycle Recycling: If the user has swiped through all rooms matching the current criteria,
        // automatically reset their swipe cycle (preserving saved_rooms) so finite datasets support continuous swiping.
        if (excludeUserId != null && roomPage.isEmpty()) {
            Page<Room> recycledPage = roomRepository.searchRooms(
                    district, roomType, minPrice, maxPrice, keyword, sortBy, null, pageable);
            if (!recycledPage.isEmpty()) {
                roomSwipeRepository.deleteAllByUserId(excludeUserId);
                roomPage = recycledPage;
                log.info("Auto-recycled room swipe cycle for user {}", excludeUserId);
            }
        }

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

        List<Room> fetchedRooms = roomRepository.findAllById(roomIds);
        if (fetchedRooms == null || fetchedRooms.isEmpty()) {
            List<RoomDetailResponse> fallbackDetails = roomIds.stream()
                    .map(this::getRoomDetail)
                    .toList();
            return new RoomCompareResponse(fallbackDetails);
        }

        Map<UUID, Room> roomMap = new HashMap<>();
        Set<UUID> landlordIds = new HashSet<>();
        for (Room r : fetchedRooms) {
            roomMap.put(r.getId(), r);
            if (r.getLandlordId() != null) {
                landlordIds.add(r.getLandlordId());
            }
        }

        Map<UUID, User> landlordMap = new HashMap<>();
        if (!landlordIds.isEmpty()) {
            userRepository.findAllById(landlordIds).forEach(u -> landlordMap.put(u.getId(), u));
        }

        List<RoomDetailResponse> details = new ArrayList<>();
        for (UUID id : roomIds) {
            Room room = roomMap.get(id);
            if (room == null) {
                throw new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy thông tin phòng trọ");
            }
            User landlord = landlordMap.getOrDefault(room.getLandlordId(),
                    landlordMap.isEmpty() ? userRepository.findById(room.getLandlordId()).orElse(null) : null);
            if (!isPublicRoom(room, landlord) && !canManage(room)) {
                throw new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy thông tin phòng trọ");
            }
            details.add(buildRoomDetailResponse(room, landlord));
        }

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

        // Increment view count in Redis & sync (guarded against transient Redis issues)
        try {
            if (redisTemplate != null && redisTemplate.opsForValue() != null) {
                String viewKey = "room:views:" + roomId;
                String dailyViewKey = "room:views:daily:" + roomId + ":" + LocalDate.now().toString();
                redisTemplate.opsForValue().increment(dailyViewKey);
                redisTemplate.expire(dailyViewKey, 30, TimeUnit.DAYS);

                Long currentViews = redisTemplate.opsForValue().increment(viewKey);
                if (currentViews != null && currentViews % 5 == 0) {
                    room.setViewCount(room.getViewCount() + 5);
                    roomRepository.save(room);
                }
            }
        } catch (Exception ex) {
            log.debug("Skipping view counter sync for room {}: {}", roomId, ex.getMessage());
        }

        return buildRoomDetailResponse(room, landlord);
    }

    private RoomDetailResponse buildRoomDetailResponse(Room room, User landlord) {
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
                .amenities(normalizeCanonicalAmenities(room.getAmenities()))
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

        int maxOccupants = request.getMaxOccupants() != null ? request.getMaxOccupants() : 2;
        if (maxOccupants < 1 || maxOccupants > 20) {
            throw new BadRequestException("INVALID_MAX_OCCUPANTS", "Số người ở tối đa phải từ 1 đến 20 người");
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
                .maxOccupants(maxOccupants)
                .addressStreet(request.getAddressStreet())
                .district(request.getDistrict())
                .city(request.getCity() != null ? request.getCity() : "TP.HCM")
                .latitude(lat)
                .longitude(lng)
                .status(RoomStatus.AVAILABLE)
                .isVerified(userRepository.findById(landlordId).map(u -> Boolean.TRUE.equals(u.getIsVerified())).orElse(false))
                .isBoosted(false)
                .viewCount(0L)
                .amenities(normalizeCanonicalAmenities(request.getAmenities()))
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
        notifyMatchingTenants(room, false);
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
        if (request.getMaxOccupants() != null) {
            if (request.getMaxOccupants() < 1 || request.getMaxOccupants() > 20) {
                throw new BadRequestException("INVALID_MAX_OCCUPANTS", "Số người ở tối đa phải từ 1 đến 20 người");
            }
            room.setMaxOccupants(request.getMaxOccupants());
        }
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
            room.setAmenities(normalizeCanonicalAmenities(request.getAmenities()));
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

        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new BadRequestException("ROOM_NOT_AVAILABLE", "Chỉ có thể đẩy tin phòng đang ở trạng thái Còn trống");
        }
        User landlord = userRepository.findById(landlordId).orElse(null);
        if (landlord != null && landlord.getStrikeCount() != null && landlord.getStrikeCount() >= 2) {
            throw new BadRequestException("BOOST_RESTRICTED", "Tài khoản đang ở mức cảnh báo vi phạm 2/3, tạm thời không thể sử dụng tính năng đẩy tin ưu tiên");
        }
        if (userConsumableRepository.decrementBoostAtomic(landlordId) == 0) {
            throw new BadRequestException("INSUFFICIENT_BOOSTS", "Số lượt đẩy tin của bạn đã hết");
        }

        Instant boostFrom = hasActiveBoost(room) ? room.getBoostExpiresAt() : Instant.now();
        room.setIsBoosted(true);
        room.setBoostExpiresAt(boostFrom.plus(7, ChronoUnit.DAYS));
        room.setUpdatedAt(Instant.now());
        roomRepository.save(room);
        notifyMatchingTenants(room, true);
        log.info("Landlord {} boosted room {}", landlordId, roomId);
    }

    private boolean matchesPreferredRoomType(String roomType, String preferredRoomType) {
        if (preferredRoomType == null || preferredRoomType.isBlank() || "ALL".equalsIgnoreCase(preferredRoomType.trim())) {
            return true;
        }
        if (roomType == null || roomType.isBlank()) {
            return false;
        }
        String r = roomType.trim().toUpperCase();
        String p = preferredRoomType.trim().toUpperCase();
        if (r.equals(p)) return true;

        if ((p.contains("KHÉP KÍN") || p.contains("KHEP KIN") || p.contains("PHÒNG TRỌ") || p.equals("PHONG_KHEP_KIN") || p.equals("PHONG_TRO"))
                && (r.contains("KHÉP KÍN") || r.contains("KHEP KIN") || r.contains("PHÒNG TRỌ") || r.equals("PHONG_KHEP_KIN") || r.equals("PHONG_TRO"))) {
            return true;
        }
        if ((p.contains("CĂN HỘ") || p.contains("CAN HO") || p.contains("CHUNG CƯ") || p.equals("CAN_HO_MINI") || p.equals("CHUNG_CU_MINI"))
                && (r.contains("CĂN HỘ") || r.contains("CAN HO") || r.contains("CHUNG CƯ") || r.equals("CAN_HO_MINI") || r.equals("CHUNG_CU_MINI"))) {
            return true;
        }
        if (p.contains("STUDIO") && r.contains("STUDIO")) {
            return true;
        }
        if ((p.contains("SLEEPBOX") || p.contains("Ở GHÉP") || p.contains("KÝ TÚC XÁ") || p.equals("KTX_SLEEPBOX") || p.equals("O_GHEP"))
                && (r.contains("SLEEPBOX") || r.contains("Ở GHÉP") || r.contains("KÝ TÚC XÁ") || r.equals("KTX_SLEEPBOX") || r.equals("O_GHEP"))) {
            return true;
        }
        return false;
    }

    private void notifyMatchingTenants(Room room, boolean isBoost) {
        if (userProfileRepository == null || notificationService == null || room == null || room.getId() == null) {
            return;
        }
        try {
            String roomDistrict = room.getDistrict() != null ? room.getDistrict().trim().toLowerCase() : "";
            if (roomDistrict.isEmpty()) return;

            BigDecimal roomPrice = room.getPrice();
            String roomType = room.getRoomType();
            List<UserProfile> profiles = userProfileRepository.findAll();
            for (UserProfile p : profiles) {
                if (p == null || p.getUserId() == null || p.getUserId().equals(room.getLandlordId())) {
                    continue;
                }
                List<String> prefDistricts = p.getPreferredDistricts();
                String prefRoomType = p.getPreferredRoomType();
                boolean hasDistrictPref = prefDistricts != null && !prefDistricts.isEmpty();
                boolean hasRoomTypePref = prefRoomType != null && !prefRoomType.isBlank() && !"ALL".equalsIgnoreCase(prefRoomType.trim());
                boolean hasBudgetPref = p.getBudgetMax() != null && p.getBudgetMax().compareTo(BigDecimal.ZERO) > 0;

                if (!hasDistrictPref && !hasRoomTypePref && !hasBudgetPref) {
                    continue;
                }

                if (hasDistrictPref) {
                    boolean districtMatch = prefDistricts.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(d -> d.trim().equalsIgnoreCase(roomDistrict) || roomDistrict.contains(d.trim().toLowerCase()));
                    if (!districtMatch) {
                        continue;
                    }
                }

                if (hasRoomTypePref && !matchesPreferredRoomType(roomType, prefRoomType)) {
                    continue;
                }

                if (roomPrice != null) {
                    if (p.getBudgetMax() != null && p.getBudgetMax().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal maxAllowed = p.getBudgetMax().multiply(BigDecimal.valueOf(1.15));
                        if (roomPrice.compareTo(maxAllowed) > 0) {
                            continue;
                        }
                    }
                    if (p.getBudgetMin() != null && p.getBudgetMin().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal minAllowed = p.getBudgetMin().multiply(BigDecimal.valueOf(0.85));
                        if (roomPrice.compareTo(minAllowed) < 0) {
                            continue;
                        }
                    }
                }

                String title = isBoost
                        ? "Phòng nổi bật phù hợp tại " + room.getDistrict()
                        : "Phòng phù hợp mới tại " + room.getDistrict();
                String priceStr = roomPrice != null ? String.format("%,.0fđ/tháng", roomPrice) : "Giá tốt";
                String body = room.getTitle() + " (" + priceStr + ") khớp với tiêu chí tìm phòng bạn đã đăng ký.";
                notificationService.create(
                        p.getUserId(),
                        title,
                        body,
                        NotificationType.SYSTEM,
                        Map.of("roomId", room.getId().toString(), "category", "matching_room")
                );
            }
        } catch (Exception ex) {
            log.debug("Could not dispatch matching room notifications for room {}: {}", room.getId(), ex.getMessage());
        }
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
        Set<UUID> landlordIds = new HashSet<>();
        rooms.forEach(r -> {
            map.put(r.getId(), r);
            if (r.getLandlordId() != null) {
                landlordIds.add(r.getLandlordId());
            }
        });

        Map<UUID, User> landlordMap = new HashMap<>();
        if (!landlordIds.isEmpty()) {
            userRepository.findAllById(landlordIds).forEach(u -> landlordMap.put(u.getId(), u));
        }

        return savedList.stream()
                .map(sr -> map.get(sr.getRoomId()))
                .filter(Objects::nonNull)
                .filter(r -> isPublicRoom(r, landlordMap.getOrDefault(r.getLandlordId(),
                        landlordMap.isEmpty() ? userRepository.findById(r.getLandlordId()).orElse(null) : null)))
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
            List<String> dateKeys = new ArrayList<>(7);
            List<String> allRedisKeys = new ArrayList<>(7 * rooms.size());
            for (int i = 6; i >= 0; i--) {
                String dateKey = today.minusDays(i).toString();
                dateKeys.add(dateKey);
                for (Room r : rooms) {
                    allRedisKeys.add("room:views:daily:" + r.getId() + ":" + dateKey);
                }
            }

            List<Object> values = null;
            try {
                if (redisTemplate != null && redisTemplate.opsForValue() != null) {
                    values = redisTemplate.opsForValue().multiGet(allRedisKeys);
                }
            } catch (Exception ignored) {}

            int idx = 0;
            for (String dateKey : dateKeys) {
                long dayViews = 0L;
                for (int rIdx = 0; rIdx < rooms.size(); rIdx++) {
                    Object val = (values != null && idx < values.size()) ? values.get(idx) : null;
                    idx++;
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
        return room.getStatus() == RoomStatus.AVAILABLE && landlord != null
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
            throw new BadRequestException("COORDINATES_REQUIRED", "Không thể xác định tọa độ từ địa chỉ đã nhập. Vui lòng chọn địa chỉ từ danh sách gợi ý hoặc kiểm tra lại tên đường, quận/huyện.");
        }

        validateCoordinates(coordinates.latitude(), coordinates.longitude());
        return coordinates;
    }

    private RoomSummaryResponse mapToSummaryResponse(Room r) {
        List<String> imageUrls = r.getImages() != null ? r.getImages().stream()
                .map(RoomImage::getImageUrl)
                .filter(Objects::nonNull)
                .toList() : List.of();

        String primaryImage = r.getImages() != null ? r.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(RoomImage::getImageUrl)
                .orElse(imageUrls.isEmpty() ? null : imageUrls.getFirst()) : null;

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
                .images(imageUrls)
                .amenities(normalizeCanonicalAmenities(r.getAmenities()))
                .createdAt(r.getCreatedAt())
                .expiresAt(r.getExpiresAt())
                .build();
    }

    private static final List<String> CANONICAL_AMENITIES = List.of(
            "Máy lạnh", "Wi-Fi", "WC riêng", "Giờ tự do", "Bãi xe",
            "Bếp riêng", "Ban công", "Thang máy", "Gác lửng", "An ninh 24/7"
    );

    private List<String> normalizeCanonicalAmenities(List<String> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) continue;
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            if (CANONICAL_AMENITIES.contains(trimmed)) {
                result.add(trimmed);
                continue;
            }
            String upper = trimmed.toUpperCase();
            switch (upper) {
                case "WIFI", "WI-FI" -> result.add("Wi-Fi");
                case "AIR_CONDITIONER", "MAY LANH", "MÁY LẠNH" -> result.add("Máy lạnh");
                case "PRIVATE_BATHROOM", "WC RIENG", "WC RIÊNG" -> result.add("WC riêng");
                case "FREE_HOURS", "FREE_TIME", "GIO TU DO", "GIỜ TỰ DO" -> result.add("Giờ tự do");
                case "PARKING", "BAI XE", "BÃI XE" -> result.add("Bãi xe");
                case "KITCHEN", "BEP RIENG", "BẾP RIÊNG", "SHARED_KITCHEN", "BẾP CHUNG" -> result.add("Bếp riêng");
                case "BALCONY", "BAN CONG", "BAN CÔNG" -> result.add("Ban công");
                case "ELEVATOR", "THANG MAY", "THANG MÁY" -> result.add("Thang máy");
                case "MEZZANINE", "GAC LUNG", "GÁC LỬNG" -> result.add("Gác lửng");
                case "SECURITY", "SECURITY_24_7", "AN NINH 24/7", "BẢO VỆ 24/7", "BAO VE 24/7",
                     "CAMERA", "CAMERA AN NINH", "FINGERPRINT_LOCK", "KHÓA VÂN TAY", "KHOA VAN TAY" -> result.add("An ninh 24/7");
                default -> {
                    // Ignore non-canonical / arbitrary tags to prevent filter spam
                }
            }
        }
        return new ArrayList<>(result);
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
            if (r.getStatus() != RoomStatus.AVAILABLE) {
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
