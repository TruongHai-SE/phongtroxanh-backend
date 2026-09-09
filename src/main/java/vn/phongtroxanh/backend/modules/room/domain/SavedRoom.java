package vn.phongtroxanh.backend.modules.room.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_rooms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(SavedRoomId.class)
public class SavedRoom implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
