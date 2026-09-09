package vn.phongtroxanh.backend.modules.rental.application.service;

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
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.TrustScoreLogRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    @Transactional
    public RentalResponse createRental(CreateRentalRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng trọ"));

        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new BadRequestException("ROOM_NOT_AVAILABLE", "Phòng này hiện không còn trống");
        }

        if (room.getLandlordId().equals(currentUserId)) {
            throw new BadRequestException("SELF_RENTAL_NOT_ALLOWED", "Bạn không thể thuê phòng của chính mình");
        }

        List<Rental> pendingRentals = rentalRepository.findByRoomIdAndStatus(room.getId(), RentalStatus.PENDING_CHECKIN);
        if (!pendingRentals.isEmpty()) {
            throw new ConflictException("ROOM_ALREADY_RESERVED", "Phòng này đã có hợp đồng đang chờ nhận phòng");
        }
        List<Rental> activeRentals = rentalRepository.findByRoomIdAndStatus(room.getId(), RentalStatus.CHECKED_IN);
        if (!activeRentals.isEmpty()) {
            throw new ConflictException("ROOM_ALREADY_RENTED", "Phòng này hiện đang có người thuê");
        }

        String initialCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String initialToken = UUID.randomUUID().toString();

        Rental rental = Rental.builder()
                .roomId(room.getId())
                .tenantId(currentUserId)
                .landlordId(room.getLandlordId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(RentalStatus.PENDING_CHECKIN)
                .checkInCode(initialCode)
                .checkInQrToken(initialToken)
                .isLeaseholder(true)
                .build();

        rental = rentalRepository.save(rental);
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

        if (!rental.getTenantId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ người thuê phòng mới có quyền tạo mã Check-in");
        }

        if (rental.getStatus() != RentalStatus.PENDING_CHECKIN) {
            throw new BadRequestException("INVALID_RENTAL_STATUS", "Chỉ có thể tạo mã Check-in cho hợp đồng đang chờ nhận phòng");
        }

        String dynamicCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String manualCode = dynamicCode.substring(0, 8);
        rental.setCheckInCode(manualCode);
        rental.setCheckInQrToken(dynamicCode);
        rentalRepository.save(rental);

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
        Object cachedCode = redisTemplate.opsForValue().get(redisKey);
        Object cachedManualCode = redisTemplate.opsForValue().get(redisManualKey);

        boolean matchesQr = (cachedCode != null && cachedCode.toString().equalsIgnoreCase(inputCode))
                || (rental.getCheckInQrToken() != null && rental.getCheckInQrToken().equalsIgnoreCase(inputCode));
        boolean matchesManual = (cachedManualCode != null && cachedManualCode.toString().equalsIgnoreCase(inputCode))
                || (rental.getCheckInCode() != null && rental.getCheckInCode().equalsIgnoreCase(inputCode));

        if (!matchesQr && !matchesManual) {
            throw new BadRequestException("INVALID_QR_CODE", "Mã Check-in không hợp lệ hoặc đã hết hạn 5 phút");
        }

        // Invalidate QR code immediately on Redis and mark used in DB to prevent replay
        redisTemplate.delete(redisKey);
        redisTemplate.delete(redisManualKey);
        String safeUsedManual = "USED_" + (inputCode.length() > 8 ? inputCode.substring(0, 8) : inputCode);
        rental.setCheckInCode(safeUsedManual.length() > 16 ? safeUsedManual.substring(0, 16) : safeUsedManual);
        rental.setCheckInQrToken("USED_" + UUID.randomUUID());

        rental.setStatus(RentalStatus.CHECKED_IN);
        rental.setCheckedInAt(Instant.now());
        rentalRepository.save(rental);

        // Update room status to RENTED
        Room room = roomRepository.findById(rental.getRoomId()).orElse(null);
        if (room != null) {
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
        }

        // TrustScore Bonus (+10 points for check-in)
        updateTrustScoreBonus(rental.getTenantId(), 10, "Nhận phòng thành công qua mã QR Check-in");
        updateTrustScoreBonus(rental.getLandlordId(), 10, "Bàn giao phòng thành công qua mã QR Check-in");

        log.info("Check-in verified successfully for rental {}", rentalId);
        return mapToResponse(rental, room);
    }

    @Transactional
    public RentalResponse terminateRental(UUID rentalId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_NOT_FOUND", "Hợp đồng không tồn tại"));

        if (!rental.getLandlordId().equals(currentUserId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Chỉ chủ trọ hoặc Quản trị viên mới có quyền kết thúc hợp đồng");
        }

        if (rental.getStatus() == RentalStatus.TERMINATED) {
            throw new BadRequestException("RENTAL_ALREADY_TERMINATED", "Hợp đồng đã kết thúc trước đó");
        }

        rental.setStatus(RentalStatus.TERMINATED);
        rentalRepository.save(rental);

        Room room = roomRepository.findById(rental.getRoomId()).orElse(null);
        if (room != null) {
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
        }

        log.info("Terminated rental {} and released room", rentalId);
        return mapToResponse(rental, room);
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

        BigDecimal price = room != null ? room.getPrice() : BigDecimal.ZERO;
        BigDecimal deposit = room != null ? room.getDepositAmount() : BigDecimal.ZERO;

        return RentalResponse.builder()
                .id(rental.getId())
                .roomId(rental.getRoomId())
                .roomTitle(room != null ? room.getTitle() : "Phòng trọ")
                .roomAddress(room != null ? room.getAddressStreet() + ", " + room.getDistrict() : null)
                .tenantId(rental.getTenantId())
                .tenantName(tenant != null ? tenant.getFullName() : "Người thuê")
                .tenantPhone(tenant != null ? tenant.getPhoneNumber() : null)
                .tenantAvatar(tenant != null ? tenant.getAvatarUrl() : null)
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
