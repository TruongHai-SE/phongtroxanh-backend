package vn.phongtroxanh.backend.modules.review.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "review_disputes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "appellant_id", nullable = false)
    private UUID appellantId;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "evidence_images", columnDefinition = "text[]")
    private List<String> evidenceImages;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "PENDING_REVIEW";

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
