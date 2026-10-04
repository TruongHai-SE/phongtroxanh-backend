package vn.phongtroxanh.backend.modules.auth.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.phongtroxanh.backend.common.mail.EmailNotificationPort;
import vn.phongtroxanh.backend.common.security.CookieUtils;
import vn.phongtroxanh.backend.common.security.JwtTokenProvider;
import vn.phongtroxanh.backend.modules.auth.presentation.dto.*;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.*;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSecurityRegressionTest {
    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;
    @Mock UserConsumableRepository consumables;
    @Mock UserVerificationRepository verifications;
    @Mock PasswordEncoder passwords;
    @Mock CookieUtils cookies;
    @Mock EmailNotificationPort mail;
    @Mock RedisTemplate<String, Object> redis;
    @Mock ValueOperations<String, Object> values;
    @Mock jakarta.persistence.EntityManager entityManager;
    @org.mockito.Spy JwtTokenProvider jwt = new JwtTokenProvider("security-test-key-0123456789012345678901234567890123456789", 900000, 604800000);
    @org.mockito.InjectMocks AuthService service;
    MockHttpServletResponse response = new MockHttpServletResponse();

    @Test void tenantOnboardingRejectsNegativeBudget() {
        UUID id = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.of(UserProfile.builder().userId(id).build()));
        try (var security = mockStatic(vn.phongtroxanh.backend.common.security.SecurityUtils.class)) {
            security.when(vn.phongtroxanh.backend.common.security.SecurityUtils::getCurrentUserId).thenReturn(id);
            assertThrows(vn.phongtroxanh.backend.common.exception.BadRequestException.class, () ->
                    service.tenantOnboarding(TenantOnboardingRequest.builder().budgetMin(java.math.BigDecimal.valueOf(-1)).build()));
            verify(profiles, never()).save(any());
        }
    }

    @Test void tenantOnboardingRejectsPartialBudgetAboveExistingMaximum() {
        UUID id = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.of(UserProfile.builder().userId(id).budgetMax(java.math.BigDecimal.valueOf(4000000)).build()));
        try (var security = mockStatic(vn.phongtroxanh.backend.common.security.SecurityUtils.class)) {
            security.when(vn.phongtroxanh.backend.common.security.SecurityUtils::getCurrentUserId).thenReturn(id);
            assertThrows(vn.phongtroxanh.backend.common.exception.BadRequestException.class, () ->
                    service.tenantOnboarding(TenantOnboardingRequest.builder().budgetMin(java.math.BigDecimal.valueOf(5000000)).build()));
            verify(profiles, never()).save(any());
        }
    }

    @Test void landlordOnboardingCannotBypassPendingKycGuard() {
        UUID id = UUID.randomUUID();
        User user = User.builder().role(UserRole.LANDLORD).build(); user.setId(id);
        when(users.findById(id)).thenReturn(Optional.of(user));
        lenient().when(verifications.existsByUserIdAndStatusIn(eq(id), anyList())).thenReturn(true);
        try (var security = mockStatic(vn.phongtroxanh.backend.common.security.SecurityUtils.class)) {
            security.when(vn.phongtroxanh.backend.common.security.SecurityUtils::getCurrentUserId).thenReturn(id);
            assertThrows(vn.phongtroxanh.backend.common.exception.ConflictException.class, () -> service.landlordOnboarding(
                    LandlordOnboardingRequest.builder().idCardNumber("012345678901").idCardFrontUrl("https://example.com/front.png")
                            .idCardBackUrl("https://example.com/back.png").build()));
            verify(verifications, never()).save(any());
        }
    }


    @Test void publicRegisterRejectsAdminBeforeWriting() {
        var request = RegisterRequest.builder().email("admin@example.com").password("password").fullName("Admin").role(UserRole.ADMIN).build();
        assertThrows(RuntimeException.class, () -> service.register(request, response));
        verify(users, never()).save(any());
    }

    @Test void registerRequiresUsableContact() {
        var request = RegisterRequest.builder().password("password").fullName("Tenant").build();
        assertThrows(RuntimeException.class, () -> service.register(request, response));
        verify(users, never()).save(any());
    }

    @Test void googleRejectsUnsignedPayloadWithNoClientId() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"email\":\"victim@example.com\"}".getBytes());
        var request = new GoogleLoginRequest("header." + payload + ".mock_sig", UserRole.TENANT);
        assertThrows(RuntimeException.class, () -> service.googleLogin(request, response));
        verify(users, never()).findByEmail(anyString());
    }

    @Test void googleRejectsPublicAdminRole() {
        ReflectionTestUtils.setField(service, "googleClientId", "client-id");
        assertThrows(RuntimeException.class, () -> service.googleLogin(new GoogleLoginRequest("header.payload.mock_sig", UserRole.ADMIN), response));
        verify(users, never()).findByEmail(anyString());
    }

    @Test void registerOtpCannotResetPassword() {
        String token = jwt.generateTempToken("tenant@example.com", "REGISTER");
        User user = User.builder().email("tenant@example.com").build();
        lenient().when(users.findByEmail("tenant@example.com")).thenReturn(Optional.of(user));
        var request = new ResetPasswordRequest(token, "new-password");
        assertThrows(RuntimeException.class, () -> service.resetPassword(request));
        verify(users, never()).save(any());
    }

    @Test void refreshRequiresRefreshTokenType() {
        UUID id = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        String token = jwt.generateAccessToken(id, "TENANT", "tenant@example.com");
        when(cookies.extractRefreshTokenFromCookie(request)).thenReturn(token);
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(values.get(anyString())).thenReturn("session");
        lenient().when(users.findById(id)).thenReturn(Optional.of(User.builder().build()));
        assertThrows(RuntimeException.class, () -> service.refreshToken(request, response));
        verify(users, never()).findById(any());
    }

    @Test void refreshRejectsLockedAccountBeforeIssuingTokens() {
        UUID id = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        String token = jwt.generateRefreshToken(id, "session");
        when(cookies.extractRefreshTokenFromCookie(request)).thenReturn(token);
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(values.get(anyString())).thenReturn(java.util.Map.of("userId", id.toString()));
        lenient().when(values.getAndDelete(anyString())).thenReturn(java.util.Map.of("userId", id.toString()));
        when(users.findById(id)).thenReturn(Optional.of(User.builder().status(UserStatus.LOCKED).build()));
        assertThrows(RuntimeException.class, () -> service.refreshToken(request, response));
        verify(cookies, never()).addRefreshTokenCookie(any(), any(), anyLong());
    }

    @Test void consumedPasswordResetProofCannotBeReplayed() {
        String token = jwt.generateTempToken("tenant@example.com", "FORGOT_PASSWORD");
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(users.findByEmail("tenant@example.com")).thenReturn(Optional.of(User.builder().build()));
        assertThrows(RuntimeException.class, () -> service.resetPassword(new ResetPasswordRequest(token, "new-password")));
        verify(users, never()).save(any());
    }

    @Test void otpAttemptLimitStopsGuessingEvenForCorrectCode() {
        when(redis.opsForValue()).thenReturn(values);
        lenient().when(values.get("auth:otp:tenant@example.com")).thenReturn(java.util.Map.of("otp", "123456", "purpose", "FORGOT_PASSWORD"));
        lenient().when(values.increment("auth:otp-attempts:tenant@example.com")).thenReturn(6L);
        assertThrows(vn.phongtroxanh.backend.common.exception.RateLimitException.class, () -> service.verifyOtp(new VerifyOtpRequest("tenant@example.com", "123456")));
        verify(values, never()).set(startsWith("auth:temp-token:"), any(), anyLong(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"aud", "iss", "exp", "email_verified", "sub"})
    void googleRejectsInvalidVerifiedClaims(String invalidClaim) throws Exception {
        var payload = new ObjectMapper().readTree("{\"aud\":\"client-id\",\"iss\":\"https://accounts.google.com\",\"exp\":4102444800,\"email_verified\":true,\"sub\":\"google-user\",\"email\":\"tenant@gmail.com\"}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).remove(invalidClaim);
        withGoogleResponse(payload.toString(), () -> {
            assertThrows(vn.phongtroxanh.backend.common.exception.UnauthorizedException.class,
                    () -> service.googleLogin(new GoogleLoginRequest("header.payload.signature", UserRole.TENANT), response));
            verify(users, never()).findByEmail(anyString());
        });
    }

    @Test void verifiedGoogleEmailDoesNotGrantKyc() {
        UUID id = UUID.randomUUID();
        User user = User.builder().email("tenant@gmail.com").role(UserRole.TENANT).isVerified(false).status(UserStatus.ACTIVE).build();
        user.setId(id);
        when(users.findByEmail("tenant@gmail.com")).thenReturn(Optional.of(user));
        when(redis.opsForValue()).thenReturn(values);
        withGoogleResponse("{\"aud\":\"client-id\",\"iss\":\"https://accounts.google.com\",\"exp\":4102444800,\"email_verified\":true,\"sub\":\"google-user\",\"email\":\"tenant@gmail.com\"}", () -> {
            assertNotNull(service.googleLogin(new GoogleLoginRequest("header.payload.signature", UserRole.TENANT), response).getAccessToken());
            assertFalse(user.getIsVerified());
        });
    }

    @Test void logoutBlacklistCoversConfiguredAccessLifetime() {
        JwtTokenProvider longLived = new JwtTokenProvider("security-test-key-0123456789012345678901234567890123456789", 3600000, 604800000);
        service = new AuthService(users, profiles, consumables, verifications, passwords, longLived, cookies, mail, redis, entityManager);
        String token = longLived.generateAccessToken(UUID.randomUUID(), "TENANT", "tenant@example.com");
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        when(redis.opsForValue()).thenReturn(values);
        service.logout(request, response);
        var ttl = org.mockito.ArgumentCaptor.forClass(Long.class);
        var unit = org.mockito.ArgumentCaptor.forClass(java.util.concurrent.TimeUnit.class);
        verify(values).set(eq("auth:blacklist:" + token), eq("revoked"), ttl.capture(), unit.capture());
        assertTrue(unit.getValue().toMillis(ttl.getValue()) > 3500000);
    }

    @Test void refreshSessionRotatesOnceAndCannotReplay() {
        UUID id = UUID.randomUUID();
        User user = User.builder().email("tenant@example.com").status(UserStatus.ACTIVE).build();
        user.setId(id);
        var request = new MockHttpServletRequest();
        String token = jwt.generateRefreshToken(id, "old-session");
        when(cookies.extractRefreshTokenFromCookie(request)).thenReturn(token);
        when(redis.opsForValue()).thenReturn(values);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(values.getAndDelete("auth:refresh-session:old-session")).thenReturn(java.util.Map.of("userId", id.toString()), null);
        assertNotNull(service.refreshToken(request, response).getAccessToken());
        assertThrows(vn.phongtroxanh.backend.common.exception.UnauthorizedException.class, () -> service.refreshToken(request, response));
        verify(cookies, times(1)).addRefreshTokenCookie(any(), any(), anyLong());
        verify(values, never()).get("auth:refresh-session:old-session");
    }

    @Test void forgotPasswordOtpCreatesSingleUsePurposeBoundProof() {
        String email = "tenant@example.com";
        User user = User.builder().email(email).status(UserStatus.ACTIVE).build();
        user.setId(UUID.randomUUID());
        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        when(redis.opsForValue()).thenReturn(values);
        java.util.Map<String, Object> cache = new java.util.HashMap<>();
        when(values.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long count = ((Number) cache.getOrDefault(key, 0L)).longValue() + 1;
            cache.put(key, count);
            return count;
        });
        doAnswer(invocation -> { cache.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(values).set(anyString(), any(), anyLong(), any());
        when(values.get(anyString())).thenAnswer(invocation -> cache.get(invocation.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(invocation -> cache.remove(invocation.getArgument(0)));
        when(passwords.encode("new-password")).thenReturn("encoded-password");
        service.forgotPassword(new ForgotPasswordRequest(email));
        String otp = (String) ((java.util.Map<?, ?>) cache.get("auth:otp:" + email)).get("otp");
        String token = service.verifyOtp(new VerifyOtpRequest(email, otp));
        assertEquals("FORGOT_PASSWORD", jwt.extractClaims(token).get("purpose"));
        service.resetPassword(new ResetPasswordRequest(token, "new-password"));
        assertEquals("encoded-password", user.getPasswordHash());
        assertEquals(1L, cache.get("auth:credential-version:" + user.getId()));
        assertThrows(vn.phongtroxanh.backend.common.exception.UnauthorizedException.class,
                () -> service.resetPassword(new ResetPasswordRequest(token, "new-password")));
        verify(users, times(1)).save(user);
        verify(mail).sendOtpEmail(email, otp);
    }

    @Test void loginLocksCurrentUserBeforeCheckingPasswordAndIssuingVersionedTokens() {
        User user = User.builder().email("tenant@example.com").passwordHash("hash").status(UserStatus.ACTIVE).build();
        user.setId(UUID.randomUUID());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        service.login(LoginRequest.builder().login(user.getEmail()).password("password").build(), response);
        var order = inOrder(users, entityManager, passwords);
        order.verify(users).findByEmail(user.getEmail());
        order.verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        order.verify(passwords).matches("password", "hash");
        assertLockBeforeVersionReads(user);
    }

    @Test void resetLocksCurrentUserBeforeConsumingProofAndRevokingCredentials() {
        User user = User.builder().email("tenant@example.com").status(UserStatus.ACTIVE).build();
        user.setId(UUID.randomUUID());
        String token = jwt.generateTempToken(user.getEmail(), "FORGOT_PASSWORD");
        String proofKey = "auth:temp-token:" + jwt.extractClaims(token).getId();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(proofKey)).thenReturn(user.getEmail());
        service.resetPassword(new ResetPasswordRequest(token, "new-password"));
        var order = inOrder(entityManager, values, passwords);
        order.verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        order.verify(values).getAndDelete(proofKey);
        order.verify(values).increment("auth:credential-version:" + user.getId());
        order.verify(passwords).encode("new-password");
    }

    @Test void refreshLocksCurrentUserBeforeCheckingVersionAndConsumingSession() {
        User user = User.builder().email("tenant@example.com").status(UserStatus.ACTIVE).build();
        user.setId(UUID.randomUUID());
        String token = jwt.generateRefreshToken(user.getId(), "session");
        var request = new MockHttpServletRequest();
        when(cookies.extractRefreshTokenFromCookie(request)).thenReturn(token);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("auth:refresh-session:session")).thenReturn(java.util.Map.of("userId", user.getId().toString()));
        service.refreshToken(request, response);
        var order = inOrder(entityManager, values);
        order.verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        order.verify(values).getAndDelete("auth:refresh-session:session");
        assertLockBeforeVersionReads(user);
    }

    @Test void googleLocksExistingUserBeforeUpdatingAvatarAndIssuingTokens() {
        User user = User.builder().email("tenant@gmail.com").role(UserRole.TENANT).status(UserStatus.ACTIVE).build();
        user.setId(UUID.randomUUID());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redis.opsForValue()).thenReturn(values);
        withGoogleResponse("{\"aud\":\"client-id\",\"iss\":\"https://accounts.google.com\",\"exp\":4102444800,\"email_verified\":true,\"sub\":\"google-user\",\"email\":\"tenant@gmail.com\",\"picture\":\"https://example.com/avatar.png\"}", () -> {
            service.googleLogin(new GoogleLoginRequest("header.payload.signature", UserRole.TENANT), response);
            var order = inOrder(users, entityManager);
            order.verify(entityManager).refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            order.verify(users).save(user);
            assertLockBeforeVersionReads(user);
        });
    }

    private void assertLockBeforeVersionReads(User user) {
        int lockSequence = mockingDetails(entityManager).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("refresh"))
                .findFirst().orElseThrow().getSequenceNumber();
        var reads = mockingDetails(values).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("get") &&
                        ("auth:credential-version:" + user.getId()).equals(invocation.getArgument(0))).toList();
        assertFalse(reads.isEmpty());
        assertTrue(reads.stream().allMatch(invocation -> invocation.getSequenceNumber() > lockSequence));
    }

    private void withGoogleResponse(String payload, Runnable assertions) {
        ReflectionTestUtils.setField(service, "googleClientId", "client-id");
        var realBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(realBuilder).build();
        RestClient client = realBuilder.build();
        var builder = mock(RestClient.Builder.class);
        when(builder.requestFactory(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=header.payload.signature"))
                .andRespond(withSuccess(payload, org.springframework.http.MediaType.APPLICATION_JSON));
        try (var restClient = mockStatic(RestClient.class)) {
            restClient.when(RestClient::builder).thenReturn(builder);
            assertions.run();
            server.verify();
        }
    }
}

