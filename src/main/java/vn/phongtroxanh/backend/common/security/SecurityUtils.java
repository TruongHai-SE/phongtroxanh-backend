package vn.phongtroxanh.backend.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.phongtroxanh.backend.common.exception.ForbiddenException;
import vn.phongtroxanh.backend.common.exception.UnauthorizedException;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Bạn chưa đăng nhập hoặc phiên đăng nhập không hợp lệ");
        }
        return principal;
    }

    public static UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public static java.util.Optional<UUID> getCurrentUserIdSafely() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal principal) {
            return java.util.Optional.of(principal.getId());
        }
        return java.util.Optional.empty();
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || role == null) return false;
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    public static void assertOwnership(UUID resourceOwnerId, String errorMessage) {
        UserPrincipal currentUser = getCurrentUser();
        if ("ADMIN".equals(currentUser.getRole())) {
            return; // Admins bypass ownership checks for moderation
        }
        if (!currentUser.getId().equals(resourceOwnerId)) {
            throw new ForbiddenException("BOLA_VIOLATION", errorMessage != null ? errorMessage : "Bạn không có quyền thao tác trên tài nguyên này");
        }
    }
}
