package vn.phongtroxanh.backend.modules.swap.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.rental.domain.Rental;
import vn.phongtroxanh.backend.modules.rental.domain.RentalStatus;
import vn.phongtroxanh.backend.modules.rental.infrastructure.repository.RentalRepository;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.swap.domain.SwapRequest;
import vn.phongtroxanh.backend.modules.swap.domain.SwapStatus;
import vn.phongtroxanh.backend.modules.swap.infrastructure.repository.SwapRequestRepository;
import vn.phongtroxanh.backend.modules.swap.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;
import vn.phongtroxanh.backend.modules.user.domain.UserProfile;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserProfileRepository;
import vn.phongtroxanh.backend.modules.room.domain.RoomImage;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSwapService {

    private final SwapRequestRepository swapRequestRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RentalRepository rentalRepository;
    private final EntityManager entityManager;
    private final NotificationService notificationService;

    @Transactional
    public SwapPostResponse createSwapPost(CreateSwapRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Room currentRoom = roomRepository.findById(request.getCurrentRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng hiện tại"));
        Rental lease = requireLeaseholder(currentRoom.getId(), currentUserId);
        requireActiveTenant(currentUserId);
        if (!currentRoom.getLandlordId().equals(lease.getLandlordId()) || currentRoom.getStatus() != RoomStatus.RENTED) {
            throw new BadRequestException("INVALID_LANDLORD", "Chủ trọ không khớp hợp đồng hiện tại");
        }

        String reason = request.getReason() != null ? request.getReason()
                : (request.getDescription() != null ? request.getDescription() : "Cần hoán đổi phòng");

        List<String> targetDistricts = request.getTargetDistricts() != null
                ? request.getTargetDistricts()
                : (request.getDesiredDistricts() != null ? request.getDesiredDistricts() : List.of());

        BigDecimal maxBudget = request.getTargetBudgetMax() != null
                ? request.getTargetBudgetMax()
                : request.getDesiredPriceMax();

        SwapRequest swap = SwapRequest.builder()
                .requesterId(currentUserId)
                .currentRoomId(request.getCurrentRoomId())
                .isLeaseholder(lease.getIsLeaseholder())
                .targetDistricts(targetDistricts)
                .targetRoomType(request.getTargetRoomType())
                .targetBudgetMax(maxBudget)
                .habits(request.getHabits())
                .reason(reason)
                .targetMoveInDate(request.getTargetMoveInDate() != null ? request.getTargetMoveInDate() : request.getMoveInDate())
                .landlordId(currentRoom.getLandlordId())
                .landlordDecision(SwapStatus.PENDING_LANDLORD)
                .status(SwapStatus.OPEN)
                .build();

        swap = swapRequestRepository.save(swap);
        return mapToResponse(swap);
    }

    public Page<SwapPostResponse> searchSwapPosts(BigDecimal maxPrice, String keyword, int page, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeLimit);

        Page<SwapRequest> swapPage;
        if (maxPrice != null) {
            swapPage = swapRequestRepository.findByStatusAndTargetBudgetMaxLessThanEqualOrderByCreatedAtDesc(SwapStatus.OPEN, maxPrice, pageable);
        } else {
            swapPage = swapRequestRepository.findByStatusOrderByCreatedAtDesc(SwapStatus.OPEN, pageable);
        }
        return swapPage.map(this::mapToResponse);
    }

    public SwapPostResponse getSwapPostDetail(UUID id) {
        SwapRequest swap = swapRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SWAP_NOT_FOUND", "Bài đăng pass phòng không tồn tại"));
        return mapToResponse(swap);
    }

    public List<SwapPostResponse> getMySwaps() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return swapRequestRepository.findByRequesterIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SwapProposalResponse sendSwapProposal(UUID swapPostId, SendSwapProposalRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        SwapRequest post = swapRequestRepository.findById(swapPostId)
                .orElseThrow(() -> new ResourceNotFoundException("SWAP_NOT_FOUND", "Bài đăng pass phòng không tồn tại"));
        entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);

        if (post.getRequesterId().equals(currentUserId)) {
            throw new BadRequestException("SELF_PROPOSAL_NOT_ALLOWED", "Bạn không thể gửi đề xuất hoán đổi cho chính bài đăng của mình");
        }

        if (post.getStatus() != SwapStatus.OPEN) {
            throw new BadRequestException("SWAP_POST_CLOSED", "Bài đăng hoán đổi phòng này đã đóng, không thể gửi đề xuất");
        }

        if (post.getMatchedTenantId() != null) {
            throw new BadRequestException("SWAP_ALREADY_MATCHED", "Bài đăng này đã có người gửi đề xuất đang xử lý");
        }

        requireActiveTenant(currentUserId);
        requireLeaseholder(post.getCurrentRoomId(), post.getRequesterId());
        if (request.getOfferedRoomId() != null) {
            if (post.getCurrentRoomId().equals(request.getOfferedRoomId())) {
                throw new BadRequestException("INVALID_OFFERED_ROOM", "Phòng đề xuất phải khác phòng đang đăng");
            }
            requireLeaseholder(request.getOfferedRoomId(), currentUserId);
        }

        post.setMatchedTenantId(currentUserId);
        post.setOfferedRoomId(request.getOfferedRoomId());
        post.setProposalMessage(request.getMessage());
        post.setProposalCreatedAt(Instant.now());
        post.setStatus(SwapStatus.MATCHING);
        post = swapRequestRepository.save(post);
        notificationService.create(post.getRequesterId(), "Đề xuất nhận phòng mới", "Có người gửi đề xuất cho bài đăng của bạn",
                NotificationType.SYSTEM, Map.of("swapId", post.getId()));

        return mapProposal(post);
    }

    @Transactional
    public SwapProposalResponse updateSwapProposalStatus(UUID requestId, UpdateSwapRequestStatusRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        SwapRequest swap = swapRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu đề xuất"));
        entityManager.refresh(swap, LockModeType.PESSIMISTIC_WRITE);

        boolean isRequester = swap.getRequesterId().equals(currentUserId);
        boolean isLandlord = swap.getLandlordId().equals(currentUserId);
        boolean isMatchedTenant = swap.getMatchedTenantId() != null && swap.getMatchedTenantId().equals(currentUserId);

        if (!isRequester && !isLandlord && !isMatchedTenant && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền cập nhật trạng thái yêu cầu hoán đổi này");
        }

        boolean admin = SecurityUtils.hasRole("ROLE_ADMIN");
        if (swap.getStatus() == SwapStatus.CANCELLED || swap.getStatus() == SwapStatus.COMPLETED || swap.getStatus() == SwapStatus.DECLINED) {
            throw new BadRequestException("SWAP_IMMUTABLE", "Yêu cầu hoán đổi đã kết thúc hoặc bị hủy, không thể thay đổi trạng thái");
        }

        SwapStatus target = request.getStatus();
        if (target == null) {
            throw new BadRequestException("INVALID_SWAP_STATUS", "Trạng thái không được để trống");
        }
        if (target == SwapStatus.APPROVED || (target == SwapStatus.DECLINED && swap.getStatus() == SwapStatus.PENDING_LANDLORD)) {
            if (!isLandlord && !admin) {
                throw new ForbiddenException("LANDLORD_REQUIRED", "Chỉ chủ trọ mới có quyền quyết định chuyển nhượng");
            }
            decide(swap, target == SwapStatus.APPROVED);
        } else if (target == SwapStatus.PENDING_LANDLORD && swap.getStatus() == SwapStatus.MATCHING && swap.getMatchedTenantId() != null) {
            if (!isRequester && !admin) {
                throw new ForbiddenException("OWNER_REQUIRED", "Chỉ chủ bài đăng mới có quyền chấp nhận đề xuất");
            }
            swap.setStatus(SwapStatus.PENDING_LANDLORD);
            notificationService.create(swap.getLandlordId(), "Yêu cầu chuyển nhượng phòng", "Người thuê đã chấp nhận đề xuất và đang chờ bạn quyết định",
                    NotificationType.SYSTEM, Map.of("swapId", swap.getId()));
        } else if (target == SwapStatus.DECLINED && swap.getStatus() == SwapStatus.MATCHING) {
            if (!isRequester && !admin) {
                throw new ForbiddenException("OWNER_REQUIRED", "Chỉ chủ bài đăng mới có quyền từ chối đề xuất");
            }
            reopen(swap);
        } else if (target == SwapStatus.CANCELLED) {
            if (isMatchedTenant && !isRequester && !isLandlord && !admin) {
                if (swap.getStatus() != SwapStatus.MATCHING && swap.getStatus() != SwapStatus.PENDING_LANDLORD) {
                    throw new BadRequestException("INVALID_SWAP_TRANSITION", "Không thể rút đề xuất đã được duyệt");
                }
                reopen(swap);
            } else {
                swap.setStatus(SwapStatus.CANCELLED);
            }
        } else if (target == SwapStatus.COMPLETED && swap.getStatus() == SwapStatus.APPROVED && swap.getLandlordDecision() == SwapStatus.APPROVED) {
            if (!isRequester && !isLandlord && !admin) {
                throw new ForbiddenException("OWNER_REQUIRED", "Chỉ chủ bài đăng hoặc chủ trọ mới có quyền bàn giao hợp đồng");
            }
            transferLease(swap);
            swap.setStatus(SwapStatus.COMPLETED);
        } else {
            throw new BadRequestException("INVALID_SWAP_TRANSITION", "Chuyển trạng thái hoán đổi không hợp lệ");
        }
        swap = swapRequestRepository.save(swap);
        return mapProposal(swap);
    }

    public List<SwapPostResponse> getLandlordRequests() {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        return swapRequestRepository.findByLandlordIdOrderByCreatedAtDesc(landlordId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SwapPostResponse landlordDecision(UUID id, boolean approve) {
        UUID landlordId = SecurityUtils.getCurrentUserId();
        SwapRequest swap = swapRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SWAP_NOT_FOUND", "Không tìm thấy yêu cầu hoán đổi"));
        entityManager.refresh(swap, LockModeType.PESSIMISTIC_WRITE);

        if (!swap.getLandlordId().equals(landlordId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không phải chủ trọ của phòng này");
        }

        decide(swap, approve);
        swap = swapRequestRepository.save(swap);
        return mapToResponse(swap);
    }

    private void decide(SwapRequest swap, boolean approve) {
        if (swap.getStatus() != SwapStatus.PENDING_LANDLORD || swap.getMatchedTenantId() == null
                || swap.getLandlordDecision() != SwapStatus.PENDING_LANDLORD) {
            throw new BadRequestException("INVALID_SWAP_TRANSITION", "Chủ bài đăng phải chấp nhận một đề xuất trước khi chủ trọ quyết định");
        }
        Rental outgoing = requireLeaseholder(swap.getCurrentRoomId(), swap.getRequesterId());
        if (!swap.getLandlordId().equals(outgoing.getLandlordId())) {
            throw new BadRequestException("INVALID_LANDLORD", "Chủ trọ không khớp hợp đồng hiện tại");
        }
        requireActiveTenant(swap.getMatchedTenantId());
        swap.setLandlordDecision(approve ? SwapStatus.APPROVED : SwapStatus.DECLINED);
        swap.setStatus(swap.getLandlordDecision());
        for (UUID recipient : List.of(swap.getRequesterId(), swap.getMatchedTenantId())) {
            notificationService.create(recipient, approve ? "Chuyển nhượng được duyệt" : "Chuyển nhượng bị từ chối",
                    "Chủ trọ đã quyết định yêu cầu chuyển nhượng phòng", NotificationType.SYSTEM, Map.of("swapId", swap.getId()));
        }
    }

    private Rental requireLeaseholder(UUID roomId, UUID tenantId) {
        return rentalRepository.findByRoomIdAndStatus(roomId, RentalStatus.CHECKED_IN).stream()
                .filter(r -> tenantId.equals(r.getTenantId()) && Boolean.TRUE.equals(r.getIsLeaseholder()))
                .findFirst().orElseThrow(() -> new ForbiddenException("LEASEHOLDER_REQUIRED", "Bạn phải là người đứng tên hợp đồng đang thuê phòng này"));
    }

    private void requireActiveTenant(UUID tenantId) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "Không tìm thấy người thuê"));
        if (tenant.getRole() != UserRole.TENANT || tenant.getStatus() == UserStatus.LOCKED || tenant.getStatus() == UserStatus.DELETED) {
            throw new ForbiddenException("TENANT_INACTIVE", "Người thuê không đủ điều kiện nhận chuyển nhượng");
        }
    }

    private void reopen(SwapRequest swap) {
        swap.setStatus(SwapStatus.OPEN);
        swap.setMatchedTenantId(null);
        swap.setOfferedRoomId(null);
        swap.setProposalMessage(null);
        swap.setProposalCreatedAt(null);
        swap.setLandlordDecision(SwapStatus.PENDING_LANDLORD);
    }

    private void transferLease(SwapRequest swap) {
        if (swap.getMatchedTenantId() == null) {
            throw new BadRequestException("SWAP_NOT_MATCHED", "Yêu cầu chưa có người nhận phòng");
        }
        requireActiveTenant(swap.getMatchedTenantId());
        Rental outgoing = requireLeaseholder(swap.getCurrentRoomId(), swap.getRequesterId());
        entityManager.refresh(outgoing, LockModeType.PESSIMISTIC_WRITE);
        if (outgoing.getStatus() != RentalStatus.CHECKED_IN || !swap.getRequesterId().equals(outgoing.getTenantId())
                || !swap.getLandlordId().equals(outgoing.getLandlordId()) || !Boolean.TRUE.equals(outgoing.getIsLeaseholder())) {
            throw new BadRequestException("LEASE_CHANGED", "Hợp đồng hiện tại đã thay đổi");
        }
        LocalDate start = outgoing.getStartDate() != null && outgoing.getStartDate().isAfter(LocalDate.now())
                ? outgoing.getStartDate() : LocalDate.now();
        if (outgoing.getEndDate() == null || !outgoing.getEndDate().isAfter(start)) {
            throw new BadRequestException("LEASE_EXPIRED", "Hợp đồng đã hết hạn chuyển nhượng");
        }
        Room room = roomRepository.findById(swap.getCurrentRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng hiện tại"));
        entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);
        if (!swap.getLandlordId().equals(room.getLandlordId()) || room.getStatus() != RoomStatus.RENTED) {
            throw new BadRequestException("ROOM_NOT_RENTED", "Phòng không thể chuyển nhượng");
        }
        outgoing.setStatus(RentalStatus.TERMINATED);
        outgoing.setIsLeaseholder(false);
        rentalRepository.saveAndFlush(outgoing);
        rentalRepository.save(Rental.builder().roomId(room.getId()).landlordId(room.getLandlordId())
                .tenantId(swap.getMatchedTenantId()).startDate(start).endDate(outgoing.getEndDate())
                .monthlyRent(outgoing.getMonthlyRent() != null ? outgoing.getMonthlyRent() : room.getPrice())
                .depositAmount(outgoing.getDepositAmount() != null ? outgoing.getDepositAmount() : room.getDepositAmount())
                .status(RentalStatus.PENDING_CHECKIN).isLeaseholder(true).build());
        room.setStatus(RoomStatus.AVAILABLE);
        roomRepository.save(room);
        for (UUID recipient : List.of(swap.getRequesterId(), swap.getMatchedTenantId())) {
            notificationService.create(recipient, "Đã chuyển nhượng hợp đồng", "Hợp đồng mới đang chờ người nhận tạo mã Check-in",
                    NotificationType.SYSTEM, Map.of("swapId", swap.getId()));
        }
    }

    private SwapProposalResponse mapProposal(SwapRequest swap) {
        User applicant = swap.getMatchedTenantId() != null ? userRepository.findById(swap.getMatchedTenantId()).orElse(null) : null;
        return SwapProposalResponse.builder().id(swap.getId()).swapPostId(swap.getId())
                .requesterId(swap.getMatchedTenantId()).requesterName(applicant != null ? applicant.getFullName() : "Người dùng")
                .offeredRoomId(swap.getOfferedRoomId()).message(swap.getProposalMessage()).status(swap.getStatus())
                .createdAt(swap.getProposalCreatedAt()).build();
    }

    private SwapPostResponse mapToResponse(SwapRequest post) {
        User requester = userRepository.findById(post.getRequesterId()).orElse(null);
        UserProfile profile = userProfileRepository.findById(post.getRequesterId()).orElse(null);
        Room currentRoom = roomRepository.findById(post.getCurrentRoomId()).orElse(null);

        List<String> images = (currentRoom != null && currentRoom.getImages() != null)
                ? currentRoom.getImages().stream().map(RoomImage::getImageUrl).toList()
                : List.of();

        return SwapPostResponse.builder()
                .id(post.getId())
                .requesterId(post.getRequesterId())
                .userId(post.getRequesterId())
                .authorName(requester != null ? requester.getFullName() : "Người dùng")
                .authorAvatar(requester != null ? requester.getAvatarUrl() : null)
                .authorSchool(profile != null ? profile.getSchoolOrCompany() : null)
                .currentRoomId(post.getCurrentRoomId())
                .currentRoomTitle(currentRoom != null ? currentRoom.getTitle() : "Phòng đang thuê")
                .currentRoomPrice(currentRoom != null ? currentRoom.getPrice() : null)
                .currentRoomDistrict(currentRoom != null ? currentRoom.getDistrict() : null)
                .currentRoomArea(currentRoom != null ? currentRoom.getAreaSqm() : null)
                .currentRoomImages(images)
                .isLeaseholder(post.getIsLeaseholder())
                .title("Tìm người hoán đổi / pass phòng: " + post.getTargetRoomType())
                .description(post.getReason())
                .reason(post.getReason())
                .targetDistricts(post.getTargetDistricts())
                .desiredDistricts(post.getTargetDistricts())
                .targetRoomType(post.getTargetRoomType())
                .targetBudgetMax(post.getTargetBudgetMax())
                .desiredPriceMax(post.getTargetBudgetMax())
                .habits(post.getHabits())
                .targetMoveInDate(post.getTargetMoveInDate())
                .moveInDate(post.getTargetMoveInDate())
                .matchedTenantId(post.getMatchedTenantId())
                .landlordId(post.getLandlordId())
                .landlordDecision(post.getLandlordDecision())
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
