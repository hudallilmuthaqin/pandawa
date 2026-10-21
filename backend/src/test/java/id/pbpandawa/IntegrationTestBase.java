package id.pbpandawa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.pbpandawa.player.Player;
import id.pbpandawa.player.PlayerRepository;
import id.pbpandawa.user.Role;
import id.pbpandawa.user.User;
import id.pbpandawa.user.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Boots the full application against a real (embedded) PostgreSQL instance,
 * so partial unique indexes, row locks and ON CONFLICT behave exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    /** 2026-10-12 12:00 WIB (05:00 UTC). */
    public static final Instant DEFAULT_NOW = Instant.parse("2026-10-12T05:00:00Z");
    protected static final String PASSWORD = "password123";

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(DEFAULT_NOW);
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected MutableClock clock;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlayerRepository playerRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();
    protected String adminToken;
    protected String userToken;

    @BeforeEach
    void resetDatabase() throws Exception {
        clock.set(DEFAULT_NOW);
        tokens.clear();
        jdbc.execute("TRUNCATE match_events, match_players, matches, court_daily_charges, audit_logs, users, players "
                + "RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE club_settings SET club_name='PB PANDAWA', shuttlecock_price=4000, court_price_per_day=7000, "
                + "timezone='Asia/Jakarta', court_split_method='EQUAL_SPLIT', self_registration_enabled=false, "
                + "score_enabled=true, correction_requires_reason=true, user_export_enabled=false WHERE id=1");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("INSERT INTO role_permissions(role, permission) VALUES ('USER','QUEUE_CREATE'),('USER','MATCH_START'),"
                + "('USER','SHUTTLE_RECORD'),('USER','MATCH_COMPLETE')");
        createUser("admin", Role.ADMIN, null);
        adminToken = token("admin");
    }

    protected User createUser(String username, Role role, Player player) {
        User u = new User(username, passwordEncoder.encode(PASSWORD), role);
        u.setPlayer(player);
        return userRepository.saveAndFlush(u);
    }

    protected Player player(String nickname) {
        return playerRepository.saveAndFlush(new Player(nickname + " Lengkap", nickname, null, LocalDate.of(2026, 1, 1)));
    }

    protected String token(String username) throws Exception {
        if (!tokens.containsKey(username)) {
            MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                    .andReturn();
            if (r.getResponse().getStatus() != 200) {
                throw new IllegalStateException("login failed for " + username + ": " + r.getResponse().getContentAsString());
            }
            tokens.put(username, json.readTree(r.getResponse().getContentAsString()).get("token").asText());
        }
        return tokens.get(username);
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected String toJson(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    // ------------------------------------------------------------ flow helpers (admin)

    protected long queue(Player a, Player b, Player c, Player d) throws Exception {
        MvcResult r = mvc.perform(auth(post("/api/queue"), adminToken).content(toJson(Map.of(
                "team1PlayerIds", List.of(a.getId(), b.getId()),
                "team2PlayerIds", List.of(c.getId(), d.getId()))))).andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException(r.getResponse().getContentAsString());
        }
        return body(r).get("id").asLong();
    }

    protected MvcResult start(long matchId) throws Exception {
        return mvc.perform(auth(post("/api/matches/" + matchId + "/start"), adminToken)).andReturn();
    }

    protected MvcResult addShuttle(long matchId, int delta, UUID requestId) throws Exception {
        return mvc.perform(auth(post("/api/matches/" + matchId + "/shuttlecocks"), adminToken)
                .content(toJson(Map.of("delta", delta, "requestId", requestId.toString(), "reason", "test")))).andReturn();
    }

    protected MvcResult complete(long matchId) throws Exception {
        return mvc.perform(auth(post("/api/matches/" + matchId + "/complete"), adminToken).content("{}")).andReturn();
    }

    /** Queues, starts at {@code startAt}, records {@code shuttles} kok and completes 30 minutes later. */
    protected long playCompletedGame(Instant startAt, int shuttles, Player a, Player b, Player c, Player d) throws Exception {
        clock.set(startAt);
        long id = queue(a, b, c, d);
        expectStatus(start(id), 200);
        for (int i = 0; i < shuttles; i++) {
            expectStatus(addShuttle(id, 1, UUID.randomUUID()), 200);
        }
        clock.set(startAt.plusSeconds(1800));
        expectStatus(complete(id), 200);
        return id;
    }

    protected void expectStatus(MvcResult r, int status) throws Exception {
        if (r.getResponse().getStatus() != status) {
            throw new AssertionError("Expected HTTP " + status + " but got " + r.getResponse().getStatus() + ": "
                    + r.getResponse().getContentAsString());
        }
    }
}
