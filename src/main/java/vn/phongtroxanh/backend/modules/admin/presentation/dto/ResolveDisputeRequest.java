package vn.phongtroxanh.backend.modules.admin.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.review.domain.ReviewDisputeStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "Quyết định xử lý không được để trống (RESOLVED_UPHELD hoặc RESOLVED_REMOVED)")
    private ReviewDisputeStatus decision;

    private String adminNotes;
}
