package vn.phongtroxanh.backend.modules.admin.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.admin.application.service.AdminService;
import vn.phongtroxanh.backend.modules.admin.presentation.dto.AdminReportResponse;
import vn.phongtroxanh.backend.modules.admin.presentation.dto.CreateReportRequest;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Community Reports", description = "Các API gửi báo cáo vi phạm cộng đồng (phòng trọ ảo, lừa đảo đặt cọc, quấy rối)")
public class ReportController {

    private final AdminService adminService;

    @PostMapping
    @Operation(summary = "Gửi báo cáo vi phạm", description = "Người dùng báo cáo phòng trọ, chủ trọ hoặc đánh giá vi phạm quy chuẩn cộng đồng")
    public ResponseEntity<ApiResponse<AdminReportResponse>> submitReport(@Valid @RequestBody CreateReportRequest request) {
        AdminReportResponse response = adminService.createReport(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gửi báo cáo vi phạm thành công", response));
    }
}
