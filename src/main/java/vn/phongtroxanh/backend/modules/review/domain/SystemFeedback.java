package vn.phongtroxanh.backend.modules.review.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_feedbacks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "category", length = 50, nullable = false)
    @Builder.Default
    private String category = "app_experience";

    @Column(name = "comment", columnDefinition = "TEXT", nullable = false)
    private String comment;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
