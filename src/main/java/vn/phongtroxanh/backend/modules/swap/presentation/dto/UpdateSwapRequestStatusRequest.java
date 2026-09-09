package vn.phongtroxanh.backend.modules.swap.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.swap.domain.SwapStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSwapRequestStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private SwapStatus status;
}
