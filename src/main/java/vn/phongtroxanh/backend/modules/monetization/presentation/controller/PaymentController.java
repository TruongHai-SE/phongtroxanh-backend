package vn.phongtroxanh.backend.modules.monetization.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Module 9: Monetization, Packages & Payments", description = "Các API gói hội viên và cổng thanh toán VNPay Sandbox")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/plans")
    @Operation(summary = "API #73: Danh sách gói dịch vụ", description = "Lấy danh sách các gói dịch vụ đang kích hoạt (Gói người thuê, Gói chủ trọ)")
    public ResponseEntity<ApiResponse<List<PackagePlan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách gói thành công", paymentService.getPackagePlans()));
    }

    @PostMapping("/create-payment")
    @Operation(summary = "API #74: Khởi tạo thanh toán VNPay", description = "Tạo đơn hàng thanh toán qua cổng VNPay Sandbox")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {

        CreatePaymentResponse response = paymentService.createPayment(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Khởi tạo thanh toán thành công", response));
    }

    @GetMapping("/vnpay-ipn")
    @Operation(summary = "API #75: Webhook IPN từ cổng VNPay", description = "Tiếp nhận thông báo kết quả giao dịch tự động từ VNPay (Idempotent, xác thực chữ ký)")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> allParams) {
        Map<String, String> result = paymentService.processVnPayIpn(allParams);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/vnpay-return")
    @Operation(summary = "API #76: URL người dùng quay lại sau thanh toán VNPay", description = "Frontend redirect về endpoint này để nhận trạng thái thanh toán")
    public ResponseEntity<ApiResponse<Map<String, String>>> vnpayReturn(@RequestParam Map<String, String> allParams) {
        String responseCode = allParams.get("vnp_ResponseCode");
        String txnRef = allParams.get("vnp_TxnRef");
        boolean isSuccess = "00".equals(responseCode);

        return ResponseEntity.ok(ApiResponse.ok(
                isSuccess ? "Thanh toán thành công" : "Thanh toán không thành công",
                Map.of("transactionCode", txnRef != null ? txnRef : "", "status", isSuccess ? "SUCCESS" : "FAILED")
        ));
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
}
