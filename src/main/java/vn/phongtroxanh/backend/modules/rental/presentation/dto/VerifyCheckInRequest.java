package vn.phongtroxanh.backend.modules.rental.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCheckInRequest {

    @NotBlank(message = "Mã xác nhận Check-in không được để trống")
    private String checkInCode;
}
