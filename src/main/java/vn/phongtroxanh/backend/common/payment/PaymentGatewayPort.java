package vn.phongtroxanh.backend.common.payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGatewayPort {

    String createPaymentUrl(String transactionCode, BigDecimal amount, String orderInfo, String ipAddress, String returnUrl);

    boolean verifyIpn(Map<String, String> params);
}
