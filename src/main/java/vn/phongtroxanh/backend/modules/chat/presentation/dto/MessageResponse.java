package vn.phongtroxanh.backend.modules.chat.presentation.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String attachmentUrl;
    private Boolean isRead;
    private Instant createdAt;
}
