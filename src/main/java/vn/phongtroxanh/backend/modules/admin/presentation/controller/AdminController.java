package vn.phongtroxanh.backend.modules.admin.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.admin.application.service.AdminService;
import vn.phongtroxanh.backend.modules.admin.domain.ReportSeverity;
import vn.phongtroxanh.backend.modules.admin.domain.ReportStatus;
import vn.phongtroxanh.backend.modules.admin.presentation.dto.*;
import vn.phongtroxanh.backend.modules.room.domain.RoomStatus;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Module 10: Admin Control & KYC Auditing", description = "Các API bảng điều khiển quản trị, kiểm duyệt hồ sơ căn cước công dân (KYC), kiểm duyệt phòng trọ, giải quyết khiếu nại, báo cáo vi phạm và nhật ký kiểm toán")
public class AdminController {

    private final AdminService adminService;

    @GetMapping({"/dashboard", "/analytics/overview"})
    @Operation(summary = "API #77 / #80: Thống kê tổng quan nền tảng", description = "Xem các chỉ số KPI toàn hệ thống: tổng người dùng, doanh thu, phòng đang cho thuê, ghép đôi...")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thống kê dashboard thành công", adminService.getDashboard()));
    }

    @GetMapping({"/kyc/pending", "/kyc/queue"})
    @Operation(summary = "API #79 / #81: Danh sách hồ sơ CCCD chờ duyệt (hỗ trợ phân trang và tìm kiếm)", description = "Lấy danh sách các hồ sơ định danh CCCD đã nộp chờ kiểm duyệt kèm tìm kiếm theo tên, email, sđt và lọc vai trò")
    public ResponseEntity<ApiResponse<Page<KycAuditItemDTO>>> getPendingKyc(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) UserRole role,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hồ sơ CCCD chờ duyệt thành công", adminService.getPendingKycPage(search, role, page, limit)));
    }

    @PutMapping("/kyc/{id}/approve")
    @Operation(summary = "API #80 (Spec) / #82: Phê duyệt hồ sơ CCCD/KYC", description = "Admin duyệt hồ sơ căn cước, kích hoạt huy hiệu Tích xanh và thưởng +30 điểm TrustScore")
    public ResponseEntity<ApiResponse<Void>> approveKyc(@PathVariable("id") UUID id) {
        adminService.approveKyc(id);
        return ResponseEntity.ok(ApiResponse.ok("Phê duyệt hồ sơ CCCD thành công", null));
    }

    @PutMapping("/kyc/{id}/reject")
    @Operation(summary = "API #81 (Spec) / #83: Từ chối hồ sơ CCCD/KYC", description = "Admin từ chối hồ sơ căn cước kèm lý do cụ thể")
    public ResponseEntity<ApiResponse<Void>> rejectKyc(
            @PathVariable("id") UUID id,
            @Valid @RequestBody KycRejectRequest request) {

        adminService.rejectKyc(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Từ chối hồ sơ CCCD thành công", null));
    }

    @GetMapping("/users")
    @Operation(summary = "API #82 (Spec) / #84: Quản lý danh sách người dùng", description = "Tìm kiếm, lọc danh sách tài khoản theo vai trò, trạng thái có phân trang")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getUsers(
            @RequestParam(value = "role", required = false) UserRole role,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách người dùng thành công", adminService.getUsers(role, status, page, limit)));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "API #83 (Spec) / #85: Cập nhật trạng thái người dùng (Khóa/Cảnh báo)", description = "Admin cập nhật trạng thái ACTIVE, WARNED hoặc LOCKED đối với người dùng vi phạm")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {

        adminService.updateUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái người dùng thành công", null));
    }

    @PutMapping("/users/{id}/verify")
    @Operation(summary = "Admin: Cấp hoặc gỡ xác minh tài khoản", description = "Cấp tích xanh xác minh thủ công hoặc thu hồi cho người dùng")
    public ResponseEntity<ApiResponse<Void>> verifyUser(
            @PathVariable("id") UUID id,
            @RequestParam(value = "verified", defaultValue = "true") boolean verified) {

        adminService.verifyUser(id, verified);
        return ResponseEntity.ok(ApiResponse.ok(verified ? "Cấp xác minh người dùng thành công" : "Thu hồi xác minh thành công", null));
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "Admin: Đặt lại mật khẩu tạm thời cho người dùng", description = "Tạo mật khẩu tạm thời, mã hóa lưu DB, gửi trực tiếp qua email người dùng và thông báo in-app (không hiển thị mật khẩu cho admin)")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> resetUserPassword(@PathVariable("id") UUID id) {
        String maskedEmail = adminService.adminResetPassword(id);
        return ResponseEntity.ok(ApiResponse.ok("Mật khẩu mới đã được tạo và gửi an toàn đến email của người dùng", java.util.Map.of("maskedEmail", maskedEmail)));
    }

    @PostMapping("/users/{id}/notify")
    @Operation(summary = "Admin: Gửi thông báo/email cho người dùng", description = "Gửi thông báo hệ thống và email trực tiếp từ ban quản trị đến tài khoản người dùng")
    public ResponseEntity<ApiResponse<Void>> notifyUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminNotifyUserRequest request) {

        adminService.sendUserNotification(id, request.getTitle(), request.getMessage());
        return ResponseEntity.ok(ApiResponse.ok("Gửi thông báo đến người dùng thành công", null));
    }

    @GetMapping("/rooms")
    @Operation(summary = "API #86: Quản lý danh sách phòng trọ", description = "Lấy danh sách tất cả các phòng trọ trên toàn hệ thống kèm trạng thái (không rò rỉ lớp JTS)")
    public ResponseEntity<ApiResponse<Page<AdminRoomResponse>>> getRooms(
            @RequestParam(value = "status", required = false) RoomStatus status,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phòng trọ thành công", adminService.getRooms(status, page, limit)));
    }

    @PutMapping("/rooms/{id}/verify")
    @Operation(summary = "API #87: Cấp tích xanh phòng trọ đã xác thực", description = "Admin xác nhận phòng trọ chính chủ, thực tế đúng mô tả")
    public ResponseEntity<ApiResponse<Void>> verifyRoom(@PathVariable("id") UUID id) {
        adminService.verifyRoom(id);
        return ResponseEntity.ok(ApiResponse.ok("Xác thực phòng trọ thành công", null));
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(summary = "API #88: Gỡ bài đăng phòng trọ vi phạm", description = "Admin gỡ bài đăng phòng trọ vi phạm chính sách (chuyển sang HIDDEN)")
    public ResponseEntity<ApiResponse<Void>> takeDownRoom(@PathVariable("id") UUID id) {
        adminService.takeDownRoom(id);
        return ResponseEntity.ok(ApiResponse.ok("Gỡ phòng trọ vi phạm thành công", null));
    }

    @GetMapping({"/reviews/disputes", "/disputes"})
    @Operation(summary = "API #89: Danh sách khiếu nại đánh giá", description = "Admin xem danh sách các khiếu nại đánh giá đang chờ giải quyết")
    public ResponseEntity<ApiResponse<Page<AdminReviewDisputeResponse>>> getDisputes(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách khiếu nại thành công", adminService.getDisputes(page, limit)));
    }

    @RequestMapping(value = {"/reviews/disputes/{id}/resolve", "/disputes/{id}/resolve"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "API #90: Phán quyết xử lý khiếu nại đánh giá", description = "Admin phán quyết giữ nguyên đánh giá hoặc gỡ bỏ đánh giá ác ý và khôi phục TrustScore")
    public ResponseEntity<ApiResponse<Void>> resolveDispute(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ResolveDisputeRequest request) {

        adminService.resolveDispute(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Phán quyết xử lý khiếu nại thành công", null));
    }

    @GetMapping("/transactions")
    @Operation(summary = "API #91 (Spec) / Giao dịch: Nhật ký kiểm toán thanh toán", description = "Admin xem toàn bộ lịch sử các giao dịch nạp tiền, mua gói dịch vụ trên hệ thống")
    public ResponseEntity<ApiResponse<Page<AdminPaymentTransactionResponse>>> getTransactions(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy nhật ký giao dịch thành công", adminService.getTransactions(page, limit)));
    }

    @GetMapping("/reports")
    @Operation(summary = "API #86 (Spec): Danh sách báo cáo vi phạm cộng đồng", description = "Lọc danh sách báo cáo vi phạm theo trạng thái và mức độ nghiêm trọng")
    public ResponseEntity<ApiResponse<Page<AdminReportResponse>>> getReports(
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestParam(value = "severity", required = false) ReportSeverity severity,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách báo cáo thành công", adminService.getReports(status, severity, page, limit)));
    }

    @GetMapping("/reports/{id}")
    @Operation(summary = "API #87 (Spec): Chi tiết 1 báo cáo vi phạm", description = "Xem chi tiết báo cáo vi phạm kèm hình ảnh bằng chứng và đối tượng vi phạm")
    public ResponseEntity<ApiResponse<AdminReportResponse>> getReportDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết báo cáo thành công", adminService.getReportDetail(id)));
    }

    @PostMapping("/reports/{id}/action")
    @Operation(summary = "API #88 (Spec): Xử lý báo cáo vi phạm", description = "Admin thực hiện hành động xử lý vi phạm: resolve, warn, remove_content, ban, dismiss")
    public ResponseEntity<ApiResponse<AdminReportResponse>> handleReportAction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminReportActionRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Xử lý báo cáo vi phạm thành công", adminService.handleReportAction(id, request)));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "API #91 (Spec): Nhật ký kiểm toán doanh nghiệp", description = "Nhật ký truy vết toàn bộ thao tác can thiệp dữ liệu nhạy cảm của Admin")
    public ResponseEntity<ApiResponse<Page<AdminAuditLogResponse>>> getAuditLogs(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok("Lấy nhật ký kiểm toán thành công", adminService.getAuditLogs(page, limit)));
    }
}
