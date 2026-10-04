package vn.phongtroxanh.backend.modules.monetization;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.model.webhooks.WebhookData;
import vn.phongtroxanh.backend.common.exception.*;
import vn.phongtroxanh.backend.common.payment.PayOsPaymentAdapter;
import vn.phongtroxanh.backend.common.security.UserPrincipal;
import vn.phongtroxanh.backend.modules.monetization.application.service.PaymentService;
import vn.phongtroxanh.backend.modules.monetization.domain.PaymentMethod;
import vn.phongtroxanh.backend.modules.monetization.presentation.dto.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="PTX_TEST_DB_URL",matches=".*ptx_mvp_check.*")
class PayOsFlowIntegrationTest {
    @Autowired PaymentService service;
    @Autowired JdbcTemplate jdbc;
    @MockBean PayOsPaymentAdapter gateway;
    UUID userId;
    @BeforeEach void setup() {
        userId=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,full_name,role) VALUES (?,?,'unused-test-hash','Payment Test','TENANT')",userId,userId+"@example.com");
        jdbc.update("INSERT INTO user_consumables(user_id) VALUES (?)",userId);
        var principal=UserPrincipal.builder().id(userId).role("TENANT").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        when(gateway.create(anyLong(),anyLong(),anyString(),anyString(),any())).thenAnswer(i -> {
            long order=i.getArgument(0),amount=i.getArgument(1);
            return CreatePaymentLinkResponse.builder().orderCode(order).amount(amount).currency("VND")
                    .bin("970422").accountNumber("123").accountName("Test account").description("PTX").qrCode("test-qr")
                    .paymentLinkId("link"+order).status(PaymentLinkStatus.PENDING).checkoutUrl("https://pay.payos.vn/web/link"+order).build();
        });
        when(gateway.verify(anyMap())).thenAnswer(i -> ((Map<?,?>)i.getArgument(0)).get("data"));
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM notifications WHERE user_id=?",userId);
        jdbc.update("DELETE FROM subscriptions WHERE user_id=?",userId);
        jdbc.update("DELETE FROM payment_transactions WHERE user_id=?",userId);
        jdbc.update("DELETE FROM user_consumables WHERE user_id=?",userId);
        jdbc.update("DELETE FROM users WHERE id=?",userId);
    }
    private CreatePaymentResponse checkout(String key) {
        var http=new MockHttpServletRequest(); http.addHeader("Idempotency-Key",key);
        return service.createPayment(CreatePaymentRequest.builder().packageId("PRO_TENANT").paymentMethod(PaymentMethod.PAYOS).build(),http);
    }
    private Map<String,Object> event(CreatePaymentResponse payment,long amount) {
        var data=WebhookData.builder().orderCode(Long.valueOf(payment.getTransactionCode())).amount(amount).description("PTX")
                .accountNumber("123").reference("reference"+payment.getTransactionCode()).transactionDateTime("2026-10-01 11:00:00")
                .currency("VND").paymentLinkId(payment.getPaymentLinkId()).code("00").desc("success").build();
        return Map.of("data",data);
    }
    private Map<String,Object> event(CreatePaymentResponse payment) {
        return event(payment, payment.getAmount().longValue());
    }
    @Test void checkoutRetryAndWebhookReplayGrantOneSubscription() {
        var first=checkout("retry"); var second=checkout("retry");
        assertEquals(first.getTransactionCode(),second.getTransactionCode());
        verify(gateway,times(1)).create(anyLong(),anyLong(),anyString(),anyString(),any());
        service.processPayOsWebhook(event(first)); service.processPayOsWebhook(event(first));
        assertEquals("SUCCESS",service.getPaymentResult(first.getTransactionCode()).getStatus().name());
        assertEquals(50,jdbc.queryForObject("SELECT swipes_left FROM user_consumables WHERE user_id=?",Integer.class,userId));
        assertEquals(2,jdbc.queryForObject("SELECT boosts_left FROM user_consumables WHERE user_id=?",Integer.class,userId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,userId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND type='PAYMENT'",Integer.class,userId));
    }
    @Test void concurrentDifferentOrdersCannotDoubleTheDailyAllowance() throws Exception {
        var first=checkout("first"); var second=checkout("second");
        var barrier=new CyclicBarrier(2);
        when(gateway.verify(anyMap())).thenAnswer(i -> { barrier.await(5,TimeUnit.SECONDS); return ((Map<?,?>)i.getArgument(0)).get("data"); });
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.processPayOsWebhook(event(first)));
            var b=pool.submit(()->service.processPayOsWebhook(event(second)));
            a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
        }
        assertEquals(50,jdbc.queryForObject("SELECT free_swipes_left FROM user_consumables WHERE user_id=?",Integer.class,userId));
        assertEquals(4,jdbc.queryForObject("SELECT boosts_left FROM user_consumables WHERE user_id=?",Integer.class,userId));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,userId));
    }
    @Test void amountMismatchKeepsOrderPending() {
        var payment=checkout("wrong-amount");
        assertThrows(BadRequestException.class,()->service.processPayOsWebhook(event(payment,1)));
        assertEquals("PENDING",service.getPaymentResult(payment.getTransactionCode()).getStatus().name());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=?",Integer.class,userId));
    }
    @Test void relativeReturnUrlIsRejectedAndExpiredRetryCannotReturnCheckout() {
        var request=CreatePaymentRequest.builder().packageId("PRO_TENANT").returnUrl("/relative").build();
        assertThrows(BadRequestException.class,()->service.createPayment(request,new MockHttpServletRequest()));
        var payment=checkout("expired");
        jdbc.update("UPDATE payment_transactions SET qr_expired_at=NOW()-INTERVAL '1 second' WHERE gateway_order_id=?",payment.getTransactionCode());
        assertThrows(ConflictException.class,()->checkout("expired"));
        service.processPayOsWebhook(event(payment));
        assertEquals("SUCCESS",service.getPaymentResult(payment.getTransactionCode()).getStatus().name());
    }
}
