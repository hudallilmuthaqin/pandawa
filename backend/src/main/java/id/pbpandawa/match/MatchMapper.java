package id.pbpandawa.match;

import id.pbpandawa.common.Money;
import id.pbpandawa.match.MatchDtos.MatchEventResponse;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.player.PlayerDtos.PlayerRef;
import id.pbpandawa.settings.SettingsService;
import id.pbpandawa.user.User;
import id.pbpandawa.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class MatchMapper {

    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public MatchMapper(UserRepository userRepository, SettingsService settingsService, Clock clock) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    public MatchResponse toResponse(Match m) {
        return m == null ? null : toResponses(List.of(m)).get(0);
    }

    public List<MatchResponse> toResponses(Collection<Match> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = matches.stream()
                .flatMap(m -> Stream.of(m.getCreatedBy(), m.getStartedBy(), m.getCompletedBy(), m.getCancelledBy()))
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> names = usernames(userIds);
        BigDecimal currentPrice = Money.normalize(settingsService.current().getShuttlecockPrice());
        Instant now = Instant.now(clock);
        return matches.stream().map(m -> {
            boolean snapshot = m.getShuttlecockPrice() != null;
            BigDecimal price = snapshot ? Money.normalize(m.getShuttlecockPrice()) : currentPrice;
            return new MatchResponse(
                    m.getId(), m.getStatus(), m.getQueuePosition(),
                    m.team(1).stream().map(mp -> PlayerRef.of(mp.getPlayer())).toList(),
                    m.team(2).stream().map(mp -> PlayerRef.of(mp.getPlayer())).toList(),
                    m.getShuttlecockCount(), price, snapshot, Money.times(price, m.getPlayers().size()),
                    m.getPlayDate(), m.getCreatedAt(), m.getStartedAt(), m.getCompletedAt(), m.getCancelledAt(),
                    m.durationSeconds(now), m.getWinnerTeam(), m.getScore(), m.getNotes(), m.getCancelReason(),
                    names.get(m.getCreatedBy()), names.get(m.getStartedBy()), names.get(m.getCompletedBy()),
                    names.get(m.getCancelledBy()), m.getCreatedBy(), m.getVersion());
        }).toList();
    }

    public List<MatchEventResponse> toEventResponses(List<MatchEvent> events) {
        Map<Long, String> names = usernames(events.stream().map(MatchEvent::getActorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return events.stream().map(e -> new MatchEventResponse(e.getId(), e.getType(), e.getDelta(), e.getCountAfter(),
                e.getReason(), names.get(e.getActorId()), e.getCreatedAt())).toList();
    }

    private Map<Long, String> usernames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUsername));
    }
}
