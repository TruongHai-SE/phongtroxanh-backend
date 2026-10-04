package vn.phongtroxanh.backend.modules.monetization.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.monetization.application.service.PaymentService;
import vn.phongtroxanh.backend.modules.monetization.domain.PackagePlan;
import vn.phongtroxanh.backend.modules.monetization.presentation.dto.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monetization")
@RequiredArgsConstructor
@Tag(name = "Module 9: Monetization, Packages & Payments", description = "Các API gói hội viên và thanh toán payOS")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/plans")
    @Operation(summary = "API #73: Danh sách gói dịch vụ", description = "Lấy danh sách các gói dịch vụ đang kích hoạt (Gói người thuê, Gói chủ trọ)")
    public ResponseEntity<ApiResponse<List<PackagePlan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách gói thành công", paymentService.getPackagePlans()));
    }

    @PostMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Tạo gói dịch vụ mới", description = "Tạo mới gói hội viên và tự động phát thông báo tới người dùng")
    public ResponseEntity<ApiResponse<PackagePlan>> createPlan(@Valid @RequestBody AdminPackagePlanRequest request) {
        PackagePlan plan = paymentService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo gói dịch vụ thành công", plan));
    }

    @PutMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Cập nhật gói dịch vụ", description = "Sửa giá và quyền lợi gói, tự động phát thông báo thay đổi tới toàn bộ người dùng")
    public ResponseEntity<ApiResponse<PackagePlan>> updatePlan(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminPackagePlanRequest request) {
        PackagePlan plan = paymentService.updatePlan(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật gói dịch vụ thành công", plan));
    }

    @DeleteMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Xóa gói dịch vụ", description = "Xóa gói dịch vụ khỏi hệ thống (trừ gói FREE)")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable("id") String id) {
        paymentService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa gói dịch vụ thành công", null));
    }

    @PostMapping("/create-payment")
    @Operation(summary = "API #74: Khởi tạo thanh toán payOS", description = "Gửi paymentMethod=PAYOS; có thể gửi Idempotency-Key để retry an toàn")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {

        CreatePaymentResponse response = paymentService.createPayment(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Khởi tạo thanh toán thành công", response));
    }

    @GetMapping("/vnpay-ipn")
    @Operation(summary = "API #75: VNPay đã ngừng sử dụng", description = "Endpoint cũ trả 410; dùng POST /payos-webhook")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> allParams) {
        return ResponseEntity.status(410).body(Map.of("RspCode", "99", "Message", "VNPay disabled; use payOS"));
    }

    @PostMapping("/payos-webhook")
    @Operation(summary = "Webhook payOS", description = "Xác minh chữ ký, số tiền và xử lý giao dịch đúng một lần")
    public ResponseEntity<Map<String, String>> payosWebhook(@RequestBody Map<String, Object> payload) {
        paymentService.processPayOsWebhook(payload);
        return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));
    }

    @GetMapping({"/payos-return", "/vnpay-return"})
    @Operation(summary = "API #76: Đọc trạng thái thanh toán", description = "Cần đăng nhập; chỉ đọc trạng thái đã xác nhận trong database")
    public ResponseEntity<ApiResponse<Map<String, String>>> vnpayReturn(@RequestParam Map<String, String> allParams) {
        String orderCode = allParams.getOrDefault("orderCode", allParams.getOrDefault("vnp_TxnRef", ""));
        var transaction = paymentService.getPaymentResult(orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Trạng thái giao dịch",
                Map.of("transactionCode", transaction.getTransactionCode(), "status", transaction.getStatus().name())));
    }

    @GetMapping("/transactions/me")
    @Operation(summary = "API #77: Lịch sử giao dịch của tôi", description = "Xem lịch sử nạp tiền và mua gói của tài khoản hiện tại")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getMyTransactions() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử giao dịch thành công", paymentService.getMyTransactions()));
    }

    @GetMapping("/consumables/me")
    @Operation(summary = "API #78: Số dư lượt quẹt & lượt đẩy tin", description = "Kiểm tra số lượt quẹt bạn cùng phòng và số lượt đẩy tin phòng trọ còn lại")
    public ResponseEntity<ApiResponse<ConsumableBalanceResponse>> getMyConsumables() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy số dư thành công", paymentService.getMyConsumables()));
    }

    @GetMapping("/subscriptions/me")
    @Operation(summary = "Gói đang hoạt động của tôi", description = "Danh sách gói trả phí còn hiệu lực và ngày hết hạn")
    public ResponseEntity<ApiResponse<List<ActiveSubscriptionResponse>>> getMySubscriptions() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy gói đang hoạt động thành công", paymentService.getMyActiveSubscriptions()));
    }
}
