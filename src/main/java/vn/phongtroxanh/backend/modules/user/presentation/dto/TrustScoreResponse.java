package vn.phongtroxanh.backend.modules.user.presentation.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreResponse {

    private Integer currentScore;
    private Boolean isVerified;
    private List<String> badges;

    private Integer kycScore;
    private Integer reviewScore;
    private Integer rentalDurationScore;
    private Integer responseRateScore;

    private List<TrustScoreLogItem> history;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrustScoreLogItem {
        private UUID id;
        private Integer delta;
        private Integer finalScore;
        private String reason;
        private Instant createdAt;
    }
}
