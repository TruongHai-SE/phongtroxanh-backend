package vn.phongtroxanh.backend.modules.chat.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.chat.application.service.ChatService;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.ConversationResponse;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.CreateConversationRequest;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.MessageResponse;
import vn.phongtroxanh.backend.modules.chat.presentation.dto.SendMessageRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Module 8: Real-time Chat & WebSocket", description = "Các API danh sách hội thoại, lịch sử tin nhắn và gửi tin nhắn (STOMP WebSocket + Redis Pub/Sub)")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    @Operation(summary = "API #68: Danh sách các cuộc trò chuyện", description = "Lấy toàn bộ hội thoại của người dùng (type=ROOM hoặc type=ROOMMATE) kèm số tin chưa đọc và tin nhắn mới nhất")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hội thoại thành công", chatService.getConversations()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "API #69: Lịch sử tin nhắn theo hội thoại", description = "Lấy lịch sử tin nhắn trong phòng chat có phân trang")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessages(
            @PathVariable("conversationId") UUID conversationId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "30") int limit) {

        Page<MessageResponse> messages = chatService.getMessages(conversationId, page, limit);
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử tin nhắn thành công", messages));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "API #70: Gửi tin nhắn (REST Fallback / Mobile)", description = "Gửi tin nhắn vào cuộc trò chuyện qua HTTP REST (tự động phát sóng qua Redis Pub/Sub và STOMP WebSocket)")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable("conversationId") UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        MessageResponse response = chatService.sendMessage(conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gửi tin nhắn thành công", response));
    }

    @PutMapping("/conversations/{conversationId}/read")
    @Operation(summary = "API #71: Đánh dấu tin nhắn đã đọc", description = "Đánh dấu toàn bộ tin nhắn chưa đọc trong hội thoại thành đã đọc")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable("conversationId") UUID conversationId) {
        chatService.markConversationAsRead(conversationId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đã đọc", null));
    }

    @PostMapping("/conversations")
    @Operation(summary = "API #72: Bắt đầu cuộc trò chuyện mới", description = "Tạo phòng chat mới giữa 2 người hoặc trả về phòng chat sẵn có nếu đã tồn tại")
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {

        ConversationResponse response = chatService.getOrCreateConversation(request);
        return ResponseEntity.ok(ApiResponse.ok("Khởi tạo cuộc trò chuyện thành công", response));
    }
}
