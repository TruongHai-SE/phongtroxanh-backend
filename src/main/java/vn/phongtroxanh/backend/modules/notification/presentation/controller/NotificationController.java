package vn.phongtroxanh.backend.modules.notification.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.notification.presentation.dto.DeviceTokenRequest;
import vn.phongtroxanh.backend.modules.notification.presentation.dto.NotificationResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Module 9: Thông Báo Tức Thời", description = "Các API nhận thông báo cá nhân, đánh dấu đã đọc và đăng ký FCM Push Notification")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "API #73: Lấy danh sách thông báo cá nhân", description = "Lấy danh sách thông báo in-app cá nhân có phân trang (Match mới, tin nhắn, phòng phù hợp, duyệt CCCD...)")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        Page<NotificationResponse> result = notificationService.getUserNotifications(page, limit);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách thông báo thành công", result));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "API #74: Đánh dấu 1 thông báo đã đọc", description = "Cập nhật trạng thái đã đọc cho thông báo cụ thể")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable("id") UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu thông báo đã đọc", null));
    }

    @PutMapping("/read-all")
    @Operation(summary = "API #75: Đánh dấu tất cả thông báo đã đọc", description = "Đánh dấu tất cả thông báo chưa đọc của người dùng thành đã đọc")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu tất cả thông báo là đã đọc", null));
    }

    @PostMapping("/device-token")
    @Operation(summary = "API #76: Đăng ký FCM Device Token", description = "Đăng ký FCM Device Token để nhận Push Notification trên trình duyệt/mobile")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(@Valid @RequestBody DeviceTokenRequest request) {
        notificationService.registerDeviceToken(request);
        return ResponseEntity.ok(ApiResponse.ok("Đăng ký device token thành công", null));
    }
}
