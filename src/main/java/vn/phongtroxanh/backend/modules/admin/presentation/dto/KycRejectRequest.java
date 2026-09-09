package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRejectRequest {

    @NotBlank(message = "Lý do từ chối hồ sơ không được để trống")
    private String reason;
}
