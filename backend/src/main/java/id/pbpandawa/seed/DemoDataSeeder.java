package id.pbpandawa.seed;

import id.pbpandawa.config.AppProperties;
import id.pbpandawa.court.CourtDailyChargeRepository;
import id.pbpandawa.match.Match;
import id.pbpandawa.match.MatchRepository;
import id.pbpandawa.match.MatchStatus;
import id.pbpandawa.player.Player;
import id.pbpandawa.player.PlayerRepository;
import id.pbpandawa.settings.ClubSettings;
import id.pbpandawa.settings.ClubSettingsRepository;
import id.pbpandawa.user.Role;
import id.pbpandawa.user.User;
import id.pbpandawa.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for local development and manual testing (app.seed.enabled=true).
 * Runs only on an empty database. Never enable in production.
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final CourtDailyChargeRepository courtRepository;
    private final ClubSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, PlayerRepository playerRepository,
                          MatchRepository matchRepository, CourtDailyChargeRepository courtRepository,
                          ClubSettingsRepository settingsRepository, PasswordEncoder passwordEncoder,
                          AppProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.courtRepository = courtRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        log.warn("Seeding DEMO data (app.seed.enabled=true). Do not use in production!");
        ClubSettings settings = settingsRepository.findById(ClubSettings.SINGLETON_ID).orElseThrow();
        ZoneId zone = settings.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));

        String[][] roster = {
                {"Rizal Fahmi", "Rizal"}, {"Abdul Azis", "Azis"}, {"Didi Supriadi", "Didi"}, {"Arif Hidayat", "Arif"},
                {"Hudal Lilmuthaqin", "Hudal"}, {"Fauzi Ramadhan", "Oji"}, {"Fauzan Akbar", "Fauzan"},
                {"Bobby Hoho", "Boho"}, {"Raihan Saputra", "Ehan"}, {"Jaka Pratama", "Bang Jak"},
        };
        Map<String, Player> players = new LinkedHashMap<>();
        for (String[] r : roster) {
            players.put(r[1], playerRepository.save(new Player(r[0], r[1], null, today.minusDays(30))));
        }

        User admin = userRepository.save(new User("admin", passwordEncoder.encode(props.seed().adminPassword()), Role.ADMIN));
        User rizal = new User("rizal", passwordEncoder.encode(props.seed().userPassword()), Role.USER);
        rizal.setPlayer(players.get("Rizal"));
        userRepository.save(rizal);
        User hudal = new User("hudal", passwordEncoder.encode(props.seed().userPassword()), Role.USER);
        hudal.setPlayer(players.get("Hudal"));
        userRepository.save(hudal);

        // History: a few completed sessions over the past two weeks.
        Random rnd = new Random(42);
        List<Player> all = List.copyOf(players.values());
        BigDecimal shuttlePrice = settings.getShuttlecockPrice();
        for (int daysAgo : new int[] {13, 11, 9, 6, 4, 2, 1}) {
            LocalDate date = today.minusDays(daysAgo);
            Instant t = date.atTime(19, 0).atZone(zone).toInstant();
            int games = 2 + rnd.nextInt(4);
            for (int g = 0; g < games; g++) {
                List<Player> four = pickFour(all, rnd);
                Match m = new Match(admin.getId(), 1);
                m.addPlayer(four.get(0), 1, 1);
                m.addPlayer(four.get(1), 1, 2);
                m.addPlayer(four.get(2), 2, 1);
                m.addPlayer(four.get(3), 2, 2);
                m.start(t, admin.getId(), shuttlePrice);
                m.setShuttlecockCount(2 + rnd.nextInt(4));
                m.setWinnerTeam((short) (1 + rnd.nextInt(2)));
                int minutes = 18 + rnd.nextInt(15);
                t = t.plusSeconds(minutes * 60L);
                m.complete(t, date, admin.getId());
                matchRepository.save(m);
                t = t.plusSeconds(120);
            }
            courtRepository.insertIfAbsent(date, settings.getCourtPricePerDay(), settings.getCourtSplitMethod().name());
        }

        // Today's queue (from the project brief).
        String[][] queue = {
                {"Rizal", "Azis", "Didi", "Arif"},
                {"Rizal", "Hudal", "Oji", "Didi"},
                {"Fauzan", "Boho", "Ehan", "Bang Jak"},
        };
        for (int i = 0; i < queue.length; i++) {
            Match m = new Match(admin.getId(), i + 1);
            m.setStatus(i == 0 ? MatchStatus.READY : MatchStatus.WAITING);
            m.addPlayer(players.get(queue[i][0]), 1, 1);
            m.addPlayer(players.get(queue[i][1]), 1, 2);
            m.addPlayer(players.get(queue[i][2]), 2, 1);
            m.addPlayer(players.get(queue[i][3]), 2, 2);
            matchRepository.save(m);
        }
        log.warn("Demo accounts: admin / {}  |  rizal, hudal / {}", "<app.seed.admin-password>", "<app.seed.user-password>");
    }

    private static List<Player> pickFour(List<Player> all, Random rnd) {
        List<Player> shuffled = new java.util.ArrayList<>(all);
        java.util.Collections.shuffle(shuffled, rnd);
        return shuffled.subList(0, 4);
    }
}
