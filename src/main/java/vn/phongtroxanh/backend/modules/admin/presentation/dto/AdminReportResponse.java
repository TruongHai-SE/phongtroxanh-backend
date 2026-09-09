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
public class AdminReportResponse {

    private UUID id;
    private UUID reporterId;
    private String targetType;
    private UUID targetId;
    private String reportType;
    private String detail;
    private List<String> evidenceImages;
    private String status;
    private String severity;
    private UUID handlerId;
    private Instant createdAt;
    private Instant updatedAt;
}
