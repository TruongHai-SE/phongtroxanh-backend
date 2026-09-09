package vn.phongtroxanh.backend.modules.review.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDisputeStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private UUID id;
    private UUID rentalId;
    private UUID reviewerId;
    private String reviewerName;
    private String reviewerAvatar;
    private UUID revieweeId;
    private UUID roomId;

    private Integer rating;
    private Integer cleanlinessRating;
    private Integer accuracyRating;
    private Integer communicationRating;
    private String comment;
    private List<String> tags;
    private List<String> images;

    private ReviewDisputeStatus disputeStatus;
    private String disputeReason;
    private String replyComment;
    private Instant repliedAt;
    private List<String> evidenceImages;
    private Instant createdAt;
}
