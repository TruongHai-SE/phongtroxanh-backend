package vn.phongtroxanh.backend.modules.review.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu chỉnh sửa đánh giá phòng trọ / chủ trọ")
public class UpdateReviewRequest {

    @NotNull(message = "Số sao đánh giá không được để trống")
    @Min(value = 1, message = "Đánh giá tối thiểu 1 sao")
    @Max(value = 5, message = "Đánh giá tối đa 5 sao")
    @Schema(description = "Số sao đánh giá mới (1 - 5)", example = "5")
    private Integer rating;

    @NotBlank(message = "Nội dung nhận xét không được để trống")
    @Schema(description = "Nội dung nhận xét cập nhật", example = "Chủ trọ đã hỗ trợ sửa điều hòa rất nhanh chóng và nhiệt tình.")
    private String comment;

    @Schema(description = "Danh sách nhãn nổi bật cập nhật")
    private List<String> tags;

    @Schema(description = "Danh sách link ảnh cập nhật")
    private List<String> images;
}
