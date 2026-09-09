package vn.phongtroxanh.backend.modules.review.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.review.application.service.ReviewService;
import vn.phongtroxanh.backend.modules.review.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Module 7: Reviews, Evidence & Disputes", description = "Các API đánh giá 2 chiều (Chủ trọ ↔ Người thuê), tải lên ảnh bằng chứng và quy trình khiếu nại 4 lớp")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(summary = "API #60: Đăng đánh giá", description = "Đăng đánh giá 2 chiều sau khi hoàn thành hoặc đang thuê phòng, tự động cập nhật điểm TrustScore")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(@Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gửi đánh giá thành công", response));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "API #61: Danh sách đánh giá phòng", description = "Lấy toàn bộ đánh giá công khai của một phòng trọ cụ thể")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsForRoom(@PathVariable("roomId") UUID roomId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đánh giá phòng thành công", reviewService.getReviewsForRoom(roomId)));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "API #62: Danh sách đánh giá người dùng", description = "Lấy toàn bộ đánh giá công khai của một người dùng (Người thuê hoặc Chủ trọ)")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsForUser(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đánh giá người dùng thành công", reviewService.getReviewsForUser(userId)));
    }

    @PostMapping("/{id}/dispute")
    @Operation(summary = "API #63: Gửi khiếu nại đánh giá (Dispute)", description = "Người nhận đánh giá gửi yêu cầu xem xét khiếu nại lên ban quản trị")
    public ResponseEntity<ApiResponse<ReviewResponse>> submitDispute(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SubmitDisputeRequest request) {

        ReviewResponse response = reviewService.submitDispute(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Gửi khiếu nại đánh giá thành công", response));
    }

    @PostMapping(value = "/{id}/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "API #64: Tải lên bằng chứng khiếu nại/đánh giá", description = "Tải lên hình ảnh bằng chứng đối chất (hư hỏng, hóa đơn, tin nhắn)")
    public ResponseEntity<ApiResponse<List<String>>> uploadEvidence(
            @PathVariable("id") UUID id,
            @RequestParam("files") List<MultipartFile> files) {

        List<String> urls = reviewService.uploadEvidence(id, files);
        return ResponseEntity.ok(ApiResponse.ok("Tải lên bằng chứng thành công", urls));
    }

    @GetMapping("/{id}")
    @Operation(summary = "API #65: Chi tiết đánh giá & bằng chứng", description = "Xem chi tiết một đánh giá kèm ảnh bằng chứng")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết đánh giá thành công", reviewService.getReviewDetail(id)));
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "API #66: Phản hồi công khai đánh giá", description = "Người nhận đánh giá viết phản hồi công khai")
    public ResponseEntity<ApiResponse<ReviewResponse>> replyReview(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReplyReviewRequest request) {

        ReviewResponse response = reviewService.replyReview(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Gửi phản hồi thành công", response));
    }

    @GetMapping("/disputes/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "API #67: Danh sách khiếu nại chờ Admin duyệt", description = "Admin lấy danh sách các khiếu nại đánh giá đang ở trạng thái PENDING")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getPendingDisputes(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách khiếu nại thành công", reviewService.getPendingDisputes(page, limit)));
    }
}
