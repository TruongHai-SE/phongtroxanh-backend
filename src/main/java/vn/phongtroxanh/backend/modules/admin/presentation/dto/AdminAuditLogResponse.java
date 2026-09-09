package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogResponse {

    private UUID id;
    private UUID actorId;
    private String action;
    private String targetEntity;
    private UUID targetId;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
}
