package vn.phongtroxanh.backend.modules.monetization.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.phongtroxanh.backend.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_type", length = 50, nullable = false)
    @Builder.Default
    private String itemType = "PACKAGE";

    @Column(name = "item_name", length = 150, nullable = false)
    @Builder.Default
    private String itemName = "PACKAGE";

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "idempotency_key", length = 100, unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "qr_expired_at")
    private Instant qrExpiredAt;

    @Transient
    private String packageId;

    @Transient
    private String transactionCode;

    @Transient
    private String vnpTransactionNo;

    @PrePersist
    @PreUpdate
    public void syncGatewayOrder() {
        if (gatewayOrderId == null && transactionCode != null) {
            this.gatewayOrderId = transactionCode;
        }
        if (packageId != null && (itemName == null || "PACKAGE".equals(itemName))) {
            this.itemName = packageId;
        }
    }

    public String getTransactionCode() {
        return gatewayOrderId != null ? gatewayOrderId : transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
        this.gatewayOrderId = transactionCode;
    }

    public String getPackageId() {
        return packageId != null ? packageId : itemName;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
        if (this.itemName == null || "PACKAGE".equals(this.itemName)) {
            this.itemName = packageId;
        }
    }
}
