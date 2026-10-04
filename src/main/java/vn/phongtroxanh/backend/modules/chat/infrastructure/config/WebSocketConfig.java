package vn.phongtroxanh.backend.modules.chat.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import vn.phongtroxanh.backend.common.security.UserPrincipal;
import vn.phongtroxanh.backend.common.security.AccessTokenAuthenticator;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.modules.chat.infrastructure.repository.ConversationRepository;
import vn.phongtroxanh.backend.modules.chat.infrastructure.redis.RedisChatPublisher;
import vn.phongtroxanh.backend.modules.chat.infrastructure.redis.RedisChatSubscriber;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AccessTokenAuthenticator accessTokenAuthenticator;
    private final ConversationRepository conversationRepository;
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new UnauthorizedException("AUTH_REQUIRED", "WebSocket yêu cầu đăng nhập");
                    }
                    String token = authHeader.substring(7);
                    UserPrincipal principal = accessTokenAuthenticator.authenticate(token);
                    accessor.setUser(new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities()));
                    if (accessor.getSessionId() != null) sessionTokens.put(accessor.getSessionId(), token);
                } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    if (accessor.getSessionId() != null) sessionTokens.remove(accessor.getSessionId());
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || StompCommand.SEND.equals(accessor.getCommand())) {
                    if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) ||
                            !(auth.getCredentials() instanceof String token)) {
                        throw new UnauthorizedException("AUTH_REQUIRED", "WebSocket yêu cầu đăng nhập");
                    }
                    UserPrincipal principal = accessTokenAuthenticator.authenticate(token);
                    String destination = accessor.getDestination();
                    if (StompCommand.SEND.equals(accessor.getCommand())) {
                        if (!"/app/chat.sendMessage".equals(destination)) {
                            throw new ForbiddenException("INVALID_DESTINATION", "Không được gửi trực tiếp tới broker");
                        }
                    } else {
                        assertParticipant(destination, principal);
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
                String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
                String token = sessionId == null ? null : sessionTokens.get(sessionId);
                if (token == null) return null;
                try {
                    UserPrincipal principal = accessTokenAuthenticator.authenticate(token);
                    assertParticipant(SimpMessageHeaderAccessor.getDestination(message.getHeaders()), principal);
                    return message;
                } catch (RuntimeException ex) {
                    log.debug("Dropped WebSocket delivery for inactive or unauthorized session {}", sessionId);
                    return null;
                }
            }
        });
    }

    @org.springframework.context.event.EventListener
    public void onDisconnect(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        sessionTokens.remove(event.getSessionId());
    }

    private void assertParticipant(String destination, UserPrincipal principal) {
        String prefix = "/topic/conversations.";
        if (destination == null || !destination.startsWith(prefix)) {
            throw new ForbiddenException("INVALID_DESTINATION", "Topic không được phép");
        }
        UUID conversationId;
        try {
            conversationId = UUID.fromString(destination.substring(prefix.length()));
            if (!destination.equals(prefix + conversationId)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException("INVALID_DESTINATION", "Topic không hợp lệ");
        }
        var conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ForbiddenException("BOLA_FORBIDDEN", "Bạn không phải thành viên hội thoại"));
        if (!principal.getId().equals(conversation.getParticipantOneId()) &&
                !principal.getId().equals(conversation.getParticipantTwoId())) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không phải thành viên hội thoại");
        }
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatSubscriber chatSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatSubscriber, new ChannelTopic(RedisChatPublisher.CHAT_TOPIC));
        return container;
    }
}
