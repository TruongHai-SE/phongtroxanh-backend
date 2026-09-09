package vn.phongtroxanh.backend.modules.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trust_score_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "final_score", nullable = false)
    private Integer finalScore;

    @Column(name = "reason", length = 255, nullable = false)
    private String reason;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
