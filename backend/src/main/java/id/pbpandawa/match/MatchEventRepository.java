package id.pbpandawa.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    Optional<MatchEvent> findByRequestId(UUID requestId);

    List<MatchEvent> findByMatchIdOrderByCreatedAtAscIdAsc(Long matchId);
}
