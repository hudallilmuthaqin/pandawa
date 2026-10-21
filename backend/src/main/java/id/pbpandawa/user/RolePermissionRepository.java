package id.pbpandawa.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.Key> {

    List<RolePermission> findByRole(Role role);

    @Modifying
    @Query("delete from RolePermission rp where rp.role = :role")
    void deleteByRole(Role role);
}
