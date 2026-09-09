package vn.phongtroxanh.backend.modules.swap.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSwapRequest {

    private String title;
    private String description;

    @NotNull(message = "currentRoomId không được để trống")
    private UUID currentRoomId;

    private Boolean isLeaseholder;
    private List<String> targetDistricts;
    private List<String> desiredDistricts;
    private String targetRoomType;
    private BigDecimal targetBudgetMax;
    private BigDecimal desiredPriceMax;
    private List<String> habits;
    private String reason;
    private LocalDate targetMoveInDate;
    private LocalDate moveInDate;
}
