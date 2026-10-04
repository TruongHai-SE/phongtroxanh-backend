package vn.phongtroxanh.backend.modules.review.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    private UUID rentalId;
    private UUID roomId;

    @NotNull(message = "Điểm đánh giá không được để trống")
    @Min(value = 1, message = "Điểm đánh giá tối thiểu là 1")
    @Max(value = 5, message = "Điểm đánh giá tối đa là 5")
    private Integer rating;

    private Integer cleanlinessRating;
    private Integer accuracyRating;
    private Integer communicationRating;
    @NotBlank(message = "Nội dung đánh giá không được để trống")
    @Size(max = 5000, message = "Nội dung đánh giá tối đa 5000 ký tự")
    private String comment;
    private List<String> tags;
    private List<String> images;
}
