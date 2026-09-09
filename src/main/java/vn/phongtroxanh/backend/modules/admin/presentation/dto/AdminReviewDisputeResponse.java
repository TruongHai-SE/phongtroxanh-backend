package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReviewDisputeResponse {

    private UUID id;
    private UUID reviewId;
    private UUID appellantId;
    private String reason;
    private List<String> evidenceImages;
    private String status;
    private String adminNotes;
    private UUID resolvedBy;
    private Instant createdAt;
    private Instant resolvedAt;
}
