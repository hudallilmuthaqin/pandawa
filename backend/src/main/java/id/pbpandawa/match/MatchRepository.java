package id.pbpandawa.match;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MatchRepository extends JpaRepository<Match, Long>, JpaSpecificationExecutor<Match> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> lockById(Long id);

    List<Match> findByStatusInOrderByQueuePositionAsc(Collection<MatchStatus> statuses);

    Optional<Match> findFirstByStatus(MatchStatus status);

    boolean existsByStatus(MatchStatus status);

    long countByStatus(MatchStatus status);

    @Query("select coalesce(max(m.queuePosition), 0) from Match m where m.status in ('WAITING', 'READY')")
    int maxQueuePosition();

    @Query("""
            select count(mp) from MatchPlayer mp
            where mp.player.id = :playerId and mp.match.status = 'COMPLETED'
            """)
    long countCompletedByPlayer(Long playerId);
}
