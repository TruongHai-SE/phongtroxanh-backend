package vn.phongtroxanh.backend.modules.rental.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ConflictException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.rental.presentation.dto.*;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.user.domain.TrustScoreLog;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.domain.UserProfile;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.TrustScoreLogRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserProfileRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalService {

    private final RentalRepository rentalRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EntityManager entityManager;
    private final NotificationService notificationService;

    @Transactional
    public RentalResponse createRental(CreateRentalRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (request.getStartDate() == null || request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("INVALID_RENTAL_DATES", "Ngày dự kiến dọn vào ở phải từ hôm nay trở đi");
        }
        LocalDate viewingDate = request.getViewingDate() != null
                ? request.getViewingDate()
                : (request.getEndDate() != null && !request.getEndDate().isAfter(request.getStartDate())
                        ? request.getEndDate()
                        : request.getStartDate());
        if (viewingDate.isBefore(LocalDate.now()) || viewingDate.isAfter(request.getStartDate())) {
            throw new BadRequestException("INVALID_VIEWING_DATE", "Ngày hẹn gặp xem phòng phải từ hôm nay trở đi và không được sau ngày dự kiến dọn vào ở");
        }
        User tenant = requireActiveUser(currentUserId);
        if (tenant.getRole() != UserRole.TENANT && tenant.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("TENANT_REQUIRED", "Chỉ tài khoản người thuê mới có thể gửi yêu cầu thuê phòng");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng trọ"));
        entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);

        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new BadRequestException("ROOM_NOT_AVAILABLE", "Phòng này hiện không còn trống");
        }

        if (room.getLandlordId().equals(currentUserId)) {
            throw new BadRequestException("SELF_RENTAL_NOT_ALLOWED", "Bạn không thể tự đăng ký thuê phòng của chính mình");
        }
        if (requireActiveUser(room.getLandlordId()).getRole() != UserRole.LANDLORD) {
            throw new BadRequestException("INVALID_LANDLORD", "Chủ phòng không có quyền cho thuê");
        }

        List<Rental> activeRentals = rentalRepository.findByRoomIdAndStatus(room.getId(), RentalStatus.CHECKED_IN);
        if (!activeRentals.isEmpty()) {
            throw new ConflictException("ROOM_ALREADY_RENTED", "Phòng này hiện đang có người thuê thực tế");
        }

        List<Rental> pendingRentals = rentalRepository.findByRoomIdAndStatus(room.getId(), RentalStatus.PENDING_CHECKIN);
        if (!pendingRentals.isEmpty()) {
            Rental existingPending = pendingRentals.get(0);
            if (existingPending.getTenantId().equals(currentUserId)) {
                throw new ConflictException("DUPLICATE_RENTAL_REQUEST", "Bạn đã gửi yêu cầu thuê cho phòng này rồi. Vui lòng kiểm tra trong mục 'Phòng của tôi'.");
            }
            throw new ConflictException("ROOM_ALREADY_RESERVED", "Phòng này đang có yêu cầu thuê chờ chủ trọ xử lý. Bạn có thể nhắn tin trực tiếp với chủ trọ.");
        }

        String cleanMessage = (request.getMessage() != null && !request.getMessage().trim().isEmpty())
                ? request.getMessage().trim()
                : null;
        if (cleanMessage != null && cleanMessage.length() > 240) {
            cleanMessage = cleanMessage.substring(0, 240);
        }

        Rental rental = Rental.builder()
                .roomId(room.getId())
                .tenantId(currentUserId)
                .landlordId(room.getLandlordId())
                .startDate(request.getStartDate())
                .endDate(viewingDate)
                .monthlyRent(room.getPrice())
                .depositAmount(room.getDepositAmount())
                .status(RentalStatus.PENDING_CHECKIN)
                .checkInCode(null)
                .checkInQrToken(cleanMessage)
                .isLeaseholder(true)
                .build();

        rental = rentalRepository.save(rental);
        notificationService.create(
                rental.getLandlordId(),
                "Yêu cầu xem & thuê phòng từ " + tenant.getFullName(),
                tenant.getFullName() + " hẹn gặp xem phòng '" + room.getTitle() + "' ngày " + viewingDate + " (Dự kiến dọn vào ở: " + request.getStartDate() + "). Hãy xem hồ sơ và duyệt yêu cầu.",
                NotificationType.SYSTEM,
                Map.of("rentalId", rental.getId(), "roomId", room.getId(), "tenantId", currentUserId)
        );
        log.info("Created rental request {} for tenant {} and room {}", rental.getId(), currentUserId, room.getId());

        return mapToResponse(rental, room);
    }

    @Transactional
    public RentalResponse approveRental(UUID rentalId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Yêu cầu không tồn tại"));
        entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);

        if (!rental.getLandlordId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ chủ trọ của phòng này mới có quyền duyệt yêu cầu");
        }

        if (rental.getStatus() != RentalStatus.PENDING_CHECKIN) {
            throw new BadRequestException("INVALID_RENTAL_STATUS", "Yêu cầu không ở trạng thái chờ duyệt");
        }

        Room room = roomRepository.findById(rental.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng trọ"));

        rental.setCheckInCode("APPROVED");
        rental = rentalRepository.save(rental);

        notificationService.create(
                rental.getTenantId(),
                "Chủ trọ đã duyệt yêu cầu xem & thuê phòng!",
                "Chủ trọ đã duyệt lịch hẹn xem & thuê phòng '" + room.getTitle() + "' (Dự kiến vào ở: " + rental.getStartDate() + "). Vui lòng vào mục 'Phòng của tôi' để xem chi tiết.",
                NotificationType.SYSTEM,
                Map.of("rentalId", rental.getId(), "roomId", room.getId())
        );
        log.info("Landlord {} approved rental request {}", currentUserId, rentalId);
        return mapToResponse(rental, room);
    }

    @Transactional
    public RentalResponse cancelRentalRequest(UUID rentalId, String reason) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Yêu cầu thuê không tồn tại"));
        entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);

        boolean isLandlord = rental.getLandlordId().equals(currentUserId);
        boolean isTenant = rental.getTenantId().equals(currentUserId);
        if (!isLandlord && !isTenant && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền hủy yêu cầu thuê này");
        }

        if (rental.getStatus() == RentalStatus.CHECKED_IN) {
            throw new BadRequestException("ALREADY_CHECKED_IN", "Hợp đồng đã nhận phòng và đang có hiệu lực, không thể hủy ngang như yêu cầu chờ duyệt. Vui lòng sử dụng chức năng Thanh lý hợp đồng.");
        }
        if (rental.getStatus() == RentalStatus.TERMINATED || rental.getStatus() == RentalStatus.CANCELLED) {
            throw new BadRequestException("ALREADY_CANCELLED", "Yêu cầu thuê này đã được đóng trước đó");
        }

        Room room = roomRepository.findById(rental.getRoomId()).orElse(null);
        String roomTitle = room != null ? room.getTitle() : "Phòng trọ";
        String cleanReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : (isLandlord ? "Chủ trọ từ chối yêu cầu thuê (ngày vào ở hoặc điều kiện chưa phù hợp)" : "Người thuê chủ động rút yêu cầu thuê phòng");
        if (cleanReason.length() > 230) {
            cleanReason = cleanReason.substring(0, 230);
        }

        rental.setStatus(RentalStatus.CANCELLED);
        rental.setCheckInCode("CANCELLED");
        rental.setCheckInQrToken("[Đã hủy/Từ chối]: " + cleanReason);
        rentalRepository.save(rental);

        redisTemplate.delete("rental:checkin:" + rentalId);
        redisTemplate.delete("rental:checkin:manual:" + rentalId);

        if (isLandlord) {
            notificationService.create(
                    rental.getTenantId(),
                    "Phản hồi yêu cầu thuê phòng '" + roomTitle + "'",
                    "Chủ trọ chưa thể duyệt yêu cầu thuê của bạn. Lý do: " + cleanReason,
                    NotificationType.SYSTEM,
                    Map.of("rentalId", rentalId)
            );
        } else {
            notificationService.create(
                    rental.getLandlordId(),
                    "Người thuê đã rút yêu cầu thuê phòng",
                    "Người thuê đã hủy yêu cầu thuê phòng '" + roomTitle + "'. Lý do: " + cleanReason,
                    NotificationType.SYSTEM,
                    Map.of("rentalId", rentalId)
            );
        }

        log.info("Cancelled pending rental request {} by user {}", rentalId, currentUserId);
        return mapToResponse(rental, room);
    }

    public RentalResponse getRentalDetail(UUID rentalId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng không tồn tại"));

        assertParticipant(rental, currentUserId);
        Room room = roomRepository.findById(rental.getRoomId()).orElse(null);
        return mapToResponse(rental, room);
    }

    public List<RentalResponse> getMyTenantRentals() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return mapRentalsBatch(rentalRepository.findByTenantIdOrderByCreatedAtDesc(currentUserId));
    }

    public List<RentalResponse> getMyLandlordRentals() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return mapRentalsBatch(rentalRepository.findByLandlordIdOrderByCreatedAtDesc(currentUserId));
    }

    private List<RentalResponse> mapRentalsBatch(List<Rental> rentals) {
        if (rentals == null || rentals.isEmpty()) {
            return List.of();
        }
        Set<UUID> roomIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> tenantIds = new HashSet<>();
        for (Rental r : rentals) {
            if (r.getRoomId() != null) roomIds.add(r.getRoomId());
            if (r.getTenantId() != null) {
                userIds.add(r.getTenantId());
                tenantIds.add(r.getTenantId());
            }
            if (r.getLandlordId() != null) userIds.add(r.getLandlordId());
        }

        Map<UUID, Room> roomMap = new HashMap<>();
        if (!roomIds.isEmpty()) {
            Iterable<Room> fetchedRooms = roomRepository.findAllById(roomIds);
            if (fetchedRooms != null) {
                fetchedRooms.forEach(rm -> {
                    if (rm != null && rm.getId() != null) roomMap.put(rm.getId(), rm);
                });
            }
        }
        boolean roomsBatchLoaded = !roomMap.isEmpty();

        Map<UUID, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            Iterable<User> fetchedUsers = userRepository.findAllById(userIds);
            if (fetchedUsers != null) {
                fetchedUsers.forEach(u -> {
                    if (u != null && u.getId() != null) userMap.put(u.getId(), u);
                });
            }
        }
        boolean usersBatchLoaded = !userMap.isEmpty();

        Map<UUID, UserProfile> profileMap = new HashMap<>();
        if (userProfileRepository != null && !tenantIds.isEmpty()) {
            Iterable<UserProfile> fetchedProfiles = userProfileRepository.findAllById(tenantIds);
            if (fetchedProfiles != null) {
                fetchedProfiles.forEach(p -> {
                    if (p != null && p.getUserId() != null) profileMap.put(p.getUserId(), p);
                });
            }
        }
        boolean profilesBatchLoaded = !profileMap.isEmpty();

        return rentals.stream()
                .map(r -> {
                    Room room = roomsBatchLoaded
                            ? roomMap.get(r.getRoomId())
                            : roomRepository.findById(r.getRoomId()).orElse(null);
                    User tenant = usersBatchLoaded
                            ? userMap.get(r.getTenantId())
                            : userRepository.findById(r.getTenantId()).orElse(null);
                    UserProfile tenantProfile = profilesBatchLoaded
                            ? profileMap.get(r.getTenantId())
                            : (userProfileRepository != null ? userProfileRepository.findById(r.getTenantId()).orElse(null) : null);
                    User landlord = usersBatchLoaded
                            ? userMap.get(r.getLandlordId())
                            : userRepository.findById(r.getLandlordId()).orElse(null);
                    return buildRentalResponse(r, room, tenant, tenantProfile, landlord);
                })
                .toList();
    }

    @Transactional
    public CheckInQrResponse generateCheckInQr(UUID rentalId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng không tồn tại"));
        entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);

        if (!rental.getTenantId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ người thuê phòng mới có quyền tạo mã Check-in");
        }

        if (rental.getStatus() != RentalStatus.PENDING_CHECKIN) {
            throw new BadRequestException("INVALID_RENTAL_STATUS", "Chỉ có thể tạo mã Check-in cho hợp đồng đang chờ nhận phòng");
        }

        String dynamicCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String manualCode = dynamicCode.substring(0, 8);
        String redisKey = "rental:checkin:" + rentalId;
        String redisManualKey = "rental:checkin:manual:" + rentalId;
        redisTemplate.opsForValue().set(redisKey, dynamicCode, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(redisManualKey, manualCode, 5, TimeUnit.MINUTES);

        Instant expiresAt = Instant.now().plusSeconds(300);

        return CheckInQrResponse.builder()
                .rentalId(rentalId)
                .qrCodePayload(dynamicCode)
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public RentalResponse verifyCheckIn(UUID rentalId, VerifyCheckInRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng không tồn tại"));
        entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);

        if (rental.getStatus() == RentalStatus.CHECKED_IN) {
            throw new ConflictException("RENTAL_ALREADY_CHECKED_IN", "Hợp đồng này đã được xác nhận Check-in trước đó");
        }

        if (rental.getStatus() != RentalStatus.PENDING_CHECKIN) {
            throw new BadRequestException("INVALID_RENTAL_STATUS", "Hợp đồng không ở trạng thái chờ nhận phòng");
        }

        boolean isLandlord = rental.getLandlordId().equals(currentUserId);
        boolean isTenant = rental.getTenantId().equals(currentUserId);
        boolean isAdmin = SecurityUtils.hasRole("ROLE_ADMIN");

        if (!isLandlord && !isTenant && !isAdmin) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ chủ trọ hoặc người thuê của hợp đồng này mới có quyền xác nhận bàn giao phòng");
        }

        // Nếu người thuê bấm xác nhận nhận phòng, bắt buộc Chủ trọ phải đã Duyệt yêu cầu ("APPROVED") trước đó
        if (isTenant && !isLandlord && !isAdmin && !"APPROVED".equalsIgnoreCase(rental.getCheckInCode())) {
            throw new BadRequestException("LANDLORD_APPROVAL_REQUIRED", "Chủ trọ chưa duyệt yêu cầu thuê phòng này. Vui lòng chờ chủ trọ duyệt hồ sơ và ngày vào ở trước khi xác nhận nhận phòng.");
        }

        String inputCode = request.getCheckInCode() != null ? request.getCheckInCode().trim().toUpperCase() : "";
        if (inputCode.isEmpty()) {
            throw new BadRequestException("INVALID_CHECKIN_CODE", "Mã xác nhận Check-in không được để trống");
        }

        String redisKey = "rental:checkin:" + rentalId;
        String redisManualKey = "rental:checkin:manual:" + rentalId;

        boolean isDirectHandover = inputCode.equals("CONFIRM") || inputCode.equals("HANDOVER");
        if (!isDirectHandover) {
            Object cachedCode = redisTemplate.opsForValue().get(redisKey);
            Object cachedManualCode = redisTemplate.opsForValue().get(redisManualKey);

            boolean matchesQr = cachedCode != null && cachedCode.toString().equalsIgnoreCase(inputCode);
            boolean matchesManual = cachedManualCode != null && cachedManualCode.toString().equalsIgnoreCase(inputCode);

            if (!matchesQr && !matchesManual) {
                throw new BadRequestException("INVALID_QR_CODE", "Mã xác nhận Check-in không hợp lệ hoặc đã hết hạn");
            }
        }

        Room room = roomRepository.findById(rental.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng trọ"));
        entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);
        if (!rental.getLandlordId().equals(room.getLandlordId())
                || (room.getStatus() != RoomStatus.AVAILABLE && room.getStatus() != RoomStatus.RENTED)) {
            throw new BadRequestException("ROOM_NOT_AVAILABLE", "Phòng không thể bàn giao theo hợp đồng này");
        }
        requireActiveUser(rental.getTenantId());
        requireActiveUser(rental.getLandlordId());

        redisTemplate.delete(redisKey);
        redisTemplate.delete(redisManualKey);
        rental.setCheckInCode("CHECKED_IN");

        rental.setStatus(RentalStatus.CHECKED_IN);
        rental.setCheckedInAt(Instant.now());
        rentalRepository.save(rental);

        room.setStatus(RoomStatus.RENTED);
        roomRepository.save(room);

        updateTrustScoreBonus(rental.getTenantId(), 10, "Nhận phòng thành công");
        updateTrustScoreBonus(rental.getLandlordId(), 10, "Bàn giao phòng thành công");

        if (isLandlord) {
            notificationService.create(
                    rental.getTenantId(),
                    "Bàn giao & Nhận phòng thành công (+10 TrustScore)",
                    "Chủ trọ đã xác nhận bàn giao phòng '" + room.getTitle() + "'. Hợp đồng thuê chính thức có hiệu lực!",
                    NotificationType.SYSTEM,
                    Map.of("rentalId", rentalId, "roomId", room.getId())
            );
        } else {
            notificationService.create(
                    rental.getLandlordId(),
                    "Người thuê đã xác nhận nhận phòng (+10 TrustScore)",
                    "Người thuê đã xác nhận nhận bàn giao phòng '" + room.getTitle() + "'. Hợp đồng thuê chính thức có hiệu lực!",
                    NotificationType.SYSTEM,
                    Map.of("rentalId", rentalId, "roomId", room.getId())
            );
        }

        log.info("Check-in verified successfully for rental {}", rentalId);
        return mapToResponse(rental, room);
    }

    @Transactional
    public RentalResponse terminateRental(UUID rentalId) {
        return terminateRental(rentalId, null, null);
    }

    @Transactional
    public RentalResponse terminateRental(UUID rentalId, String reason, String terminationType) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng không tồn tại"));
        entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);

        boolean isLandlord = rental.getLandlordId().equals(currentUserId);
        boolean isTenant = rental.getTenantId().equals(currentUserId);
        boolean isAdmin = SecurityUtils.hasRole("ROLE_ADMIN");

        if (!isLandlord && !isTenant && !isAdmin) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ các bên trong hợp đồng hoặc Quản trị viên mới có quyền thanh lý hợp đồng");
        }

        if (rental.getStatus() == RentalStatus.TERMINATED || rental.getStatus() == RentalStatus.CANCELLED) {
            throw new BadRequestException("RENTAL_ALREADY_TERMINATED", "Hợp đồng đã kết thúc trước đó");
        }

        boolean checkedIn = rental.getStatus() == RentalStatus.CHECKED_IN;
        if (!checkedIn) {
            return cancelRentalRequest(rentalId, reason);
        }

        String safeReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : "Hai bên thỏa thuận thanh lý hợp đồng và hoàn trả cọc đầy đủ";
        if (safeReason.length() > 210) {
            safeReason = safeReason.substring(0, 210);
        }

        boolean isEarlyUnilateralByLandlord = isLandlord
                && "EARLY_LANDLORD".equalsIgnoreCase(terminationType);

        rental.setStatus(RentalStatus.TERMINATED);
        rental.setCheckInCode("TERMINATED");
        rental.setCheckInQrToken("[Biên bản thanh lý]: " + safeReason);
        rentalRepository.save(rental);

        redisTemplate.delete("rental:checkin:" + rentalId);
        redisTemplate.delete("rental:checkin:manual:" + rentalId);

        Room room = roomRepository.findById(rental.getRoomId()).orElse(null);
        if (room != null) {
            entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);
            if (room.getStatus() == RoomStatus.RENTED) {
                room.setStatus(RoomStatus.AVAILABLE);
                roomRepository.save(room);
            }
        }

        if (isEarlyUnilateralByLandlord) {
            updateTrustScoreBonus(rental.getLandlordId(), -15, "Chủ trọ đơn phương chấm dứt hợp đồng trước hạn");
        }

        String roomTitle = room != null ? room.getTitle() : "Phòng trọ";
        if (isLandlord) {
            notificationService.create(
                    rental.getTenantId(),
                    "Biên bản thanh lý hợp đồng phòng '" + roomTitle + "'",
                    "Chủ trọ đã ghi nhận thanh lý hợp đồng (" + safeReason + "). Toàn bộ thông tin chủ trọ và biên bản đặt cọc vẫn được lưu trữ bảo vệ trong mục 'Phòng của tôi'.",
                    NotificationType.SYSTEM,
                    Map.of("rentalId", rentalId)
            );
        } else {
            notificationService.create(
                    rental.getLandlordId(),
                    "Người thuê đã xác nhận trả phòng '" + roomTitle + "'",
                    "Người thuê đã gửi biên bản trả phòng & thanh lý hợp đồng (" + safeReason + "). Phòng đã được mở lại trạng thái Còn trống.",
                    NotificationType.SYSTEM,
                    Map.of("rentalId", rentalId)
            );
        }

        log.info("Terminated rental {} and released room (reason={})", rentalId, safeReason);
        return mapToResponse(rental, room);
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DELETED) {
            throw new ForbiddenException("ACCOUNT_INACTIVE", "Tài khoản không thể thực hiện giao dịch thuê phòng");
        }
        return user;
    }

    private void updateTrustScoreBonus(UUID userId, int bonus, String reason) {
        userRepository.findById(userId).ifPresent(user -> {
            int oldScore = user.getTrustScore() != null ? user.getTrustScore() : 50;
            int newScore = Math.max(0, Math.min(100, oldScore + bonus));
            user.setTrustScore(newScore);
            userRepository.save(user);

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(userId)
                    .delta(bonus)
                    .finalScore(newScore)
                    .reason(reason)
                    .build());
        });
    }

    private void assertParticipant(Rental rental, UUID userId) {
        if (!rental.getTenantId().equals(userId) && !rental.getLandlordId().equals(userId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền truy cập hợp đồng này");
        }
    }

    private RentalResponse mapToResponse(Rental rental, Room room) {
        User tenant = userRepository.findById(rental.getTenantId()).orElse(null);
        UserProfile tenantProfile = userProfileRepository != null
                ? userProfileRepository.findById(rental.getTenantId()).orElse(null)
                : null;
        User landlord = userRepository.findById(rental.getLandlordId()).orElse(null);
        return buildRentalResponse(rental, room, tenant, tenantProfile, landlord);
    }

    private RentalResponse buildRentalResponse(Rental rental, Room room, User tenant, UserProfile tenantProfile, User landlord) {

        BigDecimal price = rental.getMonthlyRent() != null ? rental.getMonthlyRent()
                : (room != null ? room.getPrice() : BigDecimal.ZERO);
        BigDecimal deposit = rental.getDepositAmount() != null ? rental.getDepositAmount()
                : (room != null ? room.getDepositAmount() : BigDecimal.ZERO);

        String approvalStatus;
        if (rental.getStatus() == RentalStatus.CHECKED_IN) {
            approvalStatus = "CHECKED_IN";
        } else if (rental.getStatus() == RentalStatus.TERMINATED) {
            approvalStatus = "TERMINATED";
        } else if (rental.getStatus() == RentalStatus.CANCELLED) {
            approvalStatus = "CANCELLED";
        } else if ("APPROVED".equalsIgnoreCase(rental.getCheckInCode())) {
            approvalStatus = "APPROVED";
        } else {
            approvalStatus = "PENDING_APPROVAL";
        }

        Integer birthYear = (tenantProfile != null && tenantProfile.getBirthDate() != null)
                ? tenantProfile.getBirthDate().getYear()
                : null;
        String gender = (tenantProfile != null && tenantProfile.getGender() != null)
                ? tenantProfile.getGender().name()
                : null;

        LocalDate viewingDate = (rental.getEndDate() != null
                && rental.getStartDate() != null
                && !rental.getEndDate().isAfter(rental.getStartDate()))
                ? rental.getEndDate()
                : rental.getStartDate();

        String tenantMessage = (rental.getCheckInQrToken() != null && !rental.getCheckInQrToken().isBlank())
                ? rental.getCheckInQrToken()
                : "Em muốn đăng ký thuê phòng này, mong anh/chị xem hồ sơ và duyệt giúp em ạ.";

        return RentalResponse.builder()
                .id(rental.getId())
                .roomId(rental.getRoomId())
                .roomTitle(room != null ? room.getTitle() : "Phòng trọ")
                .roomAddress(room != null ? room.getAddressStreet() + ", " + room.getDistrict() : null)
                .roomDistrict(room != null ? room.getDistrict() : null)
                .tenantId(rental.getTenantId())
                .tenantName(tenant != null ? tenant.getFullName() : "Người thuê")
                .tenantPhone(tenant != null ? tenant.getPhoneNumber() : null)
                .tenantEmail(tenant != null ? tenant.getEmail() : null)
                .tenantAvatar(tenant != null ? tenant.getAvatarUrl() : null)
                .tenantTrustScore(tenant != null && tenant.getTrustScore() != null ? tenant.getTrustScore() : 50)
                .tenantVerified(tenant != null && Boolean.TRUE.equals(tenant.getIsVerified()))
                .tenantSchool(tenantProfile != null ? tenantProfile.getSchoolOrCompany() : null)
                .tenantGender(gender)
                .tenantBirthYear(birthYear)
                .tenantBio(tenantProfile != null ? tenantProfile.getBio() : null)
                .tenantMessage(tenantMessage)
                .landlordId(rental.getLandlordId())
                .landlordName(landlord != null ? landlord.getFullName() : "Chủ trọ")
                .landlordPhone(landlord != null ? landlord.getPhoneNumber() : null)
                .landlordEmail(landlord != null ? landlord.getEmail() : null)
                .landlordAvatar(landlord != null ? landlord.getAvatarUrl() : null)
                .landlordTrustScore(landlord != null && landlord.getTrustScore() != null ? landlord.getTrustScore() : 50)
                .landlordVerified(landlord != null && Boolean.TRUE.equals(landlord.getIsVerified()))
                .viewingDate(viewingDate)
                .startDate(rental.getStartDate())
                .endDate(rental.getEndDate())
                .monthlyRent(price)
                .depositAmount(deposit)
                .status(rental.getStatus())
                .approvalStatus(approvalStatus)
                .checkInAt(rental.getCheckedInAt())
                .createdAt(rental.getCreatedAt())
                .build();
    }
}
