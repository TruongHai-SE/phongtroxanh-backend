package vn.phongtroxanh.backend.modules.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import vn.phongtroxanh.backend.common.security.JwtTokenProvider;
import vn.phongtroxanh.backend.modules.chat.application.service.ChatService;
import vn.phongtroxanh.backend.modules.chat.infrastructure.config.WebSocketConfig;
import vn.phongtroxanh.backend.modules.chat.infrastructure.redis.RedisChatPublisher;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.room.infrastructure.repository.RoomRepository;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSecurityRegressionTest {
    static class TestChannelRegistration extends ChannelRegistration {
        org.springframework.messaging.support.ChannelInterceptor interceptor() { return getInterceptors().getFirst(); }
        boolean hasInterceptor() { return !getInterceptors().isEmpty(); }
    }
    @Mock JwtTokenProvider jwt;
    @Mock RedisTemplate<String, Object> redis;
    @Mock UserRepository users;
    @Mock ConversationRepository conversations;
    @Mock MessageRepository messages;
    @Mock RoomRepository rooms;
    @Mock RedisChatPublisher publisher;
    @Mock vn.phongtroxanh.backend.modules.notification.application.service.NotificationService notifications;
    @Mock vn.phongtroxanh.backend.common.security.AccessTokenAuthenticator accessTokens;
    @InjectMocks WebSocketConfig config;
    @InjectMocks ChatService chat;

    @Test void websocketConnectRequiresBearerToken() {
        TestChannelRegistration registration = new TestChannelRegistration();
        config.configureClientInboundChannel(registration);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThrows(RuntimeException.class, () -> registration.interceptor().preSend(message, null));
    }

    @Test void websocketRejectsAnonymousSubscribe() {
        TestChannelRegistration registration = new TestChannelRegistration();
        config.configureClientInboundChannel(registration);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/conversations." + UUID.randomUUID());
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThrows(RuntimeException.class, () -> registration.interceptor().preSend(message, null));
    }

    @Test void websocketRejectsClientBrokerSend() {
        TestChannelRegistration registration = new TestChannelRegistration();
        config.configureClientInboundChannel(registration);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/topic/conversations." + UUID.randomUUID());
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThrows(RuntimeException.class, () -> registration.interceptor().preSend(message, null));
    }

    @Test void chatRejectsBlankContentBeforeRepositoryRead() {
        assertThrows(RuntimeException.class, () -> chat.sendMessageInternal(UUID.randomUUID(), UUID.randomUUID(), "  ", null));
        verifyNoInteractions(conversations, messages, publisher);
    }

    @Test void messageAttachmentSurvivesHistoryAndRecipientIsNotified() {
        UUID id = UUID.randomUUID(), sender = UUID.randomUUID(), recipient = UUID.randomUUID();
        var conversation = vn.phongtroxanh.backend.modules.chat.domain.Conversation.builder()
                .participantOneId(sender).participantTwoId(recipient).build();
        conversation.setId(id);
        when(conversations.findById(id)).thenReturn(java.util.Optional.of(conversation));
        java.util.concurrent.atomic.AtomicReference<vn.phongtroxanh.backend.modules.chat.domain.Message> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(messages.save(any())).thenAnswer(invocation -> { saved.set(invocation.getArgument(0)); return saved.get(); });
        when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any())).thenAnswer(invocation -> new org.springframework.data.domain.PageImpl<>(java.util.List.of(saved.get())));
        var principal = vn.phongtroxanh.backend.common.security.UserPrincipal.builder().id(sender).role("TENANT").active(true).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            chat.sendMessageInternal(id, sender, "hello", "https://example.com/attachment.png");
            assertEquals("https://example.com/attachment.png", chat.getMessages(id, 0, 10).getContent().getFirst().getAttachmentUrl());
            verify(notifications).create(eq(recipient), anyString(), eq("hello"), eq(vn.phongtroxanh.backend.modules.notification.domain.NotificationType.MESSAGE), anyMap());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test void authenticatedNonMemberCannotSubscribe() {
        UUID id = UUID.randomUUID();
        var principal = vn.phongtroxanh.backend.common.security.UserPrincipal.builder().id(UUID.randomUUID()).role("TENANT").active(true).build();
        when(accessTokens.authenticate("token")).thenReturn(principal);
        when(conversations.findById(id)).thenReturn(java.util.Optional.of(vn.phongtroxanh.backend.modules.chat.domain.Conversation.builder()
                .participantOneId(UUID.randomUUID()).participantTwoId(UUID.randomUUID()).build()));
        TestChannelRegistration registration = new TestChannelRegistration();
        config.configureClientInboundChannel(registration);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/conversations." + id);
        headers.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities()));
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThrows(vn.phongtroxanh.backend.common.exception.ForbiddenException.class, () -> registration.interceptor().preSend(message, null));
    }

    @Test void revokedSessionStopsReceivingExistingSubscription() {
        var principal = vn.phongtroxanh.backend.common.security.UserPrincipal.builder().id(UUID.randomUUID()).role("TENANT").active(true).build();
        when(accessTokens.authenticate("token")).thenReturn(principal);
        TestChannelRegistration inbound = new TestChannelRegistration();
        config.configureClientInboundChannel(inbound);
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer token");
        connect.setSessionId("session");
        connect.setLeaveMutable(true);
        inbound.interceptor().preSend(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()), null);
        when(accessTokens.authenticate("token")).thenThrow(new vn.phongtroxanh.backend.common.exception.UnauthorizedException("REVOKED", "revoked"));
        TestChannelRegistration outbound = new TestChannelRegistration();
        config.configureClientOutboundChannel(outbound);
        assertTrue(outbound.hasInterceptor(), "Existing subscriptions must recheck token revocation before delivery");
        var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headers.setSessionId("session");
        headers.setDestination("/topic/conversations." + UUID.randomUUID());
        assertNull(outbound.interceptor().preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
    }

    @Test void conversationMemberCanConnectSubscribeAndReceive() {
        UUID id = UUID.randomUUID(), userId = UUID.randomUUID();
        var principal = vn.phongtroxanh.backend.common.security.UserPrincipal.builder().id(userId).role("TENANT").active(true).build();
        when(accessTokens.authenticate("token")).thenReturn(principal);
        when(conversations.findById(id)).thenReturn(java.util.Optional.of(vn.phongtroxanh.backend.modules.chat.domain.Conversation.builder()
                .participantOneId(userId).participantTwoId(UUID.randomUUID()).build()));
        TestChannelRegistration inbound = new TestChannelRegistration();
        config.configureClientInboundChannel(inbound);
        var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer token");
        connect.setSessionId("session");
        connect.setLeaveMutable(true);
        assertNotNull(inbound.interceptor().preSend(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()), null));
        var subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/conversations." + id);
        subscribe.setUser(connect.getUser());
        assertNotNull(inbound.interceptor().preSend(MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders()), null));
        TestChannelRegistration outbound = new TestChannelRegistration();
        config.configureClientOutboundChannel(outbound);
        var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headers.setSessionId("session");
        headers.setDestination("/topic/conversations." + id);
        var delivery = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertSame(delivery, outbound.interceptor().preSend(delivery, null));
    }
}
