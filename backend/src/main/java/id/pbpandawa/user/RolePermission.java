package id.pbpandawa.user;

import id.pbpandawa.security.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermission.Key.class)
public class RolePermission {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Role role;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Permission permission;

    protected RolePermission() {
    }

    public RolePermission(Role role, Permission permission) {
        this.role = role;
        this.permission = permission;
    }

    public Role getRole() { return role; }
    public Permission getPermission() { return permission; }

    public static class Key implements Serializable {
        private Role role;
        private Permission permission;

        public Key() {
        }

        public Key(Role role, Permission permission) {
            this.role = role;
            this.permission = permission;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.role == role && k.permission == permission;
        }

        @Override
        public int hashCode() {
            return Objects.hash(role, permission);
        }
    }
}
