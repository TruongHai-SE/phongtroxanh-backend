package vn.phongtroxanh.backend.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.phongtroxanh.backend.modules.user.domain.*;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessSecurityRegressionTest {
    @Mock JwtTokenProvider jwt;
    @Mock RedisTemplate<String, Object> redis;
    @Mock UserRepository users;
    @Mock ValueOperations<String, Object> values;
    JwtAuthenticationFilter filter;
    @org.junit.jupiter.api.BeforeEach void setUp() {
        filter = new JwtAuthenticationFilter(new AccessTokenAuthenticator(jwt, redis, users));
    }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void roleHelperAcceptsExistingPrefix() {
        UserPrincipal principal = UserPrincipal.builder().id(UUID.randomUUID()).role("ADMIN").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertTrue(SecurityUtils.hasRole("ADMIN"));
        assertTrue(SecurityUtils.hasRole("ROLE_ADMIN"));
    }

    @Test void lockedUserCannotAuthenticateWithUnexpiredToken() throws Exception {
        UUID id = UUID.randomUUID();
        JwtTokenProvider real = new JwtTokenProvider("security-test-key-0123456789012345678901234567890123456789", 900000, 604800000);
        String token = real.generateAccessToken(id, "TENANT", "tenant@example.com");
        when(jwt.validateToken(token)).thenReturn(true);
        when(jwt.extractClaims(token)).thenReturn(real.extractClaims(token));
        lenient().when(users.findById(id)).thenReturn(Optional.of(User.builder().status(UserStatus.LOCKED).build()));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void refreshTokenCannotAuthenticateHttp() throws Exception {
        UUID id = UUID.randomUUID();
        JwtTokenProvider real = new JwtTokenProvider("security-test-key-0123456789012345678901234567890123456789", 900000, 604800000);
        String token = real.generateRefreshToken(id, "session");
        when(jwt.validateToken(token)).thenReturn(true);
        when(jwt.extractClaims(token)).thenReturn(real.extractClaims(token));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void accessUsesCurrentDatabaseRole() throws Exception {
        UUID id = UUID.randomUUID();
        JwtTokenProvider real = new JwtTokenProvider("security-test-key-0123456789012345678901234567890123456789", 900000, 604800000);
        String token = real.generateAccessToken(id, "ADMIN", "old@example.com");
        when(jwt.validateToken(token)).thenReturn(true);
        when(jwt.extractClaims(token)).thenReturn(real.extractClaims(token));
        when(users.findById(id)).thenReturn(Optional.of(User.builder().status(UserStatus.ACTIVE).role(UserRole.TENANT).email("current@example.com").build()));
        when(redis.opsForValue()).thenReturn(values);
        var principal = new AccessTokenAuthenticator(jwt, redis, users).authenticate(token);
        assertEquals("TENANT", principal.getRole());
        assertEquals("current@example.com", principal.getEmail());
    }

    @Test void passwordResetCredentialVersionRevokesOldAccessToken() {
        UUID id = UUID.randomUUID();
        JwtTokenProvider real = new JwtTokenProvider("security-test-key-0123456789012345678901234567890123456789", 900000, 604800000);
        String token = real.generateAccessToken(id, "TENANT", "tenant@example.com");
        when(jwt.validateToken(token)).thenReturn(true);
        when(jwt.extractClaims(token)).thenReturn(real.extractClaims(token));
        when(users.findById(id)).thenReturn(Optional.of(User.builder().status(UserStatus.ACTIVE).build()));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("auth:credential-version:" + id)).thenReturn(1L);
        assertThrows(vn.phongtroxanh.backend.common.exception.UnauthorizedException.class,
                () -> new AccessTokenAuthenticator(jwt, redis, users).authenticate(token));
    }
}
