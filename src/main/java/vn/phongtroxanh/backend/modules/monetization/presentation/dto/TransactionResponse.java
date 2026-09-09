package vn.phongtroxanh.backend.modules.monetization.presentation.dto;

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
public class TransactionResponse {

    private UUID id;
    private String transactionCode;
    private String packageId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String vnpTransactionNo;
    private Instant createdAt;
}
