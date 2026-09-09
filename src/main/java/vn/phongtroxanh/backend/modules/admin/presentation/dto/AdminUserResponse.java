package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private UUID id;
    private String email;
    private String phoneNumber;
    private String fullName;
    private String avatarUrl;
    private UserRole role;
    private UserStatus status;
    private Integer trustScore;
    private Boolean isVerified;
    private Instant createdAt;
}
