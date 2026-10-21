package id.pbpandawa.security;

import id.pbpandawa.user.Role;
import id.pbpandawa.user.RolePermission;
import id.pbpandawa.user.RolePermissionRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private final RolePermissionRepository repository;

    public PermissionService(RolePermissionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Set<Permission> permissionsOf(Role role) {
        if (role == Role.ADMIN) {
            return EnumSet.allOf(Permission.class);
        }
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        for (RolePermission rp : repository.findByRole(role)) {
            // Never trust the table blindly: admin-only permissions are not grantable.
            if (rp.getPermission().isGrantableToUser()) {
                result.add(rp.getPermission());
            }
        }
        return result;
    }
}
