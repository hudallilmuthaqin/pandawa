package id.pbpandawa.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import org.hibernate.annotations.UpdateTimestamp;

/** Singleton row (id = 1). Its row lock doubles as the queue/court mutex. */
@Entity
@Table(name = "club_settings")
public class ClubSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "club_name", nullable = false, length = 80)
    private String clubName;

    @Column(name = "shuttlecock_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal shuttlecockPrice;

    @Column(name = "court_price_per_day", nullable = false, precision = 12, scale = 0)
    private BigDecimal courtPricePerDay;

    @Column(name = "court_count", nullable = false)
    private short courtCount;

    @Column(nullable = false, length = 40)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "court_split_method", nullable = false, length = 20)
    private CourtSplitMethod courtSplitMethod;

    @Column(name = "self_registration_enabled", nullable = false)
    private boolean selfRegistrationEnabled;

    @Column(name = "score_enabled", nullable = false)
    private boolean scoreEnabled;

    @Column(name = "correction_requires_reason", nullable = false)
    private boolean correctionRequiresReason;

    @Column(name = "user_export_enabled", nullable = false)
    private boolean userExportEnabled;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ClubSettings() {
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public Short getId() { return id; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
    public BigDecimal getShuttlecockPrice() { return shuttlecockPrice; }
    public void setShuttlecockPrice(BigDecimal shuttlecockPrice) { this.shuttlecockPrice = shuttlecockPrice; }
    public BigDecimal getCourtPricePerDay() { return courtPricePerDay; }
    public void setCourtPricePerDay(BigDecimal courtPricePerDay) { this.courtPricePerDay = courtPricePerDay; }
    public short getCourtCount() { return courtCount; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public CourtSplitMethod getCourtSplitMethod() { return courtSplitMethod; }
    public void setCourtSplitMethod(CourtSplitMethod courtSplitMethod) { this.courtSplitMethod = courtSplitMethod; }
    public boolean isSelfRegistrationEnabled() { return selfRegistrationEnabled; }
    public void setSelfRegistrationEnabled(boolean v) { this.selfRegistrationEnabled = v; }
    public boolean isScoreEnabled() { return scoreEnabled; }
    public void setScoreEnabled(boolean scoreEnabled) { this.scoreEnabled = scoreEnabled; }
    public boolean isCorrectionRequiresReason() { return correctionRequiresReason; }
    public void setCorrectionRequiresReason(boolean v) { this.correctionRequiresReason = v; }
    public boolean isUserExportEnabled() { return userExportEnabled; }
    public void setUserExportEnabled(boolean userExportEnabled) { this.userExportEnabled = userExportEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
