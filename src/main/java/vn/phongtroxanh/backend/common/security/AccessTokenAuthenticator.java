package vn.phongtroxanh.backend.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticator {
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    public UserPrincipal authenticate(String token) {
        if (token == null || !tokenProvider.validateToken(token) ||
                Boolean.TRUE.equals(redisTemplate.hasKey("auth:blacklist:" + token))) {
            throw new UnauthorizedException("INVALID_ACCESS_TOKEN", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }
        var claims = tokenProvider.extractClaims(token);
        if (!"ACCESS".equals(claims.get("type", String.class))) {
            throw new UnauthorizedException("INVALID_ACCESS_TOKEN", "Yêu cầu access token");
        }
        UUID id;
        try {
            id = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("INVALID_ACCESS_TOKEN", "Access token không hợp lệ");
        }
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("ACCOUNT_INACTIVE", "Tài khoản không còn tồn tại"));
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.WARNED) {
            throw new UnauthorizedException("ACCOUNT_INACTIVE", "Tài khoản đã bị khóa hoặc không còn tồn tại");
        }
        Object currentVersion = redisTemplate.opsForValue().get("auth:credential-version:" + id);
        Number tokenVersion = claims.get("credentialVersion", Number.class);
        if ((tokenVersion == null ? 0 : tokenVersion.longValue()) !=
                (currentVersion == null ? 0 : ((Number) currentVersion).longValue())) {
            throw new UnauthorizedException("SESSION_REVOKED", "Phiên đăng nhập đã bị thu hồi");
        }
        return UserPrincipal.builder().id(id).email(user.getEmail()).phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name()).fullName(user.getFullName()).active(true).build();
    }
}
