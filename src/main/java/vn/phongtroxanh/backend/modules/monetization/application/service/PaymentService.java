package vn.phongtroxanh.backend.modules.monetization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.model.webhooks.WebhookData;
import vn.phongtroxanh.backend.common.exception.*;
import vn.phongtroxanh.backend.common.payment.PayOsPaymentAdapter;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.monetization.domain.*;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.monetization.presentation.dto.*;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;
import vn.phongtroxanh.backend.modules.user.domain.UserConsumable;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PackagePlanRepository packagePlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserConsumableRepository userConsumableRepository;
    private final PayOsPaymentAdapter gateway;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final NotificationService notifications;
    @Value("${app.frontend-url:http://localhost:5173}") private String frontendUrl;
    @Value("${app.payment.payos.return-url:http://localhost:5173/payment/payos-return}") private String defaultReturnUrl;
    @Value("${app.payment.payos.cancel-url:http://localhost:5173/payment/payos-cancel}") private String cancelUrl;

    public List<PackagePlan> getPackagePlans() { return packagePlanRepository.findAll(); }

    @Transactional
    public PackagePlan createPlan(AdminPackagePlanRequest request) {
        String planId = request.getId() != null && !request.getId().isBlank()
                ? request.getId().trim().toUpperCase()
                : UUID.randomUUID().toString();
        if (packagePlanRepository.existsById(planId)) {
            throw new BadRequestException("PLAN_ALREADY_EXISTS", "Mã gói dịch vụ đã tồn tại: " + planId);
        }

        PackagePlan plan = PackagePlan.builder()
                .id(planId)
                .targetRole(request.getTargetRole())
                .name(request.getName().trim())
                .priceMonthly(request.getPriceMonthly())
                .priceYearly(request.getPriceYearly())
                .features(request.getFeatures().trim())
                .build();

        plan = packagePlanRepository.save(plan);
        log.info("Admin created new package plan {}", plan.getId());

        notifications.broadcast(
                "Gói dịch vụ mới: " + plan.getName(),
                "Hệ thống vừa ra mắt gói dịch vụ mới '" + plan.getName() + "'. Khám phá ngay các ưu đãi hấp dẫn!",
                NotificationType.SYSTEM,
                Map.of("planId", plan.getId())
        );

        return plan;
    }

    @Transactional
    public PackagePlan updatePlan(String planId, AdminPackagePlanRequest request) {
        PackagePlan plan = packagePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("PLAN_NOT_FOUND", "Không tìm thấy gói dịch vụ: " + planId));

        boolean priceOrFeaturesChanged = !plan.getName().equals(request.getName().trim())
                || plan.getPriceMonthly().compareTo(request.getPriceMonthly()) != 0
                || plan.getPriceYearly().compareTo(request.getPriceYearly()) != 0
                || !plan.getFeatures().equals(request.getFeatures().trim());

        plan.setName(request.getName().trim());
        plan.setTargetRole(request.getTargetRole());
        plan.setPriceMonthly(request.getPriceMonthly());
        plan.setPriceYearly(request.getPriceYearly());
        plan.setFeatures(request.getFeatures().trim());

        plan = packagePlanRepository.save(plan);
        log.info("Admin updated package plan {}", plan.getId());

        // When content/price changed, broadcast notification to all users
        if (priceOrFeaturesChanged) {
            notifications.broadcast(
                    "Cập nhật gói dịch vụ: " + plan.getName(),
                    "Gói dịch vụ '" + plan.getName() + "' vừa được điều chỉnh nội dung ưu đãi và giá cước. Vui lòng kiểm tra chi tiết!",
                    NotificationType.SYSTEM,
                    Map.of("planId", plan.getId())
            );
        }

        return plan;
    }

    @Transactional
    public void deletePlan(String planId) {
        PackagePlan plan = packagePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("PLAN_NOT_FOUND", "Không tìm thấy gói dịch vụ: " + planId));

        if ("FREE".equalsIgnoreCase(planId)) {
            throw new BadRequestException("CANNOT_DELETE_DEFAULT_PLAN", "Không thể xóa gói mặc định FREE");
        }

        packagePlanRepository.delete(plan);
        log.info("Admin deleted package plan {}", planId);
    }

    public CreatePaymentResponse createPayment(CreatePaymentRequest request, HttpServletRequest httpRequest) {
        if (request.getPaymentMethod() != PaymentMethod.PAYOS)
            throw new BadRequestException("UNSUPPORTED_PAYMENT_METHOD", "Hệ thống sử dụng phương thức PAYOS");
        gateway.requireConfigured();
        UUID userId = SecurityUtils.getCurrentUserId();
        String header = httpRequest.getHeader("Idempotency-Key");
        if (header != null && !header.matches("[A-Za-z0-9_-]{1,60}"))
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key phải gồm 1 đến 60 chữ, số, dấu gạch ngang hoặc gạch dưới");
        String key = userId + ":" + (header == null ? UUID.randomUUID() : header);
        String returnUrl = validateReturnUrl(request.getReturnUrl());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        PaymentTransaction payment;
        try { payment = tx.execute(status -> reserve(request, userId, key, returnUrl)); }
        catch (DataIntegrityViolationException race) {
            payment = paymentTransactionRepository.findByIdempotencyKey(key).orElseThrow(() -> race);
            assertSameRequest(payment, request, returnUrl);
        }
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getQrExpiredAt().isBefore(Instant.now()))
            throw new ConflictException("PAYMENT_EXPIRED", "Đơn thanh toán đã hết hạn. Hãy tạo đơn với Idempotency-Key mới");
        if (payment.getPaymentUrl() != null) return checkoutResponse(payment);
        // Persist before network I/O: a timeout must not lose the gateway order.
        var link = gateway.create(Long.parseLong(payment.getGatewayOrderId()), payment.getAmount().longValueExact(),
                payment.getReturnUrl(), cancelUrl, payment.getQrExpiredAt());
        if (!Objects.equals(link.getOrderCode(), Long.valueOf(payment.getGatewayOrderId())) || link.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.valueOf(link.getAmount())) != 0 || !"VND".equals(link.getCurrency()))
            throw new BadRequestException("PAYMENT_DATA_MISMATCH", "Thông tin đơn từ payOS không khớp");
        String orderId = payment.getGatewayOrderId();
        PaymentTransaction saved = tx.execute(status -> {
            var locked = paymentTransactionRepository.findLockedByOrderId(orderId).orElseThrow();
            if (locked.getPaymentUrl() == null) {
                locked.setPaymentUrl(link.getCheckoutUrl());
                locked.setPaymentLinkId(link.getPaymentLinkId());
                paymentTransactionRepository.save(locked);
            }
            return locked;
        });
        var result = checkoutResponse(saved);
        result.setQrCode(link.getQrCode());
        return result;
    }

    private PaymentTransaction reserve(CreatePaymentRequest request, UUID userId, String key, String returnUrl) {
        var existing = paymentTransactionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) { assertSameRequest(existing.get(), request, returnUrl); return existing.get(); }
        var plan = packagePlanRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("PACKAGE_NOT_FOUND", "Gói dịch vụ không tồn tại"));
        if (!SecurityUtils.hasRole(plan.getTargetRole().name()))
            throw new ForbiddenException("PACKAGE_ROLE_MISMATCH", "Gói dịch vụ không dành cho loại tài khoản của bạn");
        if (plan.getPriceMonthly().signum() <= 0)
            throw new BadRequestException("FREE_PACKAGE", "Gói miễn phí không cần thanh toán");
        try { plan.getPriceMonthly().longValueExact(); }
        catch (ArithmeticException ex) { throw new BadRequestException("INVALID_PAYMENT_AMOUNT", "Số tiền phải là số nguyên VND"); }
        Map<String,Integer> benefits;
        try {
            var features = objectMapper.readTree(plan.getFeatures());
            benefits = Map.of("swipes_per_day", Math.max(15, features.path("swipes_per_day").asInt(15)),
                    "boosts", Math.max(0, features.path("boosts").asInt(0)));
        } catch (Exception ex) { throw new BadRequestException("INVALID_PACKAGE", "Cấu hình gói dịch vụ không hợp lệ"); }
        Long orderCode = jdbc.queryForObject("SELECT nextval('payos_order_code_seq')", Long.class);
        return paymentTransactionRepository.saveAndFlush(PaymentTransaction.builder().userId(userId).packageId(plan.getId()).itemName(plan.getId())
                .amount(plan.getPriceMonthly()).paymentMethod(PaymentMethod.PAYOS).status(PaymentStatus.PENDING)
                .gatewayOrderId(String.valueOf(orderCode)).idempotencyKey(key).returnUrl(returnUrl)
                .benefits(benefits).qrExpiredAt(Instant.now().plusSeconds(1800)).build());
    }

    private void assertSameRequest(PaymentTransaction payment, CreatePaymentRequest request, String returnUrl) {
        if (!Objects.equals(payment.getPackageId(), request.getPackageId()) || !Objects.equals(payment.getReturnUrl(), returnUrl))
            throw new ConflictException("IDEMPOTENCY_CONFLICT", "Idempotency-Key đã dùng cho một yêu cầu khác");
    }

    private String validateReturnUrl(String supplied) {
        String value = supplied == null || supplied.isBlank() ? defaultReturnUrl : supplied;
        if (value.length() > 1000) throw new BadRequestException("INVALID_RETURN_URL", "URL quay lại tối đa 1000 ký tự");
        try {
            URI uri = URI.create(value), allowed = URI.create(frontendUrl);
            if (uri.getScheme() == null || !Set.of("http","https").contains(uri.getScheme()) || uri.getUserInfo() != null
                    || !Objects.equals(uri.getScheme(), allowed.getScheme()) || !Objects.equals(uri.getHost(), allowed.getHost())
                    || uri.getPort() != allowed.getPort()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw new BadRequestException("INVALID_RETURN_URL", "URL quay lại phải thuộc FRONTEND_URL của hệ thống"); }
        return value;
    }

    @Transactional
    public void processPayOsWebhook(Map<String,Object> payload) {
        WebhookData data = gateway.verify(payload);
        if (!"00".equals(data.getCode())) return;
        var transaction = paymentTransactionRepository.findLockedByOrderId(String.valueOf(data.getOrderCode())).orElse(null);
        // payOS sends a signed test order when registering the webhook.
        if (transaction == null) return;
        if (data.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.valueOf(data.getAmount())) != 0
                || !"VND".equals(data.getCurrency()) || transaction.getPaymentMethod() != PaymentMethod.PAYOS
                || (transaction.getPaymentLinkId() != null && !Objects.equals(transaction.getPaymentLinkId(),data.getPaymentLinkId())))
            throw new BadRequestException("PAYMENT_DATA_MISMATCH", "Số tiền hoặc thông tin đơn thanh toán không khớp");
        if (transaction.getStatus() == PaymentStatus.SUCCESS) return;
        if (transaction.getStatus() != PaymentStatus.PENDING)
            throw new ConflictException("INVALID_PAYMENT_STATUS", "Đơn thanh toán không ở trạng thái chờ xác nhận");
        grantPackageBenefits(transaction);
        transaction.setPaymentLinkId(data.getPaymentLinkId());
        transaction.setGatewayReference(data.getReference());
        transaction.setStatus(PaymentStatus.SUCCESS);
        paymentTransactionRepository.save(transaction);
        notifications.create(transaction.getUserId(), "Thanh toán thành công", "Gói dịch vụ đã được kích hoạt",
                NotificationType.PAYMENT, Map.of("transactionCode",transaction.getTransactionCode()));
    }

    private void grantPackageBenefits(PaymentTransaction payment) {
        UUID userId = payment.getUserId();
        jdbc.update("INSERT INTO user_consumables(user_id) VALUES (?) ON CONFLICT DO NOTHING",userId);
        jdbc.queryForObject("SELECT user_id FROM user_consumables WHERE user_id=? FOR UPDATE",UUID.class,userId);
        userConsumableRepository.resetDailySwipesAtomic(userId,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        int oldQuota = jdbc.queryForObject("SELECT COALESCE(MAX((p.features->>'swipes_per_day')::integer),15) FROM subscriptions s JOIN package_plans p ON p.id=s.plan_id WHERE s.user_id=? AND s.is_active AND s.start_date<=NOW() AND s.end_date>NOW()",Integer.class,userId);
        int increase = Math.max(0,payment.getBenefits().getOrDefault("swipes_per_day",15)-Math.max(15,oldQuota));
        jdbc.update("UPDATE user_consumables SET swipes_left=swipes_left+?, free_swipes_left=free_swipes_left+?, boosts_left=boosts_left+?, version=version+1 WHERE user_id=?",
                increase,increase,payment.getBenefits().getOrDefault("boosts",0),userId);
        jdbc.update("INSERT INTO subscriptions(user_id,plan_id,billing_cycle,start_date,end_date) VALUES (?,?,'MONTHLY',NOW(),GREATEST(NOW(),COALESCE((SELECT MAX(end_date) FROM subscriptions WHERE user_id=? AND plan_id=? AND is_active),NOW())) + INTERVAL '30 days')",
                userId,payment.getPackageId(),userId,payment.getPackageId());
    }

    public TransactionResponse getPaymentResult(String orderCode) {
        var payment = paymentTransactionRepository.findByGatewayOrderId(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch"));
        SecurityUtils.assertOwnership(payment.getUserId(),"Bạn không có quyền xem giao dịch này");
        return mapToResponse(payment);
    }
    public List<TransactionResponse> getMyTransactions() {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtils.getCurrentUserId()).stream().map(this::mapToResponse).toList();
    }
    @Transactional public ConsumableBalanceResponse getMyConsumables() {
        UUID userId = SecurityUtils.getCurrentUserId();
        userConsumableRepository.resetDailySwipesAtomic(userId,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        UserConsumable c = userConsumableRepository.findById(userId).orElse(null);
        return c == null ? new ConsumableBalanceResponse(15,0) : new ConsumableBalanceResponse(c.getSwipesLeft(),c.getBoostsLeft());
    }
    public List<ActiveSubscriptionResponse> getMyActiveSubscriptions() {
        return jdbc.query("SELECT s.plan_id, p.name, MAX(s.end_date) AS end_date FROM subscriptions s JOIN package_plans p ON p.id=s.plan_id"
                        + " WHERE s.user_id=? AND s.is_active AND s.start_date<=NOW() AND s.end_date>NOW() GROUP BY s.plan_id, p.name ORDER BY end_date DESC",
                (rs, i) -> new ActiveSubscriptionResponse(rs.getString("plan_id"), rs.getString("name"), rs.getTimestamp("end_date").toInstant()),
                SecurityUtils.getCurrentUserId());
    }
    private CreatePaymentResponse checkoutResponse(PaymentTransaction payment) {
        return CreatePaymentResponse.builder().transactionCode(payment.getTransactionCode()).paymentMethod(PaymentMethod.PAYOS)
                .amount(payment.getAmount()).paymentUrl(payment.getPaymentUrl()).paymentLinkId(payment.getPaymentLinkId()).build();
    }
    private TransactionResponse mapToResponse(PaymentTransaction payment) {
        return TransactionResponse.builder().id(payment.getId()).transactionCode(payment.getTransactionCode()).packageId(payment.getPackageId())
                .amount(payment.getAmount()).paymentMethod(payment.getPaymentMethod()).status(payment.getStatus())
                .vnpTransactionNo(payment.getGatewayReference()).createdAt(payment.getCreatedAt()).build();
    }
}
