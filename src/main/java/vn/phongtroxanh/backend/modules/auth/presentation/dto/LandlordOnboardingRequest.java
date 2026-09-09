package vn.phongtroxanh.backend.modules.auth.presentation.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandlordOnboardingRequest {

    // Step 1: Thông tin liên hệ
    private String fullName;
    private String phoneNumber;
    private String address;

    // Step 2: Quy mô phòng trọ
    @PositiveOrZero(message = "Số lượng phòng dự kiến không được âm")
    private Integer estimatedRoomCount;
    private List<String> primaryDistricts;

    // Step 3: Giấy tờ xác thực / CCCD
    private String idCardNumber;
    private String idCardFrontUrl;
    private String idCardBackUrl;
}
