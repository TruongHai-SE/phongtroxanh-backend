package vn.phongtroxanh.backend.modules.room.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_swipes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_room_swipe", columnNames = {"user_id", "room_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomSwipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "action", nullable = false, length = 20)
    private String action; // LIKE, PASS

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
