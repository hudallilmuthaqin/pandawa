package id.pbpandawa.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u left join fetch u.player where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("select u from User u left join fetch u.player where u.id = :id")
    Optional<User> findWithPlayerById(Long id);

    @Query("select u from User u left join fetch u.player order by u.username")
    List<User> findAllWithPlayer();

    Optional<User> findByPlayerId(Long playerId);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndEnabledTrue(Role role);
}
