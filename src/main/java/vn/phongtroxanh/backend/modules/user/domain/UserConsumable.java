package vn.phongtroxanh.backend.modules.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_consumables")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsumable implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "swipes_left")
    @Builder.Default
    private Integer swipesLeft = 15;

    @Column(name = "boosts_left")
    @Builder.Default
    private Integer boostsLeft = 0;

    @Column(name = "super_matches_left")
    @Builder.Default
    private Integer superMatchesLeft = 0;

    @Column(name = "last_swipe_reset_at")
    @Builder.Default
    private LocalDate lastSwipeResetAt = LocalDate.now();

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;
}
