package id.pbpandawa.report;

import id.pbpandawa.audit.AuditController.AuditLogResponse;
import id.pbpandawa.audit.AuditLogRepository;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.common.Money;
import id.pbpandawa.court.CourtDailyCharge;
import id.pbpandawa.court.CourtDailyChargeRepository;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchService;
import id.pbpandawa.match.MatchService.HistoryFilter;
import id.pbpandawa.match.MatchStatus;
import id.pbpandawa.match.QueueService;
import id.pbpandawa.player.Player;
import id.pbpandawa.player.PlayerDtos.PlayerRef;
import id.pbpandawa.player.PlayerRepository;
import id.pbpandawa.player.PlayerStatus;
import id.pbpandawa.report.ReportDtos.CountPoint;
import id.pbpandawa.report.ReportDtos.DailyReport;
import id.pbpandawa.report.ReportDtos.DailyRow;
import id.pbpandawa.report.ReportDtos.DashboardReport;
import id.pbpandawa.report.ReportDtos.MonthlyReport;
import id.pbpandawa.report.ReportDtos.PlayerRow;
import id.pbpandawa.report.ReportDtos.PlayerStatistics;
import id.pbpandawa.report.ReportDtos.RangeReport;
import id.pbpandawa.report.ReportDtos.Summary;
import id.pbpandawa.settings.ClubSettings;
import id.pbpandawa.settings.CourtSplitMethod;
import id.pbpandawa.settings.SettingsService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All report numbers are computed from the database (completed matches and
 * court_daily_charges) for an inclusive range of local playing dates. Daily,
 * monthly, range and dashboard reports share the same computation, so their
 * numbers are always consistent.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    public static final int MAX_RANGE_DAYS = 366;
    static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);

    public enum Period { TODAY, WEEK, MONTH, CUSTOM }

    private final EntityManager em;
    private final CourtDailyChargeRepository courtRepository;
    private final PlayerRepository playerRepository;
    private final SettingsService settingsService;
    private final QueueService queueService;
    private final MatchService matchService;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public ReportService(EntityManager em, CourtDailyChargeRepository courtRepository,
                         PlayerRepository playerRepository, SettingsService settingsService,
                         QueueService queueService, MatchService matchService,
                         AuditLogRepository auditLogRepository, Clock clock) {
        this.em = em;
        this.courtRepository = courtRepository;
        this.playerRepository = playerRepository;
        this.settingsService = settingsService;
        this.queueService = queueService;
        this.matchService = matchService;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------- period helpers

    public LocalDate today() {
        return LocalDate.now(clock.withZone(settingsService.current().zoneId()));
    }

    public record DateRange(LocalDate from, LocalDate to) {
    }

    /** Resolves a period preset (or a custom range) to an inclusive local-date range and validates it. */
    public DateRange resolve(Period period, LocalDate from, LocalDate to) {
        LocalDate today = today();
        DateRange range = switch (period == null ? Period.CUSTOM : period) {
            case TODAY -> new DateRange(today, today);
            case WEEK -> new DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case MONTH -> new DateRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
            case CUSTOM -> {
                if (from == null && to == null) {
                    yield new DateRange(today, today);
                }
                if (from == null || to == null) {
                    throw BusinessException.badRequest("INVALID_RANGE", "Tanggal mulai dan tanggal akhir wajib diisi");
                }
                yield new DateRange(from, to);
            }
        };
        validate(range.from(), range.to());
        return range;
    }

    public static void validate(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.badRequest("INVALID_RANGE", "Tanggal akhir tidak boleh lebih awal dari tanggal mulai");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw BusinessException.badRequest("RANGE_TOO_LONG", "Rentang maksimal " + MAX_RANGE_DAYS + " hari");
        }
    }

    // ------------------------------------------------------------- core computation

    /** One row per (completed match, participating player). */
    private record Participation(Long matchId, LocalDate playDate, int shuttlecocks, BigDecimal price,
                                 Short winnerTeam, Long playerId, short team) {
    }

    /** Every player pays the price snapshot once per game, so a game costs players x price (shuttlecock count aside). */
    private record MatchFact(Long id, LocalDate playDate, int shuttlecocks, BigDecimal price, int players) {
        BigDecimal cost() {
            return Money.times(price, players);
        }
    }

    /** Fully computed report data for an inclusive local-date range. */
    record Computation(Summary summary, List<DailyRow> daily, List<PlayerRow> players) {
    }

    @Transactional(readOnly = true)
    Computation compute(LocalDate from, LocalDate to) {
        List<Participation> rows = em.createQuery("""
                        select m.id, m.playDate, m.shuttlecockCount, m.shuttlecockPrice, m.winnerTeam, mp.player.id, mp.team
                        from MatchPlayer mp join mp.match m
                        where m.status = :status and m.playDate between :from and :to
                        """, Object[].class)
                .setParameter("status", MatchStatus.COMPLETED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList().stream()
                .map(r -> new Participation((Long) r[0], (LocalDate) r[1], (Integer) r[2], (BigDecimal) r[3],
                        (Short) r[4], (Long) r[5], (Short) r[6]))
                .toList();

        // Distinct completed matches: each match's shuttlecock cost is counted exactly once.
        Map<Long, Long> playerCounts = rows.stream().collect(Collectors.groupingBy(Participation::matchId, Collectors.counting()));
        Map<Long, MatchFact> matches = new HashMap<>();
        for (Participation p : rows) {
            matches.putIfAbsent(p.matchId(), new MatchFact(p.matchId(), p.playDate(), p.shuttlecocks(), p.price(),
                    playerCounts.get(p.matchId()).intValue()));
        }

        Map<LocalDate, CourtDailyCharge> charges = courtRepository.findByPlayDateBetweenOrderByPlayDate(from, to)
                .stream().collect(Collectors.toMap(CourtDailyCharge::getPlayDate, Function.identity()));
        ClubSettings settings = settingsService.current();

        // ---- per date
        Map<LocalDate, List<MatchFact>> matchesByDate = matches.values().stream()
                .collect(Collectors.groupingBy(MatchFact::playDate, TreeMap::new, Collectors.toList()));
        Map<LocalDate, TreeSet<Long>> playersByDate = new TreeMap<>();
        for (Participation p : rows) {
            playersByDate.computeIfAbsent(p.playDate(), d -> new TreeSet<>()).add(p.playerId());
        }

        List<DailyRow> daily = new ArrayList<>();
        Map<Long, BigDecimal> courtShare = new HashMap<>();
        for (Map.Entry<LocalDate, List<MatchFact>> e : matchesByDate.entrySet()) {
            LocalDate date = e.getKey();
            List<MatchFact> dayMatches = e.getValue();
            CourtDailyCharge charge = charges.get(date);
            BigDecimal courtCost;
            CourtSplitMethod method;
            if (charge != null) {
                courtCost = Money.normalize(charge.getAmount());
                method = charge.getSplitMethod();
            } else {
                // Should not happen (charge is created on completion); fall back to current price.
                log.warn("Missing court_daily_charges row for {}; using current price", date);
                courtCost = Money.normalize(settings.getCourtPricePerDay());
                method = settings.getCourtSplitMethod();
            }
            long shuttles = dayMatches.stream().mapToLong(MatchFact::shuttlecocks).sum();
            BigDecimal shuttleCost = dayMatches.stream().map(MatchFact::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
            TreeSet<Long> dayPlayers = playersByDate.getOrDefault(date, new TreeSet<>());
            if (method == CourtSplitMethod.PER_PLAYER) {
                // The charge holds the daily price snapshot; each player of the day pays it in full.
                BigDecimal price = courtCost;
                dayPlayers.forEach(pid -> courtShare.merge(pid, price, BigDecimal::add));
                courtCost = Money.times(price, dayPlayers.size());
            }
            daily.add(new DailyRow(date, dayMatches.size(), dayPlayers.size(), shuttles, shuttleCost, courtCost, method,
                    shuttleCost.add(courtCost)));
            if (method == CourtSplitMethod.EQUAL_SPLIT) {
                CostAllocator.splitEqually(courtCost, dayPlayers)
                        .forEach((pid, share) -> courtShare.merge(pid, share, BigDecimal::add));
            }
        }

        // ---- per player (participation-based allocation)
        Map<Long, List<Participation>> byPlayer = rows.stream().collect(Collectors.groupingBy(Participation::playerId));
        Map<Long, Player> players = byPlayer.isEmpty() ? Map.of()
                : playerRepository.findByIdIn(byPlayer.keySet()).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        List<PlayerRow> playerRows = new ArrayList<>();
        for (Map.Entry<Long, List<Participation>> e : byPlayer.entrySet()) {
            List<Participation> ps = e.getValue();
            Player player = players.get(e.getKey());
            long shuttles = ps.stream().mapToLong(Participation::shuttlecocks).sum();
            BigDecimal shuttleCost = ps.stream().map(p -> Money.normalize(p.price()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal share = courtShare.getOrDefault(e.getKey(), BigDecimal.ZERO);
            long wins = ps.stream().filter(p -> p.winnerTeam() != null && p.winnerTeam() == p.team()).count();
            long losses = ps.stream().filter(p -> p.winnerTeam() != null && p.winnerTeam() != p.team()).count();
            long playDays = ps.stream().map(Participation::playDate).distinct().count();
            playerRows.add(new PlayerRow(e.getKey(), player != null ? player.getNickname() : "#" + e.getKey(),
                    player != null ? player.getFullName() : null, ps.size(), playDays, shuttles, shuttleCost, share,
                    shuttleCost.add(share), wins, losses));
        }
        playerRows.sort(Comparator.comparingLong(PlayerRow::games).reversed()
                .thenComparing(PlayerRow::nickname, String.CASE_INSENSITIVE_ORDER));

        // ---- summary: club totals come from matches and court charges, never from player rows.
        long totalShuttles = matches.values().stream().mapToLong(MatchFact::shuttlecocks).sum();
        BigDecimal shuttleCost = matches.values().stream().map(MatchFact::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal courtCost = daily.stream().map(DailyRow::courtCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        Summary summary = new Summary(matches.size(), byPlayer.size(), totalShuttles, shuttleCost, daily.size(),
                courtCost, shuttleCost.add(courtCost));
        return new Computation(summary, daily, playerRows);
    }

    // ------------------------------------------------------------- public reports

    @Transactional(readOnly = true)
    public RangeReport range(LocalDate from, LocalDate to) {
        validate(from, to);
        Computation c = compute(from, to);
        CourtSplitMethod method = settingsService.current().getCourtSplitMethod();
        return new RangeReport(from, to, c.summary(), c.daily(), c.players(), method, allocationNote(method));
    }

    @Transactional(readOnly = true)
    public DailyReport daily(LocalDate date) {
        LocalDate d = date != null ? date : today();
        Computation c = compute(d, d);
        List<MatchResponse> matches = matchService.historyAll(new HistoryFilter(d, d, null, null));
        DailyRow day = c.daily().isEmpty() ? null : c.daily().get(0);
        return new DailyReport(d, c.summary(), day, c.players(), matches);
    }

    @Transactional(readOnly = true)
    public MonthlyReport monthly(YearMonth month) {
        YearMonth ym = month != null ? month : YearMonth.from(today());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Computation c = compute(from, to);
        BigDecimal avg = c.summary().playDays() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(c.summary().totalGames())
                .divide(BigDecimal.valueOf(c.summary().playDays()), 2, RoundingMode.HALF_UP);
        List<PlayerRow> mostActive = c.players().stream().limit(5).toList();
        return new MonthlyReport(ym.toString(), from, to, c.summary(), avg, c.daily(), c.players(), mostActive);
    }

    @Transactional(readOnly = true)
    public DashboardReport dashboard(LocalDate from, LocalDate to) {
        validate(from, to);
        Computation c = compute(from, to);
        ClubSettings s = settingsService.current();
        var recent = auditLogRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 10))
                .getContent().stream().map(AuditLogResponse::of).toList();
        return new DashboardReport(from, to, playerRepository.count(), playerRepository.countByStatus(PlayerStatus.ACTIVE),
                c.summary().uniquePlayers(), Money.normalize(s.getShuttlecockPrice()),
                Money.normalize(s.getCourtPricePerDay()), s.getCourtSplitMethod(), c.summary(), c.daily(),
                c.players(), queueService.queue(), recent);
    }

    @Transactional(readOnly = true)
    public PlayerStatistics playerStatistics(Long playerId, LocalDate from, LocalDate to) {
        Player player = playerRepository.findById(playerId).orElseThrow(() -> BusinessException.notFound("Pemain"));
        LocalDate today = today();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.with(TemporalAdjusters.lastDayOfMonth());
        validate(f, t);

        PlayerRow period = rowFor(compute(f, t), player);
        PlayerRow allTime = rowFor(compute(EPOCH, today.plusDays(1)), player);

        List<LocalDate> dates = em.createQuery("""
                        select m.playDate from MatchPlayer mp join mp.match m
                        where mp.player.id = :pid and m.status = :status and m.playDate between :from and :to
                        """, LocalDate.class)
                .setParameter("pid", playerId).setParameter("status", MatchStatus.COMPLETED)
                .setParameter("from", f).setParameter("to", t)
                .getResultList();
        Map<String, Long> perDay = new TreeMap<>();
        Map<String, Long> perWeek = new TreeMap<>();
        Map<String, Long> perMonth = new TreeMap<>();
        for (LocalDate d : dates) {
            perDay.merge(d.toString(), 1L, Long::sum);
            perWeek.merge(d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(), 1L, Long::sum);
            perMonth.merge(YearMonth.from(d).toString(), 1L, Long::sum);
        }
        ClubSettings s = settingsService.current();
        List<MatchResponse> recent = matchService.history(new HistoryFilter(null, null, playerId, null), 0, 10).content();
        return new PlayerStatistics(PlayerRef.of(player), f, t, allTime.games(), allTime, period,
                points(perDay), points(perWeek), points(perMonth), s.isScoreEnabled(), s.getCourtSplitMethod(), recent);
    }

    private static PlayerRow rowFor(Computation c, Player player) {
        return c.players().stream().filter(r -> r.playerId().equals(player.getId())).findFirst()
                .orElse(new PlayerRow(player.getId(), player.getNickname(), player.getFullName(), 0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
    }

    private static List<CountPoint> points(Map<String, Long> map) {
        return map.entrySet().stream().map(e -> new CountPoint(e.getKey(), e.getValue())).toList();
    }

    static String allocationNote(CourtSplitMethod method) {
        String base = "Biaya kok pemain = harga kok × jumlah game yang diikuti (setiap pemain bayar sekali per game, "
                + "berapa pun kok yang dipakai). Biaya kok klub per game = jumlah pemain × harga kok.";
        return switch (method) {
            case EQUAL_SPLIT -> base + " Biaya lapangan dibagi rata ke pemain unik pada tanggal tersebut (sisa "
                    + "pembulatan rupiah diberikan ke pemain dengan ID terkecil).";
            case PER_PLAYER -> base + " Setiap pemain yang bermain pada tanggal tersebut membayar harga lapangan per "
                    + "hari secara penuh; biaya lapangan klub = harga × jumlah pemain unik.";
            case CLUB_ONLY -> base + " Biaya lapangan dicatat sebagai pengeluaran klub saja (tidak masuk iuran pribadi).";
        };
    }
}
