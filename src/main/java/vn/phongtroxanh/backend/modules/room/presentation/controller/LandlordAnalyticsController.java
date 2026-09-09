package vn.phongtroxanh.backend.modules.room.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.room.application.service.RoomService;
import vn.phongtroxanh.backend.modules.room.presentation.dto.LandlordAnalyticsResponse;

@RestController
@RequestMapping({"/api/v1/landlord/analytics", "/api/v1/rooms/landlord/analytics"})
@RequiredArgsConstructor
@Tag(name = "Module 3: Landlord Analytics", description = "Các API thống kê hiệu suất bài đăng và tỷ lệ lấp đầy phòng")
public class LandlordAnalyticsController {

    private final RoomService roomService;

    @GetMapping({"", "/overview"})
    @PreAuthorize("hasRole('LANDLORD') or hasRole('ADMIN')")
    @Operation(summary = "API #39: Thống kê tổng quan chủ trọ", description = "Lấy báo cáo tổng quan: Số phòng đang cho thuê, số phòng trống, tổng lượt xem, tỷ lệ lấp đầy và biểu đồ lượt xem 7 ngày")
    public ResponseEntity<ApiResponse<LandlordAnalyticsResponse>> getOverview() {
        LandlordAnalyticsResponse response = roomService.getLandlordAnalytics();
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu phân tích chủ trọ thành công", response));
    }
}
