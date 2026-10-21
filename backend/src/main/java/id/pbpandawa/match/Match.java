package id.pbpandawa.match;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MatchStatus status;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "shuttlecock_count", nullable = false)
    private int shuttlecockCount;

    /** Price per shuttlecock captured when the match starts; never changed afterwards. */
    @Column(name = "shuttlecock_price", precision = 12, scale = 0)
    private BigDecimal shuttlecockPrice;

    /** Local (club timezone) date of completion; the basis of every report. */
    @Column(name = "play_date")
    private LocalDate playDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "winner_team")
    private Short winnerTeam;

    @Column(length = 60)
    private String score;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "started_by")
    private Long startedBy;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("team asc, slot asc")
    @BatchSize(size = 50)
    private List<MatchPlayer> players = new ArrayList<>();

    protected Match() {
    }

    public Match(Long createdBy, int queuePosition) {
        this.createdBy = createdBy;
        this.status = MatchStatus.WAITING;
        this.queuePosition = queuePosition;
    }

    public void addPlayer(id.pbpandawa.player.Player player, int team, int slot) {
        players.add(new MatchPlayer(this, player, (short) team, (short) slot));
    }

    public List<MatchPlayer> team(int team) {
        return players.stream().filter(p -> p.getTeam() == team).toList();
    }

    public boolean hasPlayer(Long playerId) {
        return players.stream().anyMatch(p -> p.getPlayer().getId().equals(playerId));
    }

    /**
     * Shuttlecock cost = number of players x price snapshot: every player pays the price once per game, regardless of
     * how many shuttlecocks were used (null price before start → current price is used for preview only).
     */
    public BigDecimal shuttlecockCost() {
        return shuttlecockPrice == null ? null : shuttlecockPrice.multiply(BigDecimal.valueOf(players.size()));
    }

    public Long durationSeconds(Instant now) {
        if (startedAt == null) {
            return null;
        }
        Instant end = completedAt != null ? completedAt : (cancelledAt != null ? cancelledAt : now);
        return Duration.between(startedAt, end).getSeconds();
    }

    public void start(Instant now, Long userId, BigDecimal priceSnapshot) {
        this.status = MatchStatus.IN_PROGRESS;
        this.queuePosition = null;
        this.startedAt = now;
        this.startedBy = userId;
        this.shuttlecockPrice = priceSnapshot;
    }

    public void complete(Instant now, LocalDate playDate, Long userId) {
        this.status = MatchStatus.COMPLETED;
        this.completedAt = now;
        this.completedBy = userId;
        this.playDate = playDate;
    }

    public void cancel(Instant now, Long userId, String reason) {
        this.status = MatchStatus.CANCELLED;
        this.queuePosition = null;
        this.cancelledAt = now;
        this.cancelledBy = userId;
        this.cancelReason = reason;
    }

    public Long getId() { return id; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public Integer getQueuePosition() { return queuePosition; }
    public void setQueuePosition(Integer queuePosition) { this.queuePosition = queuePosition; }
    public int getShuttlecockCount() { return shuttlecockCount; }
    public void setShuttlecockCount(int shuttlecockCount) { this.shuttlecockCount = shuttlecockCount; }
    public BigDecimal getShuttlecockPrice() { return shuttlecockPrice; }
    public LocalDate getPlayDate() { return playDate; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public Short getWinnerTeam() { return winnerTeam; }
    public void setWinnerTeam(Short winnerTeam) { this.winnerTeam = winnerTeam; }
    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getCreatedBy() { return createdBy; }
    public Long getStartedBy() { return startedBy; }
    public Long getCompletedBy() { return completedBy; }
    public Long getCancelledBy() { return cancelledBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<MatchPlayer> getPlayers() { return players; }
}
