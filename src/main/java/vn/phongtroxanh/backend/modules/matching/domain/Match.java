package vn.phongtroxanh.backend.modules.matching.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_a_id", "user_b_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    @Column(name = "compatibility_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal compatibilityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private MatchStatus status = MatchStatus.MATCHED;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
