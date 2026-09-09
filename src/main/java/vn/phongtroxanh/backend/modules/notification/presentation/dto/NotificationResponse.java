package vn.phongtroxanh.backend.modules.notification.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private String title;
    private String body;
    private NotificationType type;
    private Map<String, Object> data;
    private Boolean isRead;
    private Instant createdAt;
}
