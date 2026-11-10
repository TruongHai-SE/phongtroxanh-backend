package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "ID Token Google không được để trống")
    private String idToken;

    @Builder.Default
    private UserRole role = UserRole.TENANT;

    @Builder.Default
    private String trafficSource = "direct";

    public GoogleLoginRequest(String idToken, UserRole role) {
        this.idToken = idToken;
        this.role = role;
        this.trafficSource = "direct";
    }
}
