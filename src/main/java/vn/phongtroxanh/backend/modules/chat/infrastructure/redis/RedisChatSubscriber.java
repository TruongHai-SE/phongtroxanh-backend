package vn.phongtroxanh.backend.modules.chat.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            ChatMessagePayload payload = objectMapper.readValue(json, ChatMessagePayload.class);

            // Forward to WebSocket topic for that specific conversation
            String destination = "/topic/conversations." + payload.getConversationId();
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Broadcast message {} to destination {}", payload.getMessageId(), destination);
        } catch (Exception e) {
            log.error("Failed to process Redis chat message: {}", e.getMessage());
        }
    }
}
