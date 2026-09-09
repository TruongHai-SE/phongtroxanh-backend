package vn.phongtroxanh.backend.modules.monetization.application.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.payment.VnPayPaymentAdapter;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.monetization.domain.PackagePlan;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentMethod;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentStatus;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentTransaction;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.PackagePlanRepository;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.PaymentTransactionRepository;
import vn.phongtroxanh.backend.modules.monetization.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.UserConsumable;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PackagePlanRepository packagePlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final VnPayPaymentAdapter vnPayPaymentAdapter;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public List<PackagePlan> getPackagePlans() {
        return packagePlanRepository.findAll();
    }

    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, HttpServletRequest httpRequest) {
        if (request.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new BadRequestException("UNSUPPORTED_PAYMENT_METHOD", "Hệ thống chỉ hỗ trợ thanh toán qua VNPay Sandbox");
        }

        UUID currentUserId = SecurityUtils.getCurrentUserId();

        PackagePlan plan = packagePlanRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("PACKAGE_NOT_FOUND", "Gói dịch vụ không tồn tại"));

        String transactionCode = "PTX" + System.currentTimeMillis() + (int)(Math.random() * 1000);
        String idempotencyKey = "IDEMP_" + UUID.randomUUID();

        BigDecimal price = plan.getPriceMonthly();

        PaymentTransaction transaction = PaymentTransaction.builder()
                .userId(currentUserId)
                .packageId(plan.getId())
                .amount(price)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .transactionCode(transactionCode)
                .idempotencyKey(idempotencyKey)
                .build();

        transaction = paymentTransactionRepository.save(transaction);
        log.info("Initiated payment transaction {} for package {}", transactionCode, plan.getId());

        String returnUrl = request.getReturnUrl() != null && !request.getReturnUrl().isBlank()
                ? request.getReturnUrl()
                : frontendUrl + "/payment/vnpay-return";

        String ipAddress = httpRequest.getRemoteAddr();
        String paymentUrl = vnPayPaymentAdapter.createPaymentUrl(
                transactionCode, price, "Thanh toan goi " + plan.getName(), ipAddress, returnUrl);

        return CreatePaymentResponse.builder()
                .transactionCode(transactionCode)
                .paymentMethod(PaymentMethod.VNPAY)
                .amount(price)
                .paymentUrl(paymentUrl)
                .build();
    }

    @Transactional
    public Map<String, String> processVnPayIpn(Map<String, String> params) {
        log.info("Received VNPay IPN webhook: {}", params);

        if (!vnPayPaymentAdapter.verifyIpn(params)) {
            log.warn("Invalid VNPay IPN signature");
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }

        String txnRef = params.get("vnp_TxnRef");
        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCode(txnRef).orElse(null);
        if (transaction == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        if (transaction.getStatus() != PaymentStatus.PENDING) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        String responseCode = params.get("vnp_ResponseCode");
        String vnpTransactionNo = params.get("vnp_TransactionNo");

        if ("00".equals(responseCode)) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            transaction.setVnpTransactionNo(vnpTransactionNo);
            transaction.setUpdatedAt(Instant.now());
            paymentTransactionRepository.save(transaction);

            // Grant consumables to user
            grantPackageBenefits(transaction.getUserId(), transaction.getPackageId());
            log.info("VNPay payment SUCCESS for txnRef: {}", txnRef);
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setVnpTransactionNo(vnpTransactionNo);
            transaction.setUpdatedAt(Instant.now());
            paymentTransactionRepository.save(transaction);
            log.warn("VNPay payment FAILED for txnRef: {}, response code: {}", txnRef, responseCode);
        }

        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    public List<TransactionResponse> getMyTransactions() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ConsumableBalanceResponse getMyConsumables() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserConsumable c = userConsumableRepository.findById(currentUserId).orElse(null);
        if (c == null) {
            return new ConsumableBalanceResponse(15, 0, 0);
        }
        return new ConsumableBalanceResponse(c.getSwipesLeft(), c.getBoostsLeft(), c.getSuperMatchesLeft());
    }

    private void grantPackageBenefits(UUID userId, String packageId) {
        PackagePlan plan = packagePlanRepository.findById(packageId).orElse(null);
        if (plan == null) return;

        UserConsumable consumable = userConsumableRepository.findById(userId)
                .orElseGet(() -> UserConsumable.builder()
                        .userId(userId)
                        .swipesLeft(15)
                        .boostsLeft(0)
                        .superMatchesLeft(0)
                        .build());

        int swipesToAdd = "PRO_TENANT".equals(packageId) ? 50 : 0;
        int boostsToAdd = "LANDLORD_VIP".equals(packageId) ? 5 : ("PRO_TENANT".equals(packageId) ? 2 : 0);

        consumable.setSwipesLeft(consumable.getSwipesLeft() + swipesToAdd);
        consumable.setBoostsLeft(consumable.getBoostsLeft() + boostsToAdd);
        userConsumableRepository.save(consumable);

        log.info("Granted {} boosts and {} swipes to user {}", boostsToAdd, swipesToAdd, userId);
    }

    private TransactionResponse mapToResponse(PaymentTransaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionCode(t.getTransactionCode())
                .packageId(t.getPackageId())
                .amount(t.getAmount())
                .paymentMethod(t.getPaymentMethod())
                .status(t.getStatus())
                .vnpTransactionNo(t.getVnpTransactionNo())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
