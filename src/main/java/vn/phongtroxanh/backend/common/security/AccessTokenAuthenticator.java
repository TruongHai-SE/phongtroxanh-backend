package vn.phongtroxanh.backend.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;
import vn.phongtroxanh.backend.modules.user.domain.UserStatus;
import vn.phongtroxanh.backend.modules.user.infrastructure.repository.UserRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticator {
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long CACHE_TTL_MS = 15_000L;

    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private record CachedPrincipal(UserPrincipal principal, long expiresAtMs) {}

    // Strictly bounded cache (<= 100 entries, ~15KB RAM) to avoid per-request Redis TLS + DB round-trips on Render Free
    private static final Map<String, CachedPrincipal> principalCache = new ConcurrentHashMap<>();

    public UserPrincipal authenticate(String token) {
        if (token == null || !tokenProvider.validateToken(token)) {
            throw new UnauthorizedException("INVALID_ACCESS_TOKEN", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }

        long now = System.currentTimeMillis();
        CachedPrincipal cached = principalCache.get(token);
        if (cached != null) {
            if (now < cached.expiresAtMs()) {
                return cached.principal();
            }
            principalCache.remove(token);
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey("auth:blacklist:" + token))) {
            principalCache.remove(token);
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
        UserPrincipal principal = UserPrincipal.builder()
                .id(id)
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole() != null ? user.getRole().name() : "TENANT")
                .fullName(user.getFullName())
                .active(true)
                .build();

        if (principalCache.size() >= MAX_CACHE_ENTRIES) {
            principalCache.entrySet().removeIf(e -> now >= e.getValue().expiresAtMs());
            if (principalCache.size() >= MAX_CACHE_ENTRIES) {
                principalCache.clear();
            }
        }
        principalCache.put(token, new CachedPrincipal(principal, now + CACHE_TTL_MS));
        return principal;
    }

    public static void evictToken(String token) {
        if (token != null) {
            principalCache.remove(token);
        }
    }

    public static void evictUser(UUID userId) {
        if (userId != null) {
            principalCache.entrySet().removeIf(e -> userId.equals(e.getValue().principal().getId()));
        }
    }
}
