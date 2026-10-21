package id.pbpandawa.court;

import id.pbpandawa.settings.CourtSplitMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Court fee of one playing date (unique per date). Read-only from the JPA side. */
@Entity
@Table(name = "court_daily_charges")
public class CourtDailyCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "play_date", nullable = false, unique = true)
    private LocalDate playDate;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_method", nullable = false, length = 20)
    private CourtSplitMethod splitMethod;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected CourtDailyCharge() {
    }

    public Long getId() { return id; }
    public LocalDate getPlayDate() { return playDate; }
    public BigDecimal getAmount() { return amount; }
    public CourtSplitMethod getSplitMethod() { return splitMethod; }
    public Instant getCreatedAt() { return createdAt; }
}
