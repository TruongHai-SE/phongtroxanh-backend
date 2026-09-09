package vn.phongtroxanh.backend.modules.matching.presentation.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompatibilityDetailResponse {

    private UUID currentUserId;
    private UUID targetUserId;
    private int totalScore;
    private int budgetScore;
    private int locationScore;
    private int sleepScore;
    private int neatScore;
    private int guestScore;
    private int smokeScore;
    private int noiseScore;
    private int interestScore;
    private boolean hasDealbreaker;
    private List<String> highlights;
}
