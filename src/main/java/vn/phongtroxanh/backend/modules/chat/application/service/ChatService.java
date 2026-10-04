package vn.phongtroxanh.backend.modules.chat.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.BadRequestException;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.chat.domain.Conversation;
import vn.phongtroxanh.backend.modules.chat.domain.ConversationType;
import vn.phongtroxanh.backend.modules.chat.domain.Message;
import vn.phongtroxanh.backend.modules.chat.infrastructure.redis.ChatMessagePayload;
import vn.phongtroxanh.backend.modules.chat.infrastructure.redis.RedisChatPublisher;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.ConversationRepository;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.MessageRepository;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.ConversationResponse;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.CreateConversationRequest;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.MessageResponse;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.SendMessageRequest;
import vn.phongtroxanh.backend.modules.room.domain.Room;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RedisChatPublisher redisChatPublisher;
    private final NotificationService notificationService;

    public List<ConversationResponse> getConversations() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<Conversation> conversations = conversationRepository.findByUser(currentUserId);
        List<ConversationResponse> result = new ArrayList<>();

        for (Conversation c : conversations) {
            UUID partnerId = c.getParticipantOneId().equals(currentUserId) ? c.getParticipantTwoId() : c.getParticipantOneId();
            User partner = userRepository.findById(partnerId).orElse(null);

            Message lastMessage = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
            long unread = messageRepository.countUnreadMessages(c.getId(), currentUserId);

            String roomTitle = null;
            if (c.getRoomId() != null) {
                roomTitle = roomRepository.findById(c.getRoomId()).map(Room::getTitle).orElse(null);
            }

            result.add(ConversationResponse.builder()
                    .id(c.getId())
                    .partnerId(partnerId)
                    .partnerName(partner != null ? partner.getFullName() : "Người dùng")
                    .partnerAvatar(partner != null ? partner.getAvatarUrl() : null)
                    .partnerTrustScore(partner != null ? partner.getTrustScore() : 50)
                    .type(c.getType())
                    .roomId(c.getRoomId())
                    .roomTitle(roomTitle)
                    .lastMessageContent(lastMessage != null ? lastMessage.getContent() : null)
                    .lastMessageAt(c.getLastMessageAt())
                    .unreadCount(unread)
                    .build());
        }

        return result;
    }

    public Page<MessageResponse> getMessages(UUID conversationId, int page, int limit) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Không tìm thấy cuộc trò chuyện"));

        assertParticipant(c, currentUserId);

        int safeLimit = Math.min(Math.max(1, limit), 100);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeLimit);

        Page<Message> msgPage = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        return msgPage.map(m -> {
            User sender = userRepository.findById(m.getSenderId()).orElse(null);
            return MessageResponse.builder()
                    .id(m.getId())
                    .conversationId(m.getConversationId())
                    .senderId(m.getSenderId())
                    .senderName(sender != null ? sender.getFullName() : "Người gửi")
                    .senderAvatar(sender != null ? sender.getAvatarUrl() : null)
                    .content(m.getContent())
                    .attachmentUrl(m.getAttachmentUrl())
                    .isRead(m.getIsRead())
                    .createdAt(m.getCreatedAt())
                    .build();
        });
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, SendMessageRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return sendMessageInternal(conversationId, currentUserId, request.getContent(), request.getAttachmentUrl());
    }

    @Transactional
    public MessageResponse sendMessageInternal(UUID conversationId, UUID senderId, String content, String attachmentUrl) {
        if (conversationId == null || senderId == null || content == null || content.isBlank() || content.length() > 2000) {
            throw new BadRequestException("INVALID_MESSAGE", "Tin nhắn cần hội thoại và nội dung từ 1 đến 2000 ký tự");
        }
        if (attachmentUrl != null && !attachmentUrl.isBlank()) {
            try {
                var uri = java.net.URI.create(attachmentUrl);
                if (attachmentUrl.length() > 2000 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("INVALID_ATTACHMENT", "Đường dẫn tệp đính kèm phải là HTTPS hợp lệ");
            }
        }
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Không tìm thấy cuộc trò chuyện"));

        if (!senderId.equals(c.getParticipantOneId()) && !senderId.equals(c.getParticipantTwoId())) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không phải thành viên của cuộc trò chuyện này");
        }

        Message msg = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content)
                .attachmentUrl(attachmentUrl)
                .isRead(false)
                .build();

        msg = messageRepository.save(msg);

        c.setLastMessageText(content);
        c.setLastMessageAt(Instant.now());
        conversationRepository.save(c);

        User sender = userRepository.findById(senderId).orElse(null);

        UUID recipientId = senderId.equals(c.getParticipantOneId()) ? c.getParticipantTwoId() : c.getParticipantOneId();
        notificationService.create(recipientId, "Tin nhắn mới", content, NotificationType.MESSAGE,
                java.util.Map.of("conversationId", conversationId.toString(), "senderId", senderId.toString()));

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(msg.getId())
                .conversationId(conversationId)
                .senderId(senderId)
                .senderName(sender != null ? sender.getFullName() : "Người gửi")
                .content(content)
                .attachmentUrl(attachmentUrl)
                .createdAt(msg.getCreatedAt())
                .build();

        redisChatPublisher.publish(payload);

        return MessageResponse.builder()
                .id(msg.getId())
                .conversationId(conversationId)
                .senderId(senderId)
                .senderName(sender != null ? sender.getFullName() : "Người gửi")
                .senderAvatar(sender != null ? sender.getAvatarUrl() : null)
                .content(content)
                .attachmentUrl(attachmentUrl)
                .isRead(false)
                .createdAt(msg.getCreatedAt())
                .build();
    }

    @Transactional
    public void markConversationAsRead(UUID conversationId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Không tìm thấy cuộc trò chuyện"));

        assertParticipant(c, currentUserId);
        messageRepository.markAllRead(conversationId, currentUserId);
    }

    @Transactional
    public ConversationResponse getOrCreateConversation(CreateConversationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId.equals(request.getPartnerId())) {
            throw new BadRequestException("SELF_CHAT_NOT_ALLOWED", "Bạn không thể tự chat với chính mình");
        }

        Conversation c = conversationRepository.findBetweenUsers(currentUserId, request.getPartnerId(), request.getRoomId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .participantOneId(currentUserId)
                        .participantTwoId(request.getPartnerId())
                        .roomId(request.getRoomId())
                        .type(request.getType() != null ? request.getType() : ConversationType.ROOMMATE)
                        .lastMessageAt(Instant.now())
                        .build()));

        User partner = userRepository.findById(request.getPartnerId()).orElse(null);
        String roomTitle = null;
        if (c.getRoomId() != null) {
            roomTitle = roomRepository.findById(c.getRoomId()).map(Room::getTitle).orElse(null);
        }

        return ConversationResponse.builder()
                .id(c.getId())
                .partnerId(request.getPartnerId())
                .partnerName(partner != null ? partner.getFullName() : "Người dùng")
                .partnerAvatar(partner != null ? partner.getAvatarUrl() : null)
                .partnerTrustScore(partner != null ? partner.getTrustScore() : 50)
                .type(c.getType())
                .roomId(c.getRoomId())
                .roomTitle(roomTitle)
                .lastMessageAt(c.getLastMessageAt())
                .unreadCount(0)
                .build();
    }

    private void assertParticipant(Conversation c, UUID userId) {
        if (!c.getParticipantOneId().equals(userId) && !c.getParticipantTwoId().equals(userId) && !SecurityUtils.hasRole("ROLE_ADMIN")) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không phải thành viên của cuộc trò chuyện này");
        }
    }
}
