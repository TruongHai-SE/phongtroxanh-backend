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
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.rental.presentation.dto.*;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.user.domain.TrustScoreLog;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.TrustScoreLogRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalService {

    private final RentalRepository rentalRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EntityManager entityManager;
    private final NotificationService notificationService;

    @Transactional
    public RentalResponse createRental(CreateRentalRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (request.getStartDate() == null || request.getEndDate() == null
                || request.getStartDate().isBefore(LocalDate.now())
                || !request.getEndDate().isAfter(request.getStartDate())) {
            throw new BadRequestException("INVALID_RENTAL_DATES", "Ngày thuê phải từ hôm nay và ngày kết thúc phải sau ngày bắt đầu");
        }
        User tenant = requireActiveUser(currentUserId);
        if (tenant.getRole() != UserRole.TENANT && tenant.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("TENANT_REQUIRED", "Chỉ người thuê mới có thể gửi yêu cầu thuê phòng");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng trọ"));
        entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);

        if (room.getStatus() != RoomStatus.AVAILABLE || (room.getExpiresAt() != null && !room.getExpiresAt().isAfter(Instant.now()))) {
            throw new BadRequestException("ROOM_NOT_AVAILABLE", "Phòng này hiện không còn trống");
        }

        if (room.getLandlordId().equals(currentUserId)) {
            throw new BadRequestException("SELF_RENTAL_NOT_ALLOWED", "Bạn không thể thuê phòng của chính mình");
        }
        if (requireActiveUser(room.getLandlordId()).getRole() != UserRole.LANDLORD) {
            throw new BadRequestException("INVALID_LANDLORD", "Chủ phòng không có quyền cho thuê");
        }

        List<Rental> pendingRentals = rentalRepository.findByRoomIdAndStatus(room.getId(), RentalStatus.PENDING_CHECKIN);
        if (!pendingRentals.isEmpty()) {
            throw new ConflictException("ROOM_ALREADY_RESERVED", "Phòng này đã có hợp đồng đang chờ nhận phòng");
        }
        List<Rental> activeRentals = rentalRepository.findByRoomIdAndStatus(room.getId(), RentalStatus.CHECKED_IN);
        if (!activeRentals.isEmpty()) {
            throw new ConflictException("ROOM_ALREADY_RENTED", "Phòng này hiện đang có người thuê");
        }

        Rental rental = Rental.builder()
                .roomId(room.getId())
                .tenantId(currentUserId)
                .landlordId(room.getLandlordId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .monthlyRent(room.getPrice())
                .depositAmount(room.getDepositAmount())
                .status(RentalStatus.PENDING_CHECKIN)
                .isLeaseholder(true)
                .build();

        rental = rentalRepository.save(rental);
        notificationService.create(rental.getLandlordId(), "Yêu cầu thuê phòng mới", "Có người thuê gửi yêu cầu thuê phòng của bạn",
                NotificationType.SYSTEM, Map.of("rentalId", rental.getId()));
        log.info("Created rental contract {} for tenant {} and room {}", rental.getId(), currentUserId, room.getId());

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
        return rentalRepository.findByTenantIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(r -> {
                    Room room = roomRepository.findById(r.getRoomId()).orElse(null);
                    return mapToResponse(r, room);
                })
                .toList();
    }

    public List<RentalResponse> getMyLandlordRentals() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return rentalRepository.findByLandlordIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(r -> {
                    Room room = roomRepository.findById(r.getRoomId()).orElse(null);
                    return mapToResponse(r, room);
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

        // Only landlord or admin can verify check-in
        if (!rental.getLandlordId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ chủ trọ mới có quyền quét mã Check-in xác nhận nhận phòng");
        }

        String inputCode = request.getCheckInCode() != null ? request.getCheckInCode().trim().toUpperCase() : "";
        if (inputCode.isEmpty()) {
            throw new BadRequestException("INVALID_CHECKIN_CODE", "Mã Check-in không được để trống");
        }

        String redisKey = "rental:checkin:" + rentalId;
        String redisManualKey = "rental:checkin:manual:" + rentalId;

        // Support both direct 1-click confirmation ("CONFIRM", "HANDOVER") and dynamic QR code matching
        boolean isDirectHandover = inputCode.equals("CONFIRM") || inputCode.equals("HANDOVER");
        if (!isDirectHandover) {
            Object cachedCode = redisTemplate.opsForValue().get(redisKey);
            Object cachedManualCode = redisTemplate.opsForValue().get(redisManualKey);

            boolean matchesQr = cachedCode != null && cachedCode.toString().equalsIgnoreCase(inputCode);
            boolean matchesManual = cachedManualCode != null && cachedManualCode.toString().equalsIgnoreCase(inputCode);

            if (!matchesQr && !matchesManual) {
                throw new BadRequestException("INVALID_QR_CODE", "Mã Check-in không hợp lệ hoặc đã hết hạn 5 phút");
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

        // Invalidate QR code immediately on Redis and mark used in DB to prevent replay
        redisTemplate.delete(redisKey);
        redisTemplate.delete(redisManualKey);
        rental.setCheckInCode(null);
        rental.setCheckInQrToken(null);

        rental.setStatus(RentalStatus.CHECKED_IN);
        rental.setCheckedInAt(Instant.now());
        rentalRepository.save(rental);

        // Update room status to RENTED
        room.setStatus(RoomStatus.RENTED);
        roomRepository.save(room);

        // TrustScore Bonus (+10 points for check-in)
        updateTrustScoreBonus(rental.getTenantId(), 10, "Nhận phòng thành công qua mã QR Check-in");
        updateTrustScoreBonus(rental.getLandlordId(), 10, "Bàn giao phòng thành công qua mã QR Check-in");
        notificationService.create(rental.getTenantId(), "Nhận phòng thành công", "Chủ trọ đã xác nhận bàn giao phòng",
                NotificationType.SYSTEM, Map.of("rentalId", rentalId));

        log.info("Check-in verified successfully for rental {}", rentalId);
        return mapToResponse(rental, room);
    }

    @Transactional
    public RentalResponse terminateRental(UUID rentalId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng không tồn tại"));
        entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);

        if (!rental.getLandlordId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ chủ trọ hoặc Quản trị viên mới có quyền kết thúc hợp đồng");
        }

        if (rental.getStatus() == RentalStatus.TERMINATED || rental.getStatus() == RentalStatus.CANCELLED) {
            throw new BadRequestException("RENTAL_ALREADY_TERMINATED", "Hợp đồng đã kết thúc trước đó");
        }

        boolean checkedIn = rental.getStatus() == RentalStatus.CHECKED_IN;
        rental.setStatus(checkedIn ? RentalStatus.TERMINATED : RentalStatus.CANCELLED);
        rental.setCheckInCode(null);
        rental.setCheckInQrToken(null);
        rentalRepository.save(rental);
        redisTemplate.delete("rental:checkin:" + rentalId);
        redisTemplate.delete("rental:checkin:manual:" + rentalId);

        Room room = roomRepository.findById(rental.getRoomId()).orElse(null);
        if (room != null) {
            entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);
            if (checkedIn && room.getStatus() == RoomStatus.RENTED) {
                room.setStatus(RoomStatus.AVAILABLE);
                roomRepository.save(room);
            }
        }

        log.info("Terminated rental {} and released room", rentalId);
        notificationService.create(rental.getTenantId(), checkedIn ? "Hợp đồng đã kết thúc" : "Yêu cầu thuê đã hủy",
                "Chủ trọ đã cập nhật trạng thái hợp đồng thuê phòng", NotificationType.SYSTEM, Map.of("rentalId", rentalId));
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
            int oldScore = user.getTrustScore();
            int newScore = Math.min(100, oldScore + bonus);
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
        User landlord = userRepository.findById(rental.getLandlordId()).orElse(null);

        BigDecimal price = rental.getMonthlyRent() != null ? rental.getMonthlyRent()
                : (room != null ? room.getPrice() : BigDecimal.ZERO);
        BigDecimal deposit = rental.getDepositAmount() != null ? rental.getDepositAmount()
                : (room != null ? room.getDepositAmount() : BigDecimal.ZERO);

        return RentalResponse.builder()
                .id(rental.getId())
                .roomId(rental.getRoomId())
                .roomTitle(room != null ? room.getTitle() : "Phòng trọ")
                .roomAddress(room != null ? room.getAddressStreet() + ", " + room.getDistrict() : null)
                .tenantId(rental.getTenantId())
                .tenantName(tenant != null ? tenant.getFullName() : "Người thuê")
                .tenantPhone(tenant != null ? tenant.getPhoneNumber() : null)
                .tenantAvatar(tenant != null ? tenant.getAvatarUrl() : null)
                .tenantTrustScore(tenant != null && tenant.getTrustScore() != null ? tenant.getTrustScore() : 50)
                .tenantVerified(tenant != null && Boolean.TRUE.equals(tenant.getIsVerified()))
                .landlordId(rental.getLandlordId())
                .landlordName(landlord != null ? landlord.getFullName() : "Chủ trọ")
                .landlordPhone(landlord != null ? landlord.getPhoneNumber() : null)
                .startDate(rental.getStartDate())
                .endDate(rental.getEndDate())
                .monthlyRent(price)
                .depositAmount(deposit)
                .status(rental.getStatus())
                .checkInAt(rental.getCheckedInAt())
                .createdAt(rental.getCreatedAt())
                .build();
    }
}
