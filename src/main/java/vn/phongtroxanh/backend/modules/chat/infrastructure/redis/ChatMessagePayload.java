package vn.phongtroxanh.backend.modules.chat.infrastructure.redis;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePayload implements Serializable {

    private UUID messageId;
    private UUID conversationId;
    private UUID senderId;
    private String senderName;
    private String content;
    private String attachmentUrl;
    private Instant createdAt;
}
