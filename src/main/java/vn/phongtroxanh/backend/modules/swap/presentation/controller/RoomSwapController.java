package vn.phongtroxanh.backend.modules.swap.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.swap.application.service.RoomSwapService;
import vn.phongtroxanh.backend.modules.swap.presentation.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/swaps")
@RequiredArgsConstructor
@Tag(name = "Module 5: Room Swaps & Subleasing", description = "Các API đăng bài pass phòng trọ, hoán đổi vị trí phòng và duyệt yêu cầu chuyển nhượng hợp đồng")
public class RoomSwapController {

    private final RoomSwapService roomSwapService;

    @PostMapping({"", "/posts"})
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "API #48 / #46: Đăng tin yêu cầu hoán đổi phòng", description = "Đăng tin cần pass phòng trọ hoặc tìm người hoán đổi phòng theo quận và mức giá")
    public ResponseEntity<ApiResponse<SwapPostResponse>> createSwapPost(@Valid @RequestBody CreateSwapRequest request) {
        SwapPostResponse response = roomSwapService.createSwapPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đăng bài hoán đổi phòng thành công", response));
    }

    @GetMapping({"", "/feed"})
    @Operation(summary = "API #49 / #45: Khám phá bảng tin hoán đổi phòng thuê công khai", description = "Tìm kiếm danh sách các bài đăng hoán đổi phòng có phân trang và lọc theo giá")
    public ResponseEntity<ApiResponse<Page<SwapPostResponse>>> searchSwaps(
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        Page<SwapPostResponse> result = roomSwapService.searchSwapPosts(maxPrice, keyword, page, limit);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách bài đăng thành công", result));
    }

    @GetMapping({"/me", "/my-posts", "/mine"})
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "API #47 / #61: Xem danh sách tin swap cá nhân", description = "Người thuê xem danh sách các tin swap cá nhân đã đăng và trạng thái")
    public ResponseEntity<ApiResponse<List<SwapPostResponse>>> getMySwaps() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tin swap thành công", roomSwapService.getMySwaps()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "API #50: Chi tiết tin hoán đổi phòng", description = "Xem thông tin chi tiết của bài đăng hoán đổi phòng")
    public ResponseEntity<ApiResponse<SwapPostResponse>> getSwapDetail(@PathVariable("id") UUID id) {
        SwapPostResponse response = roomSwapService.getSwapPostDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết bài đăng thành công", response));
    }

    @PostMapping({"/{id}/request", "/posts/{id}/apply", "/{id}/proposals"})
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "API #51 / #49: Gửi đề xuất / đăng ký nhận hoán đổi phòng", description = "Ứng viên gửi đề xuất hoán đổi phòng hoặc nhận pass phòng kèm tin nhắn")
    public ResponseEntity<ApiResponse<SwapProposalResponse>> sendSwapProposal(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SendSwapProposalRequest request) {

        SwapProposalResponse response = roomSwapService.sendSwapProposal(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gửi đề xuất hoán đổi thành công", response));
    }

    @PutMapping("/requests/{requestId}")
    @Operation(summary = "API #52: Phê duyệt / Từ chối đề xuất hoán đổi phòng", description = "Chủ bài đăng duyệt hoặc từ chối đề xuất hoán đổi phòng")
    public ResponseEntity<ApiResponse<SwapProposalResponse>> updateRequestStatus(
            @PathVariable("requestId") UUID requestId,
            @Valid @RequestBody UpdateSwapRequestStatusRequest request) {

        SwapProposalResponse response = roomSwapService.updateSwapProposalStatus(requestId, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái đề xuất thành công", response));
    }

    @GetMapping("/landlord/requests")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "API #50 (Spec): Chủ trọ xem danh sách yêu cầu chuyển nhượng", description = "Chủ trọ xem danh sách yêu cầu hoán đổi/chuyển nhượng hợp đồng của phòng mình")
    public ResponseEntity<ApiResponse<List<SwapPostResponse>>> getLandlordRequests() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách yêu cầu hoán đổi thành công", roomSwapService.getLandlordRequests()));
    }

    @PutMapping("/landlord/{id}/approve")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "API #51 (Spec): Chủ trọ phê duyệt hoán đổi hợp đồng", description = "Chủ trọ phê duyệt hoán đổi hợp đồng cho người thuê mới")
    public ResponseEntity<ApiResponse<SwapPostResponse>> approveSwap(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Chủ trọ đã phê duyệt hoán đổi", roomSwapService.landlordDecision(id, true)));
    }

    @PutMapping("/landlord/{id}/decline")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "API #52 (Spec): Chủ trọ từ chối hoán đổi hợp đồng", description = "Chủ trọ từ chối yêu cầu hoán đổi hợp đồng")
    public ResponseEntity<ApiResponse<SwapPostResponse>> declineSwap(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Chủ trọ đã từ chối hoán đổi", roomSwapService.landlordDecision(id, false)));
    }
}
