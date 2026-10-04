package vn.phongtroxanh.backend.modules.monetization.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentMethod;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotBlank(message = "packageId không được để trống")
    private String packageId;

    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.PAYOS;

    private String returnUrl;
}
