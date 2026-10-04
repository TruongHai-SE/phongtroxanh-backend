package vn.phongtroxanh.backend.modules.monetization.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.user.domain.UserRole;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPackagePlanRequest {

    private String id;

    @NotNull(message = "Đối tượng áp dụng không được để trống")
    private UserRole targetRole;

    @NotBlank(message = "Tên gói không được để trống")
    private String name;

    @NotNull(message = "Giá theo tháng không được để trống")
    private BigDecimal priceMonthly;

    @NotNull(message = "Giá theo năm không được để trống")
    private BigDecimal priceYearly;

    @NotBlank(message = "Tính năng gói không được để trống")
    private String features;
}
