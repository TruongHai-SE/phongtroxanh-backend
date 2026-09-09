package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {

    @NotNull(message = "Trạng thái người dùng không được để trống")
    private UserStatus status;
}
