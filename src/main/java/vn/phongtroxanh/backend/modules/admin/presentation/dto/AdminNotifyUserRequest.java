package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotifyUserRequest {

    private String title;

    @NotBlank(message = "Nội dung thông báo không được để trống")
    private String message;
}
