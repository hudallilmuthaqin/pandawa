package id.pbpandawa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import id.pbpandawa.IntegrationTestBase;
import id.pbpandawa.player.Player;
import id.pbpandawa.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationTest extends IntegrationTestBase {

    Player rizal, azis, didi, arif;
    String user;

    @BeforeEach
    void setUp() throws Exception {
        rizal = player("Rizal");
        azis = player("Azis");
        didi = player("Didi");
        arif = player("Arif");
        createUser("rizal", Role.USER, rizal);
        user = token("rizal");
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        expectStatus(mvc.perform(get("/api/queue")).andReturn(), 401);
        expectStatus(mvc.perform(get("/api/settings/public")).andReturn(), 200);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        var r = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(toJson(Map.of("username", "rizal", "password", "salah")))).andReturn();
        expectStatus(r, 401);
    }

    @Test
    void userCannotUseAdminFeatures() throws Exception {
        expectStatus(mvc.perform(auth(get("/api/reports/range?from=2026-10-01&to=2026-10-12"), user)).andReturn(), 403);
        expectStatus(mvc.perform(auth(get("/api/reports/dashboard"), user)).andReturn(), 403);
        expectStatus(mvc.perform(auth(get("/api/users"), user)).andReturn(), 403);
        expectStatus(mvc.perform(auth(get("/api/audit-logs"), user)).andReturn(), 403);
        expectStatus(mvc.perform(auth(get("/api/reports/export?type=players"), user)).andReturn(), 403);
        expectStatus(mvc.perform(auth(put("/api/settings"), user).content(toJson(Map.of(
                "clubName", "X", "shuttlecockPrice", 1, "courtPricePerDay", 1, "timezone", "Asia/Jakarta",
                "courtSplitMethod", "CLUB_ONLY", "userPermissions", List.of())))).andReturn(), 403);
        expectStatus(mvc.perform(auth(get("/api/players/" + azis.getId() + "/statistics"), user)).andReturn(), 403);
        expectStatus(mvc.perform(auth(get("/api/players/" + rizal.getId() + "/statistics"), user)).andReturn(), 200);
    }

    @Test
    void userWithDefaultPermissionsCanRunTheCourtButNotCancelOrCorrect() throws Exception {
        var created = mvc.perform(auth(post("/api/queue"), user).content(toJson(Map.of(
                "team1PlayerIds", List.of(rizal.getId(), azis.getId()),
                "team2PlayerIds", List.of(didi.getId(), arif.getId()))))).andReturn();
        expectStatus(created, 201);
        long id = body(created).get("id").asLong();
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/start"), user)).andReturn(), 200);
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/shuttlecocks"), user)
                .content(toJson(Map.of("delta", 1, "requestId", UUID.randomUUID().toString())))).andReturn(), 200);
        // decrease requires SHUTTLE_CORRECT (not granted by default)
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/shuttlecocks"), user)
                .content(toJson(Map.of("delta", -1, "requestId", UUID.randomUUID().toString(), "reason", "x")))).andReturn(), 403);
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/cancel"), user).content("{}")).andReturn(), 403);
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/complete"), user).content("{}")).andReturn(), 200);
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/corrections"), user)
                .content(toJson(Map.of("shuttlecockCount", 9, "reason", "curang")))).andReturn(), 403);

        var me = body(mvc.perform(auth(get("/api/reports/me?period=TODAY"), user)).andReturn());
        assertThat(me.get("period").get("games").asInt()).isEqualTo(1);
        assertThat(me.get("period").get("shuttlecockCost").asInt()).isEqualTo(4000);
    }

    @Test
    void revokedPermissionTakesEffectImmediately() throws Exception {
        jdbc.update("DELETE FROM role_permissions WHERE permission='QUEUE_CREATE'");
        expectStatus(mvc.perform(auth(post("/api/queue"), user).content(toJson(Map.of(
                "team1PlayerIds", List.of(rizal.getId(), azis.getId()),
                "team2PlayerIds", List.of(didi.getId(), arif.getId()))))).andReturn(), 403);
    }

    @Test
    void userHistoryIsScopedToOwnMatches() throws Exception {
        Player x = player("X"), y = player("Y"), z = player("Z"), w = player("W");
        playCompletedGame(DEFAULT_NOW, 2, x, y, z, w);
        playCompletedGame(DEFAULT_NOW.plusSeconds(3600), 3, rizal, azis, didi, arif);
        var h = body(mvc.perform(auth(get("/api/matches/history"), user)).andReturn());
        assertThat(h.get("totalElements").asInt()).isEqualTo(1);
        var all = body(mvc.perform(auth(get("/api/matches/history"), adminToken)).andReturn());
        assertThat(all.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void logoutRevokesToken() throws Exception {
        expectStatus(mvc.perform(auth(post("/api/auth/logout"), user)).andReturn(), 204);
        expectStatus(mvc.perform(auth(get("/api/auth/me"), user)).andReturn(), 401);
    }

    @Test
    void selfRegistrationOnlyWhenEnabled() throws Exception {
        Map<String, String> req = Map.of("fullName", "Ehan Saputra", "nickname", "Ehan", "username", "ehan",
                "password", "rahasia123");
        expectStatus(mvc.perform(post("/api/auth/register").contentType("application/json").content(toJson(req)))
                .andReturn(), 403);
        jdbc.update("UPDATE club_settings SET self_registration_enabled = true");
        var r = mvc.perform(post("/api/auth/register").contentType("application/json").content(toJson(req))).andReturn();
        expectStatus(r, 201);
        assertThat(body(r).get("user").get("playerNickname").asText()).isEqualTo("Ehan");
    }
}
