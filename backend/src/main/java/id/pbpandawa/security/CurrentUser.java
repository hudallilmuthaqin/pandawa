package id.pbpandawa.security;

import id.pbpandawa.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Silakan login terlebih dahulu");
    }

    public static void require(Permission permission) {
        if (!get().has(permission)) {
            throw BusinessException.forbidden("Anda tidak memiliki izin untuk aksi ini");
        }
    }
}
