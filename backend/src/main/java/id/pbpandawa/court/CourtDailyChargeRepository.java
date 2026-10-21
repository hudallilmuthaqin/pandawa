package id.pbpandawa.court;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CourtDailyChargeRepository extends JpaRepository<CourtDailyCharge, Long> {

    /**
     * Creates the court charge for a date exactly once. Concurrent completions on
     * the same date are safe thanks to the UNIQUE(play_date) constraint.
     */
    @Modifying
    @Query(value = """
            INSERT INTO court_daily_charges (play_date, amount, split_method)
            VALUES (:playDate, :amount, :splitMethod)
            ON CONFLICT (play_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(LocalDate playDate, BigDecimal amount, String splitMethod);

    List<CourtDailyCharge> findByPlayDateBetweenOrderByPlayDate(LocalDate from, LocalDate to);
}
