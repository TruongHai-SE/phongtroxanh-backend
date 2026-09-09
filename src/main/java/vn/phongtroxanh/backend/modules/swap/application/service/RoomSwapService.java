package vn.phongtroxanh.backend.modules.swap.application.service;

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
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.swap.domain.SwapRequest;
import vn.phongtroxanh.backend.modules.swap.domain.SwapStatus;
import vn.phongtroxanh.backend.modules.swap.infrastructure.repository.SwapRequestRepository;
import vn.phongtroxanh.backend.modules.swap.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSwapService {

    private final SwapRequestRepository swapRequestRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional
    public SwapPostResponse createSwapPost(CreateSwapRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Room currentRoom = roomRepository.findById(request.getCurrentRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng hiện tại"));

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
                .isLeaseholder(request.getIsLeaseholder() != null ? request.getIsLeaseholder() : false)
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

        if (post.getRequesterId().equals(currentUserId)) {
            throw new BadRequestException("SELF_PROPOSAL_NOT_ALLOWED", "Bạn không thể gửi đề xuất hoán đổi cho chính bài đăng của mình");
        }

        if (post.getStatus() == SwapStatus.CANCELLED || post.getStatus() == SwapStatus.COMPLETED
                || post.getStatus() == SwapStatus.APPROVED || post.getStatus() != SwapStatus.OPEN) {
            throw new BadRequestException("SWAP_POST_CLOSED", "Bài đăng hoán đổi phòng này đã đóng, không thể gửi đề xuất");
        }

        if (post.getMatchedTenantId() != null) {
            throw new BadRequestException("SWAP_ALREADY_MATCHED", "Bài đăng này đã có người gửi đề xuất đang xử lý");
        }

        post.setMatchedTenantId(currentUserId);
        post.setStatus(SwapStatus.MATCHING);
        post = swapRequestRepository.save(post);

        User requester = userRepository.findById(currentUserId).orElse(null);
        return SwapProposalResponse.builder()
                .id(post.getId())
                .swapPostId(post.getId())
                .requesterId(currentUserId)
                .requesterName(requester != null ? requester.getFullName() : "Người dùng")
                .offeredRoomId(request.getOfferedRoomId())
                .message(request.getMessage())
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .build();
    }

    @Transactional
    public SwapProposalResponse updateSwapProposalStatus(UUID requestId, UpdateSwapRequestStatusRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        SwapRequest swap = swapRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu đề xuất"));

        boolean isRequester = swap.getRequesterId().equals(currentUserId);
        boolean isLandlord = swap.getLandlordId().equals(currentUserId);
        boolean isMatchedTenant = swap.getMatchedTenantId() != null && swap.getMatchedTenantId().equals(currentUserId);

        if (!isRequester && !isLandlord && !isMatchedTenant && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền cập nhật trạng thái yêu cầu hoán đổi này");
        }

        // Applicant (matchedTenant) can only cancel/withdraw their proposal, cannot approve it
        if (isMatchedTenant && !isRequester && request.getStatus() != SwapStatus.CANCELLED && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Người gửi đề xuất chỉ có quyền rút/hủy đề xuất của mình");
        }

        if (swap.getStatus() == SwapStatus.CANCELLED || swap.getStatus() == SwapStatus.COMPLETED) {
            throw new BadRequestException("SWAP_IMMUTABLE", "Yêu cầu hoán đổi đã kết thúc hoặc bị hủy, không thể thay đổi trạng thái");
        }

        swap.setStatus(request.getStatus());
        swap = swapRequestRepository.save(swap);

        User requester = userRepository.findById(swap.getRequesterId()).orElse(null);
        return SwapProposalResponse.builder()
                .id(swap.getId())
                .swapPostId(swap.getId())
                .requesterId(swap.getRequesterId())
                .requesterName(requester != null ? requester.getFullName() : "Người dùng")
                .offeredRoomId(swap.getCurrentRoomId())
                .message(swap.getReason())
                .status(swap.getStatus())
                .createdAt(swap.getCreatedAt())
                .build();
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

        if (!swap.getLandlordId().equals(landlordId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không phải chủ trọ của phòng này");
        }

        if (swap.getStatus() == SwapStatus.CANCELLED || swap.getStatus() == SwapStatus.COMPLETED) {
            throw new BadRequestException("SWAP_IMMUTABLE", "Yêu cầu hoán đổi đã kết thúc hoặc bị hủy, không thể thay đổi quyết định");
        }

        if (swap.getLandlordDecision() == SwapStatus.APPROVED || swap.getLandlordDecision() == SwapStatus.DECLINED) {
            throw new BadRequestException("ALREADY_DECIDED", "Yêu cầu hoán đổi này đã được chủ trọ xử lý trước đó");
        }

        if (approve) {
            swap.setLandlordDecision(SwapStatus.APPROVED);
            swap.setStatus(SwapStatus.APPROVED);
        } else {
            swap.setLandlordDecision(SwapStatus.DECLINED);
            swap.setStatus(SwapStatus.DECLINED);
        }

        swap = swapRequestRepository.save(swap);
        return mapToResponse(swap);
    }

    private SwapPostResponse mapToResponse(SwapRequest post) {
        User requester = userRepository.findById(post.getRequesterId()).orElse(null);
        return SwapPostResponse.builder()
                .id(post.getId())
                .requesterId(post.getRequesterId())
                .userId(post.getRequesterId())
                .authorName(requester != null ? requester.getFullName() : "Người dùng")
                .authorAvatar(requester != null ? requester.getAvatarUrl() : null)
                .currentRoomId(post.getCurrentRoomId())
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
