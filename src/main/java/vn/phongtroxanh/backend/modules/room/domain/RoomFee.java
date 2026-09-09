package vn.phongtroxanh.backend.modules.room.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "room_fees")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomFee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    public UUID getRoomId() {
        return room != null ? room.getId() : null;
    }

    @Column(name = "fee_label", length = 100, nullable = false)
    private String feeLabel;

    @Column(name = "fee_value", length = 100, nullable = false)
    private String feeValue;
}
