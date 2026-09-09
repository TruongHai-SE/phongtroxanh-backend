package vn.phongtroxanh.backend.modules.matching.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "swipes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"swiper_id", "target_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Swipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "swiper_id", nullable = false)
    private UUID swiperId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Convert(converter = SwipeActionConverter.class)
    @Column(name = "direction", nullable = false)
    private SwipeAction action;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
