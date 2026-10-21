package id.pbpandawa.security;

import id.pbpandawa.user.Role;
import java.util.Set;

/** Authenticated principal, rebuilt from the database on every request. */
public record AuthUser(Long id, String username, Role role, Long playerId, Set<Permission> permissions) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean has(Permission permission) {
        return isAdmin() || permissions.contains(permission);
    }
}
