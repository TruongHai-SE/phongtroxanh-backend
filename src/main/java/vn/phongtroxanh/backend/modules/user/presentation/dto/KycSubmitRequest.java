package vn.phongtroxanh.backend.modules.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmitRequest {

    @NotBlank(message = "Số CCCD/CMND không được để trống")
    @Pattern(regexp = "^[0-9]{9,12}$", message = "Số CCCD/CMND phải từ 9 đến 12 chữ số hợp lệ")
    private String idCardNumber;

    @NotBlank(message = "Ảnh mặt trước CCCD không được để trống")
    private String idCardFrontUrl;

    @NotBlank(message = "Ảnh mặt sau CCCD không được để trống")
    private String idCardBackUrl;
}
