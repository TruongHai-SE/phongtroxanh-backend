package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.UserProfileResponse;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
    private Boolean isNewUser;
    private Boolean isOnboarded;
    private UserProfileResponse user;
}
