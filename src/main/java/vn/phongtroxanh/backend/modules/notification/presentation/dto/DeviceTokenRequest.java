package vn.phongtroxanh.backend.modules.notification.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {

    @NotBlank(message = "FCM Device Token không được để trống")
    private String token;

    private String deviceType; // WEB, ANDROID, IOS
}
