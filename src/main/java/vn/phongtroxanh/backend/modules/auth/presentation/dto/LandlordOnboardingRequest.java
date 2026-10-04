package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandlordOnboardingRequest {

    // Step 1: Thông tin liên hệ
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên từ 2 đến 100 ký tự")
    @Pattern(regexp = "^(?!^\\d+$).+$", message = "Họ và tên không thể chỉ toàn chữ số")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0[3|5|7|8|9])[0-9]{8}$", message = "Số điện thoại không đúng định dạng (10 chữ số)")
    private String phoneNumber;

    @NotBlank(message = "Địa chỉ liên hệ không được để trống")
    private String address;

    // Step 2: Quy mô phòng trọ
    @NotNull(message = "Số lượng phòng dự kiến không được để trống")
    @PositiveOrZero(message = "Số lượng phòng dự kiến không được âm")
    private Integer estimatedRoomCount;

    @NotEmpty(message = "Vui lòng chọn ít nhất 1 khu vực hoạt động chính")
    private List<String> primaryDistricts;

    // Step 3: Giấy tờ xác thực / CCCD
    private String idCardNumber;
    private String idCardFrontUrl;
    private String idCardBackUrl;
}
