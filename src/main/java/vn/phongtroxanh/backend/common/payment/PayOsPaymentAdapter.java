package vn.phongtroxanh.backend.common.payment;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.model.webhooks.WebhookData;
import vn.phongtroxanh.backend.common.exception.AppException;
import vn.phongtroxanh.backend.common.exception.BadRequestException;

import java.time.Instant;
import java.util.Map;

@Service
public class PayOsPaymentAdapter {
    private final PayOS client;

    public PayOsPaymentAdapter(@Value("${app.payment.payos.client-id:}") String clientId,
                               @Value("${app.payment.payos.api-key:}") String apiKey,
                               @Value("${app.payment.payos.checksum-key:}") String checksumKey) {
        client = clientId.isBlank() || apiKey.isBlank() || checksumKey.isBlank() ? null : new PayOS(
                ClientOptions.builder().clientId(clientId).apiKey(apiKey).checksumKey(checksumKey)
                        .timeoutMs(10000).maxRetries(0).build());
    }

    public void requireConfigured() {
        if (client == null) throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYOS_NOT_CONFIGURED", "Chưa cấu hình PAYOS_CLIENT_ID, PAYOS_API_KEY và PAYOS_CHECKSUM_KEY");
    }

    public CreatePaymentLinkResponse create(long orderCode, long amount, String returnUrl, String cancelUrl, Instant expiresAt) {
        requireConfigured();
        try {
            return client.paymentRequests().create(CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode).amount(amount).description("PTX" + orderCode % 1000000)
                    .returnUrl(returnUrl).cancelUrl(cancelUrl).expiredAt(expiresAt.getEpochSecond()).build());
        } catch (RuntimeException ex) {
            // A timed-out create may already have succeeded at the gateway. Recover the same order.
            try {
                PaymentLink existing = client.paymentRequests().get(orderCode);
                if (existing.getAmount() != amount) throw unavailable();
                return CreatePaymentLinkResponse.builder().orderCode(orderCode).amount(amount)
                        .bin("").accountNumber("").accountName("").description("PTX").qrCode("")
                        .currency("VND").paymentLinkId(existing.getId()).status(existing.getStatus())
                        .checkoutUrl("https://pay.payos.vn/web/" + existing.getId()).build();
            } catch (RuntimeException recoveryFailure) { throw unavailable(); }
        }
    }

    public WebhookData verify(Map<String, Object> payload) {
        requireConfigured();
        try { return client.webhooks().verify(payload); }
        catch (RuntimeException ex) {
            throw new BadRequestException("INVALID_PAYMENT_SIGNATURE", "Thông báo thanh toán không có chữ ký hợp lệ");
        }
    }

    public PaymentLink get(long orderCode) {
        requireConfigured();
        try { return client.paymentRequests().get(orderCode); }
        catch (RuntimeException ex) { throw unavailable(); }
    }

    private AppException unavailable() {
        return new AppException(HttpStatus.BAD_GATEWAY, "PAYOS_UNAVAILABLE", "Không thể kết nối payOS. Vui lòng thử lại với cùng Idempotency-Key");
    }

    @PreDestroy public void close() { if (client != null) client.close(); }
}
