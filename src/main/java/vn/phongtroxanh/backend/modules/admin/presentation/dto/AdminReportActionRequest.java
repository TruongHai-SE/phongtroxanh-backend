package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportActionRequest {

    @NotBlank(message = "Hành động xử lý không được để trống")
    private String action; // resolve, warn, remove_content, ban, dismiss

    private String note;
}
