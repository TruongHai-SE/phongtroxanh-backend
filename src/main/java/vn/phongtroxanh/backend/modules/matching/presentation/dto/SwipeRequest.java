package vn.phongtroxanh.backend.modules.matching.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.matching.domain.SwipeAction;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwipeRequest {

    @NotNull(message = "targetUserId không được để trống")
    private UUID targetUserId;

    @NotNull(message = "Hành động quẹt không được để trống")
    private SwipeAction action;
}
