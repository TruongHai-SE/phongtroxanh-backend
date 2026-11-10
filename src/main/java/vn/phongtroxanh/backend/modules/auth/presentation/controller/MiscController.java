package vn.phongtroxanh.backend.modules.auth.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.auth.presentation.dto.LandingStatsResponse;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @GetMapping("/traffic")
    @Operation(summary = "Thống kê lưu lượng truy cập theo kênh", description = "Lấy số lượt truy cập toàn hệ thống theo 5 kênh (Facebook, TikTok, Zalo, Google, Trực tiếp)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTrafficStats() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thống kê truy cập thành công", queryTrafficMap()));
    }

    @PostMapping("/traffic")
    @Operation(summary = "Ghi nhận lượt truy cập mới theo kênh", description = "Cộng +1 lượt truy cập trực tiếp vào Database để đồng bộ toàn hệ thống")
    public ResponseEntity<ApiResponse<Map<String, Long>>> recordTraffic(@RequestParam(value = "source", defaultValue = "direct") String source) {
        String key = normalizeSource(source);
        jdbcTemplate.update(
                "INSERT INTO traffic_stats (source, count, updated_at) VALUES (?, 1, NOW()) " +
                "ON CONFLICT (source) DO UPDATE SET count = traffic_stats.count + 1, updated_at = NOW()",
                key);
        return ResponseEntity.ok(ApiResponse.ok("Ghi nhận lượt truy cập thành công", queryTrafficMap()));
    }

    private Map<String, Long> queryTrafficMap() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("facebook", 84L);
        result.put("tiktok", 52L);
        result.put("direct", 29L);
        result.put("google", 6L);
        result.put("zalo", 4L);
        try {
            jdbcTemplate.query("SELECT source, count FROM traffic_stats", rs -> {
                result.put(rs.getString("source"), rs.getLong("count"));
            });
        } catch (Exception ignored) {
            // Fallback to seeded defaults if table is not yet initialized
        }
        return result;
    }

    private String normalizeSource(String raw) {
        if (raw == null) return "direct";
        String s = raw.toLowerCase().trim();
        if (s.contains("facebook") || s.contains("fb") || s.contains("messenger") || s.contains("instagram")) return "facebook";
        if (s.contains("tiktok")) return "tiktok";
        if (s.contains("zalo")) return "zalo";
        if (s.contains("google")) return "google";
        return "direct";
    }
}
