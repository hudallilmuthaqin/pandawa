package id.pbpandawa.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:entityType = '' or a.entityType = :entityType)
              and (:action = '' or a.action = :action)
            order by a.createdAt desc, a.id desc
            """)
    Page<AuditLog> search(String entityType, String action, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
