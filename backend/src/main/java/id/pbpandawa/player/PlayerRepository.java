package id.pbpandawa.player;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    boolean existsByNicknameIgnoreCase(String nickname);

    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

    long countByStatus(PlayerStatus status);

    List<Player> findByIdIn(Collection<Long> ids);

    @Query("""
            select p from Player p
            where (:status is null or p.status = :status)
              and (:q = '' or lower(p.fullName) like concat('%', :q, '%') or lower(p.nickname) like concat('%', :q, '%'))
            """)
    Page<Player> search(PlayerStatus status, String q, Pageable pageable);
}
