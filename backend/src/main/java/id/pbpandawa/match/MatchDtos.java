package id.pbpandawa.match;

import id.pbpandawa.player.PlayerDtos.PlayerRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MatchDtos {

    private MatchDtos() {
    }

    public record CreateMatchRequest(
            @NotNull @Size(min = 2, max = 2, message = "Pasangan 1 harus berisi 2 pemain") List<@NotNull Long> team1PlayerIds,
            @NotNull @Size(min = 2, max = 2, message = "Pasangan 2 harus berisi 2 pemain") List<@NotNull Long> team2PlayerIds) {
    }

    public record MoveRequest(@NotNull @Min(1) Integer position) {
    }

    public record ShuttlecockRequest(
            @NotNull(message = "delta wajib diisi") @Min(value = -5) @Max(value = 5) Integer delta,
            @NotNull(message = "requestId wajib diisi untuk mencegah pencatatan ganda") UUID requestId,
            @Size(max = 200) String reason) {
    }

    public record CompleteRequest(
            @Min(0) Integer expectedShuttlecockCount,
            @Min(1) @Max(2) Integer winnerTeam,
            @Size(max = 60) String score,
            @Size(max = 500) String notes) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    public record CorrectionRequest(
            @NotNull @Min(0) @Max(99) Integer shuttlecockCount,
            @NotNull @Size(min = 3, max = 200, message = "Alasan koreksi wajib diisi (min. 3 karakter)") String reason) {
    }

    public record MatchResponse(
            Long id,
            MatchStatus status,
            Integer queuePosition,
            List<PlayerRef> team1,
            List<PlayerRef> team2,
            int shuttlecockCount,
            /** Snapshot for started matches; for queued matches this is the current price (preview). */
            BigDecimal shuttlecockPrice,
            boolean priceSnapshot,
            BigDecimal shuttlecockCost,
            LocalDate playDate,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            Long durationSeconds,
            Short winnerTeam,
            String score,
            String notes,
            String cancelReason,
            String createdBy,
            String startedBy,
            String completedBy,
            String cancelledBy,
            Long createdById,
            long version) {
    }

    public record QueueResponse(Instant serverTime, MatchResponse current, MatchResponse next, List<MatchResponse> queue) {
    }

    public record MatchEventResponse(Long id, MatchEventType type, Integer delta, Integer countAfter, String reason,
                                     String actor, Instant createdAt) {
    }

    public record MatchDetailResponse(MatchResponse match, List<MatchEventResponse> events, Instant serverTime) {
    }
}
