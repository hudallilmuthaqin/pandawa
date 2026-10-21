package id.pbpandawa.audit;

import id.pbpandawa.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Log")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditLogResponse(Long id, Instant createdAt, String actorUsername, String action,
                                   String entityType, Long entityId, String summary, String details) {
        public static AuditLogResponse of(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getActorUsername(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getSummary(), a.getDetails());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(@RequestParam(defaultValue = "") String entityType,
                                               @RequestParam(defaultValue = "") String action,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        var result = repository.search(entityType, action, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(result, list -> list.stream().map(AuditLogResponse::of).toList());
    }
}
