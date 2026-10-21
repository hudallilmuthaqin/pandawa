package id.pbpandawa.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only log of match state changes and shuttlecock movements. */
@Entity
@Table(name = "match_events")
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchEventType type;

    private Integer delta;

    @Column(name = "count_after")
    private Integer countAfter;

    @Column(length = 200)
    private String reason;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchEvent() {
    }

    public MatchEvent(Long matchId, MatchEventType type, Integer delta, Integer countAfter, String reason,
                      UUID requestId, Long actorId, Instant createdAt) {
        this.matchId = matchId;
        this.type = type;
        this.delta = delta;
        this.countAfter = countAfter;
        this.reason = reason;
        this.requestId = requestId;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public MatchEventType getType() { return type; }
    public Integer getDelta() { return delta; }
    public Integer getCountAfter() { return countAfter; }
    public String getReason() { return reason; }
    public UUID getRequestId() { return requestId; }
    public Long getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
