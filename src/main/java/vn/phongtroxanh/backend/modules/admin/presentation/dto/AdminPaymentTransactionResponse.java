package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentMethod;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentTransactionResponse {

    private UUID id;
    private UUID userId;
    private String itemType;
    private String itemName;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String idempotencyKey;
    private String gatewayOrderId;
    private String qrCodeUrl;
    private Instant qrExpiredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
