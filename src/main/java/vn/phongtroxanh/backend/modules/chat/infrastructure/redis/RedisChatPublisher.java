package vn.phongtroxanh.backend.modules.chat.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatPublisher {

    public static final String CHAT_TOPIC = "chat.message.topic";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ChatMessagePayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(CHAT_TOPIC, json);
            log.debug("Published chat message to Redis topic: {}", payload.getMessageId());
        } catch (Exception e) {
            log.error("Failed to publish chat message to Redis: {}", e.getMessage());
        }
    }
}
