package vn.phongtroxanh.backend.modules.auth.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.phongtroxanh.backend.common.security.JwtTokenProvider;
import vn.phongtroxanh.backend.modules.auth.presentation.dto.ResetPasswordRequest;
import vn.phongtroxanh.backend.modules.user.domain.User;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${PTX_TEST_DB_URL}",
        "spring.datasource.username=${PTX_TEST_DB_USER:postgres}",
        "spring.datasource.password=${PTX_TEST_DB_PASSWORD:postgrespassword}",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PTX_TEST_DB_URL", matches = "jdbc:postgresql://[^/]+/ptx_mvp_check(?:\\?.*)?")
class AuthRaceIntegrationTest {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider jwt;
    @Autowired RedisTemplate<String, Object> redis;
    @Autowired PlatformTransactionManager transactions;

    @Test void staleProfileUpdateCannotRestorePasswordAfterReset() throws Exception {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .fullName("Auth race fixture").passwordHash(passwords.encode("old-password")).build());
        String token = jwt.generateTempToken(user.getEmail(), "FORGOT_PASSWORD");
        String proofKey = "auth:temp-token:" + jwt.extractClaims(token).getId();
        String versionKey = "auth:credential-version:" + user.getId();
        redis.opsForValue().set(proofKey, user.getEmail(), 15, TimeUnit.MINUTES);
        var loaded = new CountDownLatch(1);
        var continueProfile = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var staleWrite = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                User stale = users.findById(user.getId()).orElseThrow();
                loaded.countDown();
                try {
                    if (!continueProfile.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Reset did not complete");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                stale.setFullName("Updated profile");
                users.saveAndFlush(stale);
            }));
            try {
                assertTrue(loaded.await(10, TimeUnit.SECONDS));
                auth.resetPassword(new ResetPasswordRequest(token, "new-password"));
            } finally {
                continueProfile.countDown();
            }
            staleWrite.get(10, TimeUnit.SECONDS);
            User current = users.findById(user.getId()).orElseThrow();
            assertEquals("Updated profile", current.getFullName());
            assertTrue(passwords.matches("new-password", current.getPasswordHash()), "Profile write must retain the newly reset password");
            assertFalse(passwords.matches("old-password", current.getPasswordHash()));
        } finally {
            redis.delete(java.util.List.of(proofKey, versionKey));
            users.deleteById(user.getId());
        }
    }
}
