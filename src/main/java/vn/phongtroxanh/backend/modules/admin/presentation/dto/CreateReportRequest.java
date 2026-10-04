package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import vn.phongtroxanh.backend.modules.admin.domain.ReportSeverity;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotBlank(message = "targetType không được để trống (ROOM, USER, REVIEW)")
    private String targetType;

    @NotNull(message = "targetId không được để trống")
    private UUID targetId;

    @NotBlank(message = "reportType không được để trống")
    @Size(max = 100, message = "reportType tối đa 100 ký tự")
    private String reportType;

    @NotBlank(message = "detail không được để trống")
    @Size(max = 5000, message = "detail tối đa 5000 ký tự")
    private String detail;

    private List<String> evidenceImages;

    private ReportSeverity severity;
}
