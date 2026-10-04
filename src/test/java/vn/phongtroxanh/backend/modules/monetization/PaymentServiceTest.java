package vn.phongtroxanh.backend.modules.monetization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.payos.model.webhooks.WebhookData;
import vn.phongtroxanh.backend.common.exception.*;
import vn.phongtroxanh.backend.common.payment.PayOsPaymentAdapter;
import vn.phongtroxanh.backend.common.security.UserPrincipal;
import vn.phongtroxanh.backend.modules.monetization.application.service.PaymentService;
import vn.phongtroxanh.backend.modules.monetization.domain.*;
import vn.phongtroxanh.backend.modules.monetization.infrastructure.repository.*;
import vn.phongtroxanh.backend.modules.monetization.presentation.dto.CreatePaymentRequest;
import vn.phongtroxanh.backend.modules.notification.application.service.NotificationService;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserConsumableRepository;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PackagePlanRepository plans;
    @Mock PaymentTransactionRepository transactions;
    @Mock UserConsumableRepository consumables;
    @Mock PayOsPaymentAdapter gateway;
    @Mock JdbcTemplate jdbc;
    @Mock PlatformTransactionManager manager;
    @Mock ObjectMapper mapper;
    @Mock NotificationService notifications;
    @InjectMocks PaymentService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void retiredSandboxCannotCreateCheckout() {
        var request = CreatePaymentRequest.builder().packageId("PRO_TENANT").paymentMethod(PaymentMethod.VNPAY).build();
        assertThrows(BadRequestException.class, () -> service.createPayment(request, new MockHttpServletRequest()));
        verifyNoInteractions(transactions, gateway);
    }
    @Test void mismatchedAmountDoesNotGrantAnything() {
        var payment = pending();
        when(gateway.verify(anyMap())).thenReturn(webhook(1L));
        when(transactions.findLockedByOrderId("1000000000")).thenReturn(Optional.of(payment));
        assertThrows(BadRequestException.class, () -> service.processPayOsWebhook(Map.of()));
        assertEquals(PaymentStatus.PENDING,payment.getStatus());
        verifyNoInteractions(jdbc,notifications);
    }
    @Test void duplicateWebhookCannotGrantTwice() {
        var payment = pending(); payment.setStatus(PaymentStatus.SUCCESS);
        when(gateway.verify(anyMap())).thenReturn(webhook(49000L));
        when(transactions.findLockedByOrderId("1000000000")).thenReturn(Optional.of(payment));
        service.processPayOsWebhook(Map.of());
        verifyNoInteractions(jdbc,notifications);
        verify(transactions,never()).save(any());
    }
    @Test void browserCallbackReadsDatabaseAndRequiresOwnership() {
        var payment = pending();
        var principal = UserPrincipal.builder().id(UUID.randomUUID()).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        when(transactions.findByGatewayOrderId("1000000000")).thenReturn(Optional.of(payment));
        assertThrows(ForbiddenException.class, () -> service.getPaymentResult("1000000000"));
        verifyNoInteractions(gateway,jdbc);
    }
    private PaymentTransaction pending() {
        return PaymentTransaction.builder().gatewayOrderId("1000000000").userId(UUID.randomUUID()).amount(BigDecimal.valueOf(49000))
                .paymentMethod(PaymentMethod.PAYOS).paymentLinkId("link123").status(PaymentStatus.PENDING).build();
    }
    private WebhookData webhook(long amount) {
        return WebhookData.builder().orderCode(1000000000L).amount(amount).description("PTX123").accountNumber("123")
                .reference("reference").transactionDateTime("2026-10-01 10:00:00").currency("VND").paymentLinkId("link123").code("00").desc("success").build();
    }
}
