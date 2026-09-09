package vn.phongtroxanh.backend.modules.chat.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.chat.domain.ConversationType;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    @NotNull(message = "partnerId không được để trống")
    private UUID partnerId;

    private UUID roomId;

    @NotNull(message = "Loại hội thoại không được để trống")
    private ConversationType type;
}
