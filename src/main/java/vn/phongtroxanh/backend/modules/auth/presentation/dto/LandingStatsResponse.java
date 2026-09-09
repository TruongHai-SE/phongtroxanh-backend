package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingStatsResponse {
    private long availableRoomsCount;
    private long matchedPairsCount;
    private double positiveReviewRate;
    private long activeUsersCount;
}
