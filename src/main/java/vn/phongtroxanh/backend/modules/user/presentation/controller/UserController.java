package vn.phongtroxanh.backend.modules.user.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.user.application.service.UserService;
import vn.phongtroxanh.backend.modules.user.presentation.dto.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Module 2: User Profiles, KYC & TrustScore", description = "Các API hồ sơ cá nhân, tiêu chí bạn cùng phòng, điểm tín nhiệm TrustScore và duyệt CCCD")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "API #13: Xem thông tin cá nhân", description = "Lấy toàn bộ thông tin cá nhân của người dùng hiện tại, số dư lượt vuốt, gói active, điểm TrustScore")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin cá nhân thành công", userService.getMe()));
    }

    @PutMapping("/me")
    @Operation(summary = "API #14: Cập nhật thông tin cơ bản", description = "Cập nhật Họ tên, Ngày sinh, Giới tính, Trường/Công ty, Bio")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin thành công", userService.updateMe(request)));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "API #15: Tải lên ảnh đại diện", description = "Tải lên ảnh đại diện cá nhân dạng multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = userService.uploadAvatar(file);
        return ResponseEntity.ok(ApiResponse.ok("Tải lên ảnh đại diện thành công", Map.of("avatarUrl", url)));
    }

    @GetMapping("/me/matching-profile")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "API #16: Xem ma trận thói quen sinh hoạt", description = "Lấy chi tiết ma trận thói quen sinh hoạt và tiêu chí tìm bạn ở ghép của người thuê")
    public ResponseEntity<ApiResponse<MatchingProfileResponse>> getMatchingProfile() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy hồ sơ tìm bạn ở ghép thành công", userService.getMatchingProfile()));
    }

    @PutMapping("/me/matching-profile")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "API #17: Cập nhật ma trận thói quen sinh hoạt", description = "Cập nhật toàn bộ thói quen: Ngủ sớm, Gọn gàng, Tiếp khách, Thuốc lá, Slider ồn, Ngân sách, Quận, Loại phòng, 4 tiêu chí Proximity")
    public ResponseEntity<ApiResponse<MatchingProfileResponse>> updateMatchingProfile(@Valid @RequestBody MatchingProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tiêu chí bạn cùng phòng thành công", userService.updateMatchingProfile(request)));
    }

    @GetMapping("/me/trust-score")
    @Operation(summary = "API #18: Xem chi tiết điểm uy tín TrustScore", description = "Lấy chi tiết điểm TrustScore (0-100), tiến độ 4 trụ cột, huy hiệu và lịch sử biến động điểm")
    public ResponseEntity<ApiResponse<TrustScoreResponse>> getTrustScore() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy điểm uy tín thành công", userService.getTrustScore()));
    }

    @PostMapping("/me/kyc/cccd")
    @Operation(summary = "API #19: Nộp hồ sơ xác thực CCCD/KYC", description = "Tải lên 2 mặt ảnh CCCD + Số CCCD mã hóa AES-256 gửi vào hàng đợi duyệt của Admin")
    public ResponseEntity<ApiResponse<KycStatusResponse>> submitKyc(@Valid @RequestBody KycSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Gửi hồ sơ xác minh CCCD thành công", userService.submitKyc(request)));
    }

    @GetMapping("/me/kyc/status")
    @Operation(summary = "API #20: Tra cứu trạng thái kiểm duyệt CCCD", description = "Tra cứu trạng thái kiểm duyệt CCCD (PENDING, APPROVED, REJECTED + lý do)")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatus() {
        return ResponseEntity.ok(ApiResponse.ok("Tra cứu trạng thái KYC thành công", userService.getKycStatus()));
    }

    @GetMapping("/{userId}/public")
    @Operation(summary = "API #21: Xem hồ sơ công khai của người dùng", description = "Xem hồ sơ công khai của người dùng khác (đã ẩn toàn bộ PII nhạy cảm)")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getPublicProfile(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy hồ sơ công khai thành công", userService.getPublicProfile(userId)));
    }

    @GetMapping("/me/settings")
    @Operation(summary = "API #22: Lấy cấu hình thông báo & quyền riêng tư", description = "Lấy cấu hình quyền riêng tư và hiển thị trạng thái")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy cài đặt thành công", userService.getSettings()));
    }

    @PutMapping("/me/settings")
    @Operation(summary = "API #23: Cập nhật cấu hình thông báo & quyền riêng tư", description = "Cập nhật cấu hình thông báo và quyền riêng tư")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateSettings(@Valid @RequestBody UserSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật cài đặt thành công", userService.updateSettings(request)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "API #24: Yêu cầu xóa tài khoản", description = "Soft delete tài khoản (status = DELETED) sau khi kiểm tra không có ràng buộc hợp đồng còn hiệu lực")
    public ResponseEntity<ApiResponse<Void>> deleteAccount() {
        userService.deleteAccount();
        return ResponseEntity.ok(ApiResponse.ok("Tài khoản của bạn đã được đánh dấu xóa", null));
    }
}
