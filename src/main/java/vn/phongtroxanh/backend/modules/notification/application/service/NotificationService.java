package vn.phongtroxanh.backend.modules.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.ResourceNotFoundException;
import vn.phongtroxanh.backend.common.security.SecurityUtils;
import vn.phongtroxanh.backend.modules.notification.domain.Notification;
import vn.phongtroxanh.backend.modules.notification.infrastructure.repository.NotificationRepository;
import vn.phongtroxanh.backend.modules.notification.presentation.dto.DeviceTokenRequest;
import vn.phongtroxanh.backend.modules.notification.presentation.dto.NotificationResponse;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import vn.phongtroxanh.backend.modules.notification.domain.NotificationType;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void create(UUID userId, String title, String body, NotificationType type, Map<String, Object> data) {
        notificationRepository.save(Notification.builder().userId(userId).title(title).body(body).type(type).data(data).build());
    }

    @Transactional
    public void broadcast(String title, String body, NotificationType type, Map<String, Object> data) {
        List<User> users = userRepository.findAll();
        List<Notification> batch = users.stream()
                .filter(u -> u.getStatus() != UserStatus.DELETED && u.getStatus() != UserStatus.LOCKED)
                .map(u -> Notification.builder()
                        .userId(u.getId())
                        .title(title)
                        .body(body)
                        .type(type)
                        .data(data)
                        .isRead(false)
                        .build())
                .toList();
        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
            log.info("Broadcasted notification '{}' to {} users", title, batch.size());
        }
    }

    public Page<NotificationResponse> getUserNotifications(int page, int limit) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, limit), 100));

        Page<Notification> notifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);
        return notifs.map(this::mapToResponse);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo"));

        if (!notif.getUserId().equals(currentUserId)) {
            throw new ForbiddenException("BOLA_FORBIDDEN", "Bạn không có quyền thao tác trên thông báo này");
        }

        notif.setIsRead(true);
        notificationRepository.save(notif);
    }

    @Transactional
    public void markAllAsRead() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        notificationRepository.markAllAsRead(currentUserId);
        log.info("Marked all notifications as read for user {}", currentUserId);
    }

    public void registerDeviceToken(DeviceTokenRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        String key = "fcm:token:" + currentUserId;
        redisTemplate.opsForValue().set(key, request.getToken(), 30, TimeUnit.DAYS);
        log.info("Registered FCM device token for user {}", currentUserId);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .title(n.getTitle())
                .body(n.getBody())
                .type(n.getType())
                .data(n.getData())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
