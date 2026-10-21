package id.pbpandawa.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.pbpandawa.security.AuthUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit entries inside the caller's transaction, so the change and its audit trail commit together. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Long entityId, String summary, Map<String, ?> details) {
        Long actorId = null;
        String actorName = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            actorId = user.id();
            actorName = user.username();
        }
        String json = null;
        if (details != null && !details.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                json = String.valueOf(details);
            }
        }
        String trimmed = summary.length() > 300 ? summary.substring(0, 300) : summary;
        repository.save(new AuditLog(actorId, actorName, action, entityType, entityId, trimmed, json, Instant.now(clock)));
        log.info("AUDIT {} {}#{} by {}: {}", action, entityType, entityId, actorName, trimmed);
    }
}
