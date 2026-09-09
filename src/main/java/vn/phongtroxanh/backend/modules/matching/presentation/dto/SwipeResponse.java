package vn.phongtroxanh.backend.modules.matching.presentation.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwipeResponse {

    private boolean isMatch;
    private Integer matchedScore;
    private UUID matchId;
    private UUID conversationId;
    private int swipesLeft;
}
