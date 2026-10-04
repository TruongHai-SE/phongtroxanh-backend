package vn.phongtroxanh.backend.modules.review.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "Kết quả giải quyết không được để trống")
    private Boolean approved; // true: chấp thuận khiếu nại (gỡ review, hoàn điểm), false: bác bỏ khiếu nại (giữ review)

    private String adminNotes;
}
