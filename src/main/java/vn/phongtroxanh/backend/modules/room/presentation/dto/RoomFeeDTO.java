package vn.phongtroxanh.backend.modules.room.presentation.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomFeeDTO {
    private UUID id;
    @NotBlank(message = "Tên khoản phí không được để trống")
    @Size(max = 100)
    private String feeLabel;
    @NotBlank(message = "Giá trị khoản phí không được để trống")
    @Size(max = 100)
    private String feeValue;
}
