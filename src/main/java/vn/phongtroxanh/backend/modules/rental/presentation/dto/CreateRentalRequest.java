package vn.phongtroxanh.backend.modules.rental.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRentalRequest {

    @NotNull(message = "roomId không được để trống")
    private UUID roomId;

    @NotNull(message = "Ngày bắt đầu thuê không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc thuê không được để trống")
    private LocalDate endDate;

    private String message;
}
