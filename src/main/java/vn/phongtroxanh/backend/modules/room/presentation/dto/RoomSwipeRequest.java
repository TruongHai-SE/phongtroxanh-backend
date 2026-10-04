package vn.phongtroxanh.backend.modules.room.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSwipeRequest {

    @NotBlank(message = "Hành động quẹt không được để trống")
    @Pattern(regexp = "^(?i)(LIKE|PASS)$", message = "Hành động chỉ có thể là LIKE hoặc PASS")
    private String action;
}
