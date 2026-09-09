package vn.phongtroxanh.backend.modules.review.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDisputeRequest {

    @NotBlank(message = "Lý do khiếu nại không được để trống")
    private String reason;

    private List<String> evidenceImages;
}
