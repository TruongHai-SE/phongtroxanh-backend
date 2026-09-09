package vn.phongtroxanh.backend.modules.matching.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.phongtroxanh.backend.common.dto.ApiResponse;
import vn.phongtroxanh.backend.modules.matching.application.service.MatchingService;
import vn.phongtroxanh.backend.modules.matching.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.presentation.dto.MatchingProfileRequest;
import vn.phongtroxanh.backend.modules.user.presentation.dto.MatchingProfileResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT') or hasRole('ADMIN')")
@Tag(name = "Module 4: Roommate Matching Engine", description = "Các API quẹt thẻ bạn cùng phòng, thuật toán 8 trụ cột, phát hiện tương thích 2 chiều và danh sách ghép đôi")
public class MatchingController {

    private final MatchingService matchingService;

    @GetMapping("/feed")
    @Operation(summary = "API #40: Bảng tin khám phá bạn cùng phòng (Discovery Feed)", description = "Lấy danh sách ứng viên bạn ở ghép tiềm năng đã được chấm điểm tương thích 8 trụ cột")
    public ResponseEntity<ApiResponse<List<RoommateCardDTO>>> getFeed() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách gợi ý thành công", matchingService.getDiscoveryFeed()));
    }

    @PostMapping("/swipe")
    @Operation(summary = "API #41: Hành động quẹt (LIKE / DISLIKE / SUPER_LIKE)", description = "Thực hiện quẹt hồ sơ (trừ 1 lượt quẹt atomic SQL). Tự động phát hiện Match 2 chiều và tạo phòng chat")
    public ResponseEntity<ApiResponse<SwipeResponse>> swipe(@Valid @RequestBody SwipeRequest request) {
        SwipeResponse response = matchingService.swipe(request);
        String msg = response.isMatch() ? "Chúc mừng! Bạn và đối phương đã tương thích ghép đôi thành công!" : "Ghi nhận hành động quẹt thành công";
        return ResponseEntity.ok(ApiResponse.ok(msg, response));
    }

    @GetMapping("/matches")
    @Operation(summary = "API #42: Danh sách các cặp đã ghép đôi (Mutual Matches)", description = "Lấy danh sách tất cả những người đã tương thích 2 chiều với người dùng hiện tại")
    public ResponseEntity<ApiResponse<List<MatchItemDTO>>> getMatches() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách ghép đôi thành công", matchingService.getMatches()));
    }

    @DeleteMapping("/matches/{matchId}")
    @Operation(summary = "API #43: Hủy ghép đôi (Unmatch)", description = "Hủy ghép đôi với một người dùng (kiểm tra quyền sở hữu chống BOLA)")
    public ResponseEntity<ApiResponse<Void>> unmatch(@PathVariable("matchId") UUID matchId) {
        matchingService.unmatch(matchId);
        return ResponseEntity.ok(ApiResponse.ok("Hủy ghép đôi thành công", null));
    }

    @PostMapping("/boost")
    @Operation(summary = "API #44: Đẩy hồ sơ tìm bạn (Boost Profile)", description = "Sử dụng 1 lượt đẩy hồ sơ để xuất hiện đầu danh sách Discovery Feed của người khác")
    public ResponseEntity<ApiResponse<Void>> boostProfile() {
        matchingService.boostProfile();
        return ResponseEntity.ok(ApiResponse.ok("Đẩy hồ sơ thành công", null));
    }

    @GetMapping("/compatibility/{targetUserId}")
    @Operation(summary = "API #45: Chi tiết tương thích 8 trụ cột", description = "Xem bảng phân tích chi tiết độ tương thích giữa 2 người: Ngân sách, Địa điểm, Ngủ sớm, Gọn gàng, Tiếp khách, Thuốc lá, Độ ồn, Sở thích")
    public ResponseEntity<ApiResponse<CompatibilityDetailResponse>> getCompatibility(@PathVariable("targetUserId") UUID targetUserId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết tương thích thành công", matchingService.getCompatibility(targetUserId)));
    }

    @GetMapping("/preferences")
    @Operation(summary = "API #46: Lấy tiêu chí tìm bạn cùng phòng", description = "Xem tiêu chí và thói quen sinh hoạt đã cấu hình")
    public ResponseEntity<ApiResponse<MatchingProfileResponse>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tiêu chí thành công", matchingService.getPreferences()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "API #47: Cập nhật tiêu chí tìm bạn cùng phòng", description = "Cập nhật lại tiêu chí và thói quen sinh hoạt của bản thân")
    public ResponseEntity<ApiResponse<MatchingProfileResponse>> updatePreferences(@Valid @RequestBody MatchingProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tiêu chí thành công", matchingService.updatePreferences(request)));
    }
}
