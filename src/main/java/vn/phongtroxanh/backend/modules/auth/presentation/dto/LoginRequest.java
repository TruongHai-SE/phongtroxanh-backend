package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Tài khoản (Email hoặc Số điện thoại) không được để trống")
    private String login;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    private String deviceId;
}
