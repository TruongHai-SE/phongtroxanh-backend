package vn.phongtroxanh.backend.modules.chat.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.chat.domain.ConversationType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private UUID id;
    private UUID partnerId;
    private String partnerName;
    private String partnerAvatar;
    private Integer partnerTrustScore;
    private ConversationType type;
    private UUID roomId;
    private String roomTitle;
    private String lastMessageContent;
    private Instant lastMessageAt;
    private long unreadCount;
}
