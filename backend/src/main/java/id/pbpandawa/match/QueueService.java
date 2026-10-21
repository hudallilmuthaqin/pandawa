package id.pbpandawa.match;

import id.pbpandawa.audit.AuditService;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.match.MatchDtos.CreateMatchRequest;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchDtos.QueueResponse;
import id.pbpandawa.player.Player;
import id.pbpandawa.player.PlayerService;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.security.Permission;
import id.pbpandawa.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-court queue. Every mutation first takes the club-wide row lock
 * (club_settings FOR UPDATE), so concurrent admins/users are serialised and
 * the queue is always renumbered 1..n with exactly one READY match at the front.
 */
@Service
public class QueueService {

    static final List<MatchStatus> QUEUED = List.of(MatchStatus.READY, MatchStatus.WAITING);

    private final MatchRepository matchRepository;
    private final MatchEventRepository eventRepository;
    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final MatchMapper mapper;
    private final Clock clock;

    public QueueService(MatchRepository matchRepository, MatchEventRepository eventRepository,
                        PlayerService playerService, SettingsService settingsService, AuditService auditService,
                        MatchMapper mapper, Clock clock) {
        this.matchRepository = matchRepository;
        this.eventRepository = eventRepository;
        this.playerService = playerService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QueueResponse queue() {
        List<Match> queued = matchRepository.findByStatusInOrderByQueuePositionAsc(QUEUED);
        Match current = matchRepository.findFirstByStatus(MatchStatus.IN_PROGRESS).orElse(null);
        List<MatchResponse> queue = mapper.toResponses(queued);
        return new QueueResponse(Instant.now(clock), mapper.toResponse(current),
                queue.isEmpty() ? null : queue.get(0), queue);
    }

    @Transactional
    public MatchResponse create(CreateMatchRequest req) {
        AuthUser me = CurrentUser.get();
        CurrentUser.require(Permission.QUEUE_CREATE);
        List<Long> ids = Stream.concat(req.team1PlayerIds().stream(), req.team2PlayerIds().stream()).toList();
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != 4) {
            throw BusinessException.badRequest("DUPLICATE_PLAYER_IN_MATCH",
                    "Satu pemain tidak boleh dipilih dua kali dalam pertandingan yang sama");
        }
        Map<Long, Player> players = playerService.byIds(ids);
        for (Long id : ids) {
            Player p = players.get(id);
            if (p == null) {
                throw BusinessException.notFound("Pemain #" + id);
            }
            if (!p.isActive()) {
                throw BusinessException.badRequest("PLAYER_INACTIVE", p.getNickname() + " sedang nonaktif");
            }
        }

        settingsService.lockForUpdate();
        Match match = new Match(me.id(), matchRepository.maxQueuePosition() + 1);
        match.addPlayer(players.get(req.team1PlayerIds().get(0)), 1, 1);
        match.addPlayer(players.get(req.team1PlayerIds().get(1)), 1, 2);
        match.addPlayer(players.get(req.team2PlayerIds().get(0)), 2, 1);
        match.addPlayer(players.get(req.team2PlayerIds().get(1)), 2, 2);
        matchRepository.save(match);
        renumber();
        eventRepository.save(new MatchEvent(match.getId(), MatchEventType.CREATED, null, 0, null, null, me.id(),
                Instant.now(clock)));
        auditService.record("MATCH_QUEUED", "MATCH", match.getId(), "Antrean baru: " + label(match), Map.of());
        return mapper.toResponse(match);
    }

    @Transactional
    public List<MatchResponse> move(Long id, int position) {
        AuthUser me = CurrentUser.get();
        settingsService.lockForUpdate();
        List<Match> queued = new ArrayList<>(matchRepository.findByStatusInOrderByQueuePositionAsc(QUEUED));
        Match match = queued.stream().filter(m -> m.getId().equals(id)).findFirst()
                .orElseThrow(() -> BusinessException.conflict("NOT_IN_QUEUE",
                        "Pertandingan sudah tidak ada di antrean (mungkin sudah dimulai atau dihapus)"));
        if (!me.has(Permission.QUEUE_MANAGE)) {
            throw BusinessException.forbidden("Anda tidak memiliki izin mengatur urutan antrean");
        }
        int from = queued.indexOf(match);
        int to = Math.max(0, Math.min(position - 1, queued.size() - 1));
        if (from != to) {
            queued.remove(from);
            queued.add(to, match);
            for (int i = 0; i < queued.size(); i++) {
                queued.get(i).setQueuePosition(i + 1);
            }
            renumber(queued);
            eventRepository.save(new MatchEvent(id, MatchEventType.MOVED, to - from, null,
                    "Posisi " + (from + 1) + " → " + (to + 1), null, me.id(), Instant.now(clock)));
        }
        return mapper.toResponses(queued);
    }

    @Transactional
    public void remove(Long id, String reason) {
        AuthUser me = CurrentUser.get();
        settingsService.lockForUpdate();
        Match match = matchRepository.lockById(id).orElseThrow(() -> BusinessException.notFound("Pertandingan"));
        if (!match.getStatus().isQueued()) {
            throw BusinessException.conflict("NOT_IN_QUEUE",
                    "Hanya pertandingan yang belum dimulai yang dapat dihapus dari antrean");
        }
        boolean isCreator = match.getCreatedBy().equals(me.id());
        if (!me.has(Permission.QUEUE_MANAGE) && !(isCreator && me.has(Permission.QUEUE_CREATE))) {
            throw BusinessException.forbidden("Anda hanya dapat menghapus antrean yang Anda buat");
        }
        String why = reason == null || reason.isBlank() ? "Dihapus dari antrean" : reason.trim();
        match.cancel(Instant.now(clock), me.id(), why);
        matchRepository.flush();
        renumber();
        eventRepository.save(new MatchEvent(id, MatchEventType.CANCELLED, null, null, why, null, me.id(),
                Instant.now(clock)));
        auditService.record("MATCH_REMOVED_FROM_QUEUE", "MATCH", id, "Antrean dihapus: " + label(match),
                Map.of("reason", why));
    }

    /** Re-reads the queue and normalises positions. Caller must hold the queue lock. */
    void renumber() {
        List<Match> queued = new ArrayList<>(matchRepository.findByStatusInOrderByQueuePositionAsc(QUEUED));
        queued.sort(Comparator.comparing(Match::getQueuePosition).thenComparing(Match::getId));
        renumber(queued);
    }

    private void renumber(List<Match> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            Match m = ordered.get(i);
            m.setQueuePosition(i + 1);
            m.setStatus(i == 0 ? MatchStatus.READY : MatchStatus.WAITING);
        }
        matchRepository.flush();
    }

    static String label(Match m) {
        return team(m, 1) + " VS " + team(m, 2);
    }

    private static String team(Match m, int team) {
        return m.team(team).stream().map(mp -> mp.getPlayer().getNickname()).collect(Collectors.joining(" - "));
    }
}
