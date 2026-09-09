package vn.phongtroxanh.backend.modules.auth.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.auth.presentation.dto.LandingStatsResponse;

@RestController
@RequestMapping("/api/v1/misc")
@RequiredArgsConstructor
@Tag(name = "Module 1: Landing Stats", description = "Các API tiện ích và số liệu thống kê công khai")
public class MiscController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/landing-stats")
    @Operation(summary = "API #12: Thống kê Landing Page", description = "Lấy số liệu thống kê hiển thị trang chủ: Số phòng có sẵn, Số cặp đã ghép đôi, Tỉ lệ đánh giá tích cực, Người dùng active")
    public ResponseEntity<ApiResponse<LandingStatsResponse>> getLandingStats() {
        Long availableRooms = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rooms WHERE status = 'AVAILABLE'", Long.class);
        Long matchedPairs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE status = 'MATCHED'", Long.class);
        Long activeUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'", Long.class);
        Double positiveReviewRate = jdbcTemplate.queryForObject(
                "SELECT COALESCE(ROUND((COUNT(*) FILTER (WHERE rating >= 4)::numeric / NULLIF(COUNT(*), 0)::numeric) * 100, 1), 0.0) FROM reviews WHERE status = 'ACTIVE'",
                Double.class);

        LandingStatsResponse stats = LandingStatsResponse.builder()
                .availableRoomsCount(availableRooms != null ? availableRooms : 0L)
                .matchedPairsCount(matchedPairs != null ? matchedPairs : 0L)
                .activeUsersCount(activeUsers != null ? activeUsers : 0L)
                .positiveReviewRate(positiveReviewRate != null ? positiveReviewRate : 0.0)
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Lấy số liệu thống kê thành công", stats));
    }
}
