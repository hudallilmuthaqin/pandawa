package id.pbpandawa.report;

import id.pbpandawa.audit.AuditController.AuditLogResponse;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchDtos.QueueResponse;
import id.pbpandawa.player.PlayerDtos.PlayerRef;
import id.pbpandawa.settings.CourtSplitMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * Club totals. shuttlecockCost is computed once per completed match
     * (players x price snapshot) — never from player rows.
     * totalExpense = shuttlecockCost + courtCost.
     */
    public record Summary(
            long totalGames,
            long uniquePlayers,
            long totalShuttlecocks,
            BigDecimal shuttlecockCost,
            long playDays,
            BigDecimal courtCost,
            BigDecimal totalExpense) {
    }

    public record DailyRow(
            LocalDate date,
            long games,
            long uniquePlayers,
            long shuttlecocks,
            BigDecimal shuttlecockCost,
            BigDecimal courtCost,
            CourtSplitMethod courtSplitMethod,
            BigDecimal totalExpense) {
    }

    /**
     * Personal usage report. shuttlecocks is the full usage of every match the player took part in;
     * shuttlecockCost is the price snapshot once per match played (every player pays the price per game).
     */
    public record PlayerRow(
            Long playerId,
            String nickname,
            String fullName,
            long games,
            long playDays,
            long shuttlecocks,
            BigDecimal shuttlecockCost,
            BigDecimal courtShare,
            BigDecimal totalEstimate,
            long wins,
            long losses) {
    }

    public record RangeReport(
            LocalDate from,
            LocalDate to,
            Summary summary,
            List<DailyRow> daily,
            List<PlayerRow> players,
            CourtSplitMethod currentCourtSplitMethod,
            String allocationNote) {
    }

    public record DailyReport(
            LocalDate date,
            Summary summary,
            DailyRow day,
            List<PlayerRow> players,
            List<MatchResponse> matches) {
    }

    public record MonthlyReport(
            String month,
            LocalDate from,
            LocalDate to,
            Summary summary,
            BigDecimal averageGamesPerPlayDay,
            List<DailyRow> daily,
            List<PlayerRow> players,
            List<PlayerRow> mostActive) {
    }

    public record DashboardReport(
            LocalDate from,
            LocalDate to,
            long totalRegisteredPlayers,
            long activeRegisteredPlayers,
            long activePlayersInPeriod,
            BigDecimal currentShuttlecockPrice,
            BigDecimal currentCourtPricePerDay,
            CourtSplitMethod courtSplitMethod,
            Summary summary,
            List<DailyRow> daily,
            List<PlayerRow> players,
            QueueResponse queue,
            List<AuditLogResponse> recentActivity) {
    }

    public record CountPoint(String key, long games) {
    }

    public record PlayerStatistics(
            PlayerRef player,
            LocalDate from,
            LocalDate to,
            long allTimeGames,
            PlayerRow allTime,
            PlayerRow period,
            List<CountPoint> perDay,
            List<CountPoint> perWeek,
            List<CountPoint> perMonth,
            boolean scoreEnabled,
            CourtSplitMethod courtSplitMethod,
            List<MatchResponse> recentMatches) {
    }
}
