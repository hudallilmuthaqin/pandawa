package id.pbpandawa.match;

import id.pbpandawa.audit.AuditService;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.common.Money;
import id.pbpandawa.common.PageResponse;
import id.pbpandawa.court.CourtDailyChargeRepository;
import id.pbpandawa.match.MatchDtos.CompleteRequest;
import id.pbpandawa.match.MatchDtos.CorrectionRequest;
import id.pbpandawa.match.MatchDtos.MatchDetailResponse;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchDtos.ShuttlecockRequest;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.security.Permission;
import id.pbpandawa.settings.ClubSettings;
import id.pbpandawa.settings.SettingsService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Match lifecycle: start → record shuttlecocks → complete (or cancel).
 * All mutations run in a transaction with a pessimistic row lock on the match;
 * starting additionally takes the queue lock. A partial unique index in the
 * database guarantees at most one IN_PROGRESS match even if application
 * checks were bypassed.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);
    private static final int MAX_SHUTTLECOCKS = 99;

    private final MatchRepository matchRepository;
    private final MatchEventRepository eventRepository;
    private final CourtDailyChargeRepository courtRepository;
    private final SettingsService settingsService;
    private final QueueService queueService;
    private final AuditService auditService;
    private final MatchMapper mapper;
    private final Clock clock;

    public MatchService(MatchRepository matchRepository, MatchEventRepository eventRepository,
                        CourtDailyChargeRepository courtRepository, SettingsService settingsService,
                        QueueService queueService, AuditService auditService, MatchMapper mapper, Clock clock) {
        this.matchRepository = matchRepository;
        this.eventRepository = eventRepository;
        this.courtRepository = courtRepository;
        this.settingsService = settingsService;
        this.queueService = queueService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public MatchDetailResponse detail(Long id) {
        Match m = find(id);
        AuthUser me = CurrentUser.get();
        if (!me.isAdmin() && m.getStatus() == MatchStatus.COMPLETED && (me.playerId() == null || !m.hasPlayer(me.playerId()))
                && !me.has(Permission.REPORT_VIEW)) {
            // Completed history of other players is personal data for non-admins.
            throw BusinessException.forbidden("Anda hanya dapat melihat detail game yang Anda ikuti");
        }
        return new MatchDetailResponse(mapper.toResponse(m),
                mapper.toEventResponses(eventRepository.findByMatchIdOrderByCreatedAtAscIdAsc(id)), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<MatchResponse> current() {
        return matchRepository.findFirstByStatus(MatchStatus.IN_PROGRESS).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> list(MatchStatus status, int page, int size) {
        Specification<Match> spec = (root, q, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
        Page<Match> result = matchRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(result, mapper::toResponses);
    }

    public record HistoryFilter(LocalDate from, LocalDate to, Long playerId, String q) {
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> history(HistoryFilter filter, int page, int size) {
        Page<Match> result = matchRepository.findAll(historySpec(filter),
                PageRequest.of(Math.max(page, 0), clampSize(size),
                        Sort.by(Sort.Direction.DESC, "completedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResponse.of(result, mapper::toResponses);
    }

    /** Unpaged (bounded) variant for CSV export. */
    @Transactional(readOnly = true)
    public List<MatchResponse> historyAll(HistoryFilter filter) {
        return mapper.toResponses(matchRepository.findAll(historySpec(filter),
                Sort.by(Sort.Direction.ASC, "completedAt").and(Sort.by(Sort.Direction.ASC, "id"))));
    }

    private Specification<Match> historySpec(HistoryFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), MatchStatus.COMPLETED));
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("playDate"), f.from()));
            }
            if (f.to() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("playDate"), f.to()));
            }
            if (f.playerId() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                var mp = sq.from(MatchPlayer.class);
                sq.select(mp.get("match").get("id"))
                        .where(cb.equal(mp.get("player").get("id"), f.playerId()));
                ps.add(root.get("id").in(sq));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                Subquery<Long> sq = query.subquery(Long.class);
                var mp = sq.from(MatchPlayer.class);
                var player = mp.join("player", JoinType.INNER);
                sq.select(mp.get("match").get("id"))
                        .where(cb.or(cb.like(cb.lower(player.get("nickname")), like),
                                cb.like(cb.lower(player.get("fullName")), like)));
                ps.add(root.get("id").in(sq));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    // ------------------------------------------------------------------ commands

    /** Starts the front (READY) match of the queue. */
    @Transactional
    public MatchResponse startNext() {
        settingsService.lockForUpdate();
        Match next = matchRepository.findFirstByStatus(MatchStatus.READY)
                .orElseThrow(() -> BusinessException.conflict("QUEUE_EMPTY", "Tidak ada pertandingan di antrean"));
        return start(next.getId());
    }

    @Transactional
    public MatchResponse start(Long id) {
        AuthUser me = CurrentUser.get();
        CurrentUser.require(Permission.MATCH_START);
        ClubSettings settings = settingsService.lockForUpdate();
        if (matchRepository.existsByStatus(MatchStatus.IN_PROGRESS)) {
            throw BusinessException.conflict("MATCH_ALREADY_IN_PROGRESS",
                    "Masih ada pertandingan yang sedang berlangsung. Selesaikan dulu sebelum memulai yang baru.");
        }
        Match match = matchRepository.lockById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        if (!match.getStatus().isQueued()) {
            throw BusinessException.conflict("INVALID_STATUS",
                    "Pertandingan ini berstatus " + match.getStatus() + " dan tidak dapat dimulai");
        }
        // Non-admins may only start the front of the queue; admins may pick any queued match.
        if (match.getStatus() != MatchStatus.READY && !me.isAdmin()) {
            throw BusinessException.conflict("NOT_FRONT_OF_QUEUE", "Hanya pertandingan terdepan yang dapat dimulai");
        }
        if (match.getPlayers().size() != 4) {
            throw BusinessException.conflict("INVALID_PLAYERS", "Pertandingan harus berisi tepat 4 pemain");
        }
        match.getPlayers().stream().filter(mp -> !mp.getPlayer().isActive()).findFirst().ifPresent(mp -> {
            throw BusinessException.conflict("PLAYER_INACTIVE",
                    mp.getPlayer().getNickname() + " sedang nonaktif. Ubah pasangan atau aktifkan pemain.");
        });

        Instant now = Instant.now(clock);
        match.start(now, me.id(), Money.normalize(settings.getShuttlecockPrice()));
        matchRepository.flush(); // unique partial index enforces single IN_PROGRESS here
        queueService.renumber();
        eventRepository.save(new MatchEvent(id, MatchEventType.STARTED, null, 0, null, null, me.id(), now));
        auditService.record("MATCH_STARTED", "MATCH", id, "Game dimulai: " + QueueService.label(match),
                Map.of("shuttlecockPrice", match.getShuttlecockPrice()));
        return mapper.toResponse(match);
    }

    @Transactional
    public MatchResponse recordShuttlecock(Long id, ShuttlecockRequest req) {
        AuthUser me = CurrentUser.get();
        int delta = req.delta();
        if (delta == 0) {
            throw BusinessException.badRequest("INVALID_DELTA", "Perubahan jumlah kok tidak boleh 0");
        }
        CurrentUser.require(delta > 0 ? Permission.SHUTTLE_RECORD : Permission.SHUTTLE_CORRECT);

        Match match = matchRepository.lockById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        // Idempotency: checked after acquiring the row lock so a concurrent retry sees the committed event.
        Optional<MatchEvent> existing = eventRepository.findByRequestId(req.requestId());
        if (existing.isPresent()) {
            if (!existing.get().getMatchId().equals(id)) {
                throw BusinessException.conflict("REQUEST_ID_REUSED", "requestId sudah dipakai untuk pertandingan lain");
            }
            log.info("Duplicate shuttlecock request {} ignored for match {}", req.requestId(), id);
            return mapper.toResponse(match);
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw BusinessException.conflict("INVALID_STATUS", "Kok hanya dapat dicatat saat game berlangsung");
        }
        int after = match.getShuttlecockCount() + delta;
        if (after < 0) {
            throw BusinessException.badRequest("NEGATIVE_SHUTTLECOCK", "Jumlah kok tidak boleh kurang dari 0");
        }
        if (after > MAX_SHUTTLECOCKS) {
            throw BusinessException.badRequest("TOO_MANY_SHUTTLECOCKS", "Jumlah kok melebihi batas wajar");
        }
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().trim();
        if (delta < 0 && reason == null && settingsService.current().isCorrectionRequiresReason() && !me.isAdmin()) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Alasan koreksi wajib diisi");
        }
        int before = match.getShuttlecockCount();
        match.setShuttlecockCount(after);
        Instant now = Instant.now(clock);
        eventRepository.save(new MatchEvent(id, delta > 0 ? MatchEventType.SHUTTLE_ADDED : MatchEventType.SHUTTLE_CORRECTED,
                delta, after, reason, req.requestId(), me.id(), now));
        if (delta < 0) {
            auditService.record("SHUTTLE_CORRECTED", "MATCH", id,
                    "Koreksi kok game #" + id + ": " + before + " → " + after,
                    reason == null ? Map.of() : Map.of("reason", reason));
        }
        matchRepository.flush();
        return mapper.toResponse(match);
    }

    @Transactional
    public MatchResponse complete(Long id, CompleteRequest req) {
        AuthUser me = CurrentUser.get();
        CurrentUser.require(Permission.MATCH_COMPLETE);
        ClubSettings settings = settingsService.current();
        Match match = matchRepository.lockById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw BusinessException.conflict("ALREADY_COMPLETED", "Pertandingan ini sudah diselesaikan");
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw BusinessException.conflict("INVALID_STATUS", "Hanya game yang sedang berlangsung yang dapat diselesaikan");
        }
        if (req.expectedShuttlecockCount() != null && req.expectedShuttlecockCount() != match.getShuttlecockCount()) {
            throw BusinessException.conflict("SHUTTLECOCK_COUNT_CHANGED",
                    "Jumlah kok sudah berubah menjadi " + match.getShuttlecockCount() + ". Periksa kembali sebelum menyelesaikan.");
        }
        if (settings.isScoreEnabled()) {
            match.setWinnerTeam(req.winnerTeam() == null ? null : req.winnerTeam().shortValue());
            match.setScore(blankToNull(req.score()));
        }
        match.setNotes(blankToNull(req.notes()));

        Instant now = Instant.now(clock);
        LocalDate playDate = LocalDate.ofInstant(now, settings.zoneId());
        match.complete(now, playDate, me.id());
        matchRepository.flush();

        // Court fee: exactly one charge per local playing date, regardless of the number of games.
        int created = courtRepository.insertIfAbsent(playDate, Money.normalize(settings.getCourtPricePerDay()),
                settings.getCourtSplitMethod().name());
        eventRepository.save(new MatchEvent(id, MatchEventType.COMPLETED, null, match.getShuttlecockCount(),
                null, null, me.id(), now));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("shuttlecocks", match.getShuttlecockCount());
        details.put("shuttlecockPrice", match.getShuttlecockPrice());
        details.put("shuttlecockCost", match.shuttlecockCost());
        details.put("playDate", playDate.toString());
        details.put("courtChargeCreated", created > 0);
        auditService.record("MATCH_COMPLETED", "MATCH", id, "Game selesai: " + QueueService.label(match)
                + " (" + match.getShuttlecockCount() + " kok)", details);
        return mapper.toResponse(match);
    }

    @Transactional
    public MatchResponse cancel(Long id, String reason) {
        AuthUser me = CurrentUser.get();
        Match match = matchRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        if (match.getStatus().isQueued()) {
            queueService.remove(id, reason);
            return mapper.toResponse(find(id));
        }
        CurrentUser.require(Permission.MATCH_CANCEL);
        match = matchRepository.lockById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw BusinessException.conflict("INVALID_STATUS", "Game yang sudah selesai/dibatalkan tidak dapat dibatalkan");
        }
        String why = reason == null || reason.isBlank() ? "Dibatalkan admin" : reason.trim();
        Instant now = Instant.now(clock);
        match.cancel(now, me.id(), why);
        matchRepository.flush();
        eventRepository.save(new MatchEvent(id, MatchEventType.CANCELLED, null, match.getShuttlecockCount(), why,
                null, me.id(), now));
        auditService.record("MATCH_CANCELLED", "MATCH", id, "Game dibatalkan: " + QueueService.label(match),
                Map.of("reason", why, "shuttlecocks", match.getShuttlecockCount()));
        return mapper.toResponse(match);
    }

    /** Auditable correction of a completed match (admin). The price snapshot is never changed. */
    @Transactional
    public MatchResponse correctCompleted(Long id, CorrectionRequest req) {
        AuthUser me = CurrentUser.get();
        CurrentUser.require(Permission.MATCH_CORRECT);
        Match match = matchRepository.lockById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        if (match.getStatus() != MatchStatus.COMPLETED) {
            throw BusinessException.conflict("INVALID_STATUS", "Koreksi ini hanya untuk game yang sudah selesai");
        }
        int before = match.getShuttlecockCount();
        int after = req.shuttlecockCount();
        if (before == after) {
            return mapper.toResponse(match);
        }
        match.setShuttlecockCount(after);
        matchRepository.flush();
        Instant now = Instant.now(clock);
        eventRepository.save(new MatchEvent(id, MatchEventType.COMPLETED_CORRECTION, after - before, after,
                req.reason().trim(), null, me.id(), now));
        auditService.record("COMPLETED_MATCH_CORRECTED", "MATCH", id,
                "Koreksi game selesai #" + id + ": kok " + before + " → " + after,
                Map.of("reason", req.reason().trim(), "from", before, "to", after));
        return mapper.toResponse(match);
    }

    private Match find(Long id) {
        return matchRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
