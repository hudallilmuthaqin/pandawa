package id.pbpandawa.settings;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ClubSettingsRepository extends JpaRepository<ClubSettings, Short> {

    /** SELECT ... FOR UPDATE on the singleton row: serialises queue & court operations. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClubSettings s where s.id = :id")
    Optional<ClubSettings> lockById(Short id);
}
