package vn.phongtroxanh.backend.modules.chat.presentation.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.common.security.UserPrincipal;
import vn.phongtroxanh.backend.modules.chat.application.service.ChatService;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;

    @Getter
    @Setter
    public static class WsMessagePayload {
        @NotNull
        private UUID conversationId;
        @NotBlank
        @Size(max = 2000)
        private String content;
        private String attachmentUrl;
    }

    @MessageMapping("/chat.sendMessage")
    public void handleWsMessage(@Valid @Payload WsMessagePayload payload, Principal principal) {
        if (principal instanceof Authentication authentication && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {

            UUID senderId = userPrincipal.getId();
            log.debug("Received STOMP chat message from sender: {} in conversation: {}", senderId, payload.getConversationId());
            chatService.sendMessageInternal(payload.getConversationId(), senderId, payload.getContent(), payload.getAttachmentUrl());
        } else {
            throw new UnauthorizedException("AUTH_REQUIRED", "Bạn cần đăng nhập để gửi tin nhắn");
        }
    }
}
