package vn.phongtroxanh.backend.modules.review.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemFeedbackResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userRole;
    private int rating;
    private String category;
    private String comment;
    private Instant createdAt;
}
