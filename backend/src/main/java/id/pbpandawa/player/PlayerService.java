package id.pbpandawa.player;

import id.pbpandawa.audit.AuditService;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.common.PageResponse;
import id.pbpandawa.player.PlayerDtos.PlayerRequest;
import id.pbpandawa.player.PlayerDtos.PlayerResponse;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.settings.SettingsService;
import id.pbpandawa.user.User;
import id.pbpandawa.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository repository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final EntityManager em;
    private final Clock clock;

    public PlayerService(PlayerRepository repository, UserRepository userRepository, SettingsService settingsService,
                         AuditService auditService, EntityManager em, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.em = em;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerResponse> search(String q, PlayerStatus status, int page, int size) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        var result = repository.search(status, needle,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("nickname")));
        return PageResponse.of(result, this::toResponses);
    }

    @Transactional(readOnly = true)
    public PlayerResponse get(Long id) {
        return toResponses(List.of(find(id))).get(0);
    }

    @Transactional(readOnly = true)
    public Player find(Long id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Pemain"));
    }

    @Transactional
    public PlayerResponse create(PlayerRequest req) {
        Player p = createEntity(req);
        auditService.record("PLAYER_CREATED", "PLAYER", p.getId(), "Pemain baru: " + p.getNickname(),
                Map.of("fullName", p.getFullName(), "nickname", p.getNickname()));
        return get(p.getId());
    }

    /** Creates a player entity (used by admin create and self-registration). */
    @Transactional
    public Player createEntity(PlayerRequest req) {
        String nickname = normalizeName(req.nickname());
        if (repository.existsByNicknameIgnoreCase(nickname)) {
            throw BusinessException.conflict("DUPLICATE_PLAYER", "Nickname \"" + nickname + "\" sudah terdaftar");
        }
        LocalDate registeredOn = req.registeredOn() != null ? req.registeredOn()
                : LocalDate.now(clock.withZone(settingsService.current().zoneId()));
        Player p = new Player(normalizeName(req.fullName()), nickname, blankToNull(req.phone()), registeredOn);
        if (req.status() != null) {
            p.setStatus(req.status());
        }
        return repository.saveAndFlush(p);
    }

    @Transactional
    public PlayerResponse update(Long id, PlayerRequest req) {
        Player p = find(id);
        String nickname = normalizeName(req.nickname());
        if (repository.existsByNicknameIgnoreCaseAndIdNot(nickname, id)) {
            throw BusinessException.conflict("DUPLICATE_PLAYER", "Nickname \"" + nickname + "\" sudah terdaftar");
        }
        Map<String, Object> changes = new HashMap<>();
        if (!p.getNickname().equals(nickname)) {
            changes.put("nickname", p.getNickname() + " → " + nickname);
        }
        if (!p.getFullName().equals(normalizeName(req.fullName()))) {
            changes.put("fullName", p.getFullName() + " → " + normalizeName(req.fullName()));
        }
        p.setFullName(normalizeName(req.fullName()));
        p.setNickname(nickname);
        p.setPhone(blankToNull(req.phone()));
        if (req.registeredOn() != null) {
            p.setRegisteredOn(req.registeredOn());
        }
        if (req.status() != null && req.status() != p.getStatus()) {
            changes.put("status", p.getStatus() + " → " + req.status());
            p.setStatus(req.status());
        }
        repository.flush();
        auditService.record("PLAYER_UPDATED", "PLAYER", id, "Data pemain diubah: " + nickname, changes);
        return get(id);
    }

    @Transactional
    public PlayerResponse changeStatus(Long id, PlayerStatus status) {
        Player p = find(id);
        if (p.getStatus() != status) {
            PlayerStatus before = p.getStatus();
            p.setStatus(status);
            repository.flush();
            auditService.record(status == PlayerStatus.INACTIVE ? "PLAYER_DEACTIVATED" : "PLAYER_ACTIVATED",
                    "PLAYER", id, "Status " + p.getNickname() + ": " + before + " → " + status, Map.of());
        }
        return get(id);
    }

    List<PlayerResponse> toResponses(List<Player> players) {
        if (players.isEmpty()) {
            return List.of();
        }
        AuthUser me = CurrentUser.get();
        List<Long> ids = players.stream().map(Player::getId).toList();
        Map<Long, Long> games = countCompleted(ids);
        Map<Long, String> usernames = me.isAdmin()
                ? em.createQuery("select u from User u where u.player.id in :ids", User.class)
                    .setParameter("ids", ids).getResultList().stream()
                    .collect(Collectors.toMap(u -> u.getPlayer().getId(), User::getUsername))
                : Map.of();
        return players.stream().map(p -> new PlayerResponse(p.getId(), p.getFullName(), p.getNickname(),
                me.isAdmin() || p.getId().equals(me.playerId()) ? p.getPhone() : null,
                p.getStatus(), p.getRegisteredOn(), usernames.get(p.getId()),
                games.getOrDefault(p.getId(), 0L), p.getCreatedAt())).toList();
    }

    private Map<Long, Long> countCompleted(List<Long> ids) {
        List<Object[]> rows = em.createQuery("""
                        select mp.player.id, count(mp) from MatchPlayer mp
                        where mp.player.id in :ids and mp.match.status = id.pbpandawa.match.MatchStatus.COMPLETED
                        group by mp.player.id
                        """, Object[].class)
                .setParameter("ids", ids).getResultList();
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    public Map<Long, Player> byIds(List<Long> ids) {
        return repository.findByIdIn(ids).stream().collect(Collectors.toMap(Player::getId, Function.identity()));
    }

    static String normalizeName(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
