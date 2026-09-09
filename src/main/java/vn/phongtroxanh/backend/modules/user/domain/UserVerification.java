package vn.phongtroxanh.backend.modules.user.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.phongtroxanh.backend.common.util.AesEncryptionUtil;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_verifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = AesEncryptionUtil.AesAttributeConverter.class)
    @Column(name = "id_card_number", nullable = false)
    private String idCardNumber;

    @Column(name = "id_card_front_url", length = 500, nullable = false)
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", length = 500, nullable = false)
    private String idCardBackUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
