package vn.phongtroxanh.backend.modules.matching.presentation.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchItemDTO {

    private UUID matchId;
    private int matchedScore;
    private UUID partnerId;
    private String partnerName;
    private String partnerAvatar;
    private String partnerSchool;
    private Integer partnerTrustScore;
    private UUID conversationId;
    private Instant matchedAt;
}
