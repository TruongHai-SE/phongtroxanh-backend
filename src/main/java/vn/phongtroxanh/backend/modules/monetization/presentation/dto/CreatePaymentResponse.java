package vn.phongtroxanh.backend.modules.monetization.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentMethod;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatePaymentResponse {

    private String transactionCode;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String paymentUrl;
    private String qrCode;
    private String paymentLinkId;
}
