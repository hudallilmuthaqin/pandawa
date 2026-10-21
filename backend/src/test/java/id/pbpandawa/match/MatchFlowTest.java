package id.pbpandawa.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import id.pbpandawa.IntegrationTestBase;
import id.pbpandawa.player.Player;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchFlowTest extends IntegrationTestBase {

    @Autowired MatchRepository matchRepository;
    @Autowired MatchEventRepository eventRepository;

    Player rizal, azis, didi, arif, hudal, oji;

    @BeforeEach
    void players() {
        rizal = player("Rizal");
        azis = player("Azis");
        didi = player("Didi");
        arif = player("Arif");
        hudal = player("Hudal");
        oji = player("Oji");
    }

    @Test
    void createsQueueEntryWithFourPlayers() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        var q = body(mvc.perform(auth(get("/api/queue"), adminToken)).andReturn());
        assertThat(q.get("queue")).hasSize(1);
        var m = q.get("queue").get(0);
        assertThat(m.get("id").asLong()).isEqualTo(id);
        assertThat(m.get("status").asText()).isEqualTo("READY");
        assertThat(m.get("queuePosition").asInt()).isEqualTo(1);
        assertThat(m.get("team1")).hasSize(2);
        assertThat(m.get("team2")).hasSize(2);
        assertThat(m.get("team1").get(0).get("nickname").asText()).isEqualTo("Rizal");
    }

    @Test
    void samePlayerTwiceInOneMatchIsRejected() throws Exception {
        var r = mvc.perform(auth(post("/api/queue"), adminToken).content(toJson(Map.of(
                "team1PlayerIds", List.of(rizal.getId(), azis.getId()),
                "team2PlayerIds", List.of(rizal.getId(), arif.getId()))))).andReturn();
        expectStatus(r, 400);
        assertThat(body(r).get("code").asText()).isEqualTo("DUPLICATE_PLAYER_IN_MATCH");
        assertThat(matchRepository.count()).isZero();
    }

    @Test
    void startingMatchSetsInProgressWithStartTimeAndPriceSnapshot() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        var r = start(id);
        expectStatus(r, 200);
        var m = body(r);
        assertThat(m.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(m.get("startedAt").asText()).isEqualTo("2026-10-12T05:00:00Z");
        assertThat(m.get("shuttlecockPrice").asInt()).isEqualTo(4000);
        assertThat(m.get("priceSnapshot").asBoolean()).isTrue();
        assertThat(m.get("queuePosition").isNull()).isTrue();
    }

    @Test
    void cannotRunTwoMatchesAtTheSameTime() throws Exception {
        long first = queue(rizal, azis, didi, arif);
        long second = queue(rizal, hudal, oji, didi);
        expectStatus(start(first), 200);
        var r = start(second);
        expectStatus(r, 409);
        assertThat(body(r).get("code").asText()).isEqualTo("MATCH_ALREADY_IN_PROGRESS");
        assertThat(matchRepository.countByStatus(MatchStatus.IN_PROGRESS)).isEqualTo(1);
    }

    @Test
    void recordsShuttlecocksAndComputesCost() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        start(id);
        for (int i = 0; i < 4; i++) {
            expectStatus(addShuttle(id, 1, UUID.randomUUID()), 200);
        }
        var m = body(mvc.perform(auth(get("/api/matches/" + id), adminToken)).andReturn()).get("match");
        assertThat(m.get("shuttlecockCount").asInt()).isEqualTo(4);
        assertThat(m.get("shuttlecockCost").asInt()).isEqualTo(16000); // 4 players x 4.000
        // correction -1 → 3 kok: cost depends on players, not on shuttlecocks
        var r = addShuttle(id, -1, UUID.randomUUID());
        expectStatus(r, 200);
        assertThat(body(r).get("shuttlecockCount").asInt()).isEqualTo(3);
        assertThat(body(r).get("shuttlecockCost").asInt()).isEqualTo(16000);
    }

    @Test
    void retriedShuttlecockRequestIsAppliedOnlyOnce() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        start(id);
        UUID requestId = UUID.randomUUID();
        expectStatus(addShuttle(id, 1, requestId), 200);
        expectStatus(addShuttle(id, 1, requestId), 200); // retry from a slow phone
        expectStatus(addShuttle(id, 1, requestId), 200);
        assertThat(matchRepository.findById(id).orElseThrow().getShuttlecockCount()).isEqualTo(1);
    }

    @Test
    void shuttlecockCountCannotGoNegative() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        start(id);
        var r = addShuttle(id, -1, UUID.randomUUID());
        expectStatus(r, 400);
        assertThat(body(r).get("code").asText()).isEqualTo("NEGATIVE_SHUTTLECOCK");
    }

    @Test
    void completingRemovesFromQueueAndPromotesNext() throws Exception {
        long first = queue(rizal, azis, didi, arif);
        long second = queue(rizal, hudal, oji, didi);
        start(first);
        assertThat(matchRepository.findById(second).orElseThrow().getStatus()).isEqualTo(MatchStatus.READY);
        addShuttle(first, 1, UUID.randomUUID());
        addShuttle(first, 1, UUID.randomUUID());
        addShuttle(first, 1, UUID.randomUUID());
        clock.advance(Duration.ofMinutes(25));

        var r = mvc.perform(auth(post("/api/matches/" + first + "/complete"), adminToken).content(toJson(Map.of(
                "expectedShuttlecockCount", 3, "winnerTeam", 1, "score", "21-18", "notes", "seru")))).andReturn();
        expectStatus(r, 200);
        var m = body(r);
        assertThat(m.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(m.get("durationSeconds").asLong()).isEqualTo(1500);
        assertThat(m.get("shuttlecockCost").asInt()).isEqualTo(16000);
        assertThat(m.get("playDate").asText()).isEqualTo("2026-10-12");
        assertThat(m.get("winnerTeam").asInt()).isEqualTo(1);

        var q = body(mvc.perform(auth(get("/api/queue"), adminToken)).andReturn());
        assertThat(q.get("current").isNull()).isTrue();
        assertThat(q.get("queue")).hasSize(1);
        assertThat(q.get("next").get("id").asLong()).isEqualTo(second);
        assertThat(q.get("next").get("status").asText()).isEqualTo("READY");
        // history keeps it
        var h = body(mvc.perform(auth(get("/api/matches/history"), adminToken)).andReturn());
        assertThat(h.get("totalElements").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from court_daily_charges", Integer.class)).isEqualTo(1);
    }

    @Test
    void completedGameIncrementsPlayerStatistics() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        var stats = body(mvc.perform(auth(get("/api/players/" + rizal.getId() + "/statistics"), adminToken)).andReturn());
        assertThat(stats.get("allTimeGames").asInt()).isZero();
        start(id);
        // in-progress does not count
        stats = body(mvc.perform(auth(get("/api/players/" + rizal.getId() + "/statistics"), adminToken)).andReturn());
        assertThat(stats.get("allTimeGames").asInt()).isZero();
        addShuttle(id, 1, UUID.randomUUID());
        complete(id);
        stats = body(mvc.perform(auth(get("/api/players/" + rizal.getId() + "/statistics"), adminToken)).andReturn());
        assertThat(stats.get("allTimeGames").asInt()).isEqualTo(1);
        assertThat(stats.get("period").get("games").asInt()).isEqualTo(1);
        assertThat(stats.get("period").get("wins").asInt()).isZero();
        var p = body(mvc.perform(auth(get("/api/players/" + rizal.getId()), adminToken)).andReturn());
        assertThat(p.get("totalGames").asInt()).isEqualTo(1);
    }

    @Test
    void cannotCompleteTwice() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        start(id);
        expectStatus(complete(id), 200);
        var r = complete(id);
        expectStatus(r, 409);
        assertThat(body(r).get("code").asText()).isEqualTo("ALREADY_COMPLETED");
        assertThat(eventRepository.findByMatchIdOrderByCreatedAtAscIdAsc(id).stream()
                .filter(e -> e.getType() == MatchEventType.COMPLETED)).hasSize(1);
    }

    @Test
    void staleShuttlecockCountBlocksCompletion() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        start(id);
        addShuttle(id, 1, UUID.randomUUID());
        var r = mvc.perform(auth(post("/api/matches/" + id + "/complete"), adminToken)
                .content(toJson(Map.of("expectedShuttlecockCount", 0)))).andReturn();
        expectStatus(r, 409);
        assertThat(matchRepository.findById(id).orElseThrow().getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
    }

    @Test
    void queueCanBeReorderedAndUnstartedMatchesRemoved() throws Exception {
        long a = queue(rizal, azis, didi, arif);
        long b = queue(rizal, hudal, oji, didi);
        long c = queue(azis, hudal, oji, arif);
        expectStatus(mvc.perform(auth(patch("/api/queue/" + c + "/position"), adminToken)
                .content(toJson(Map.of("position", 1)))).andReturn(), 200);
        var q = body(mvc.perform(auth(get("/api/queue"), adminToken)).andReturn()).get("queue");
        assertThat(List.of(q.get(0).get("id").asLong(), q.get(1).get("id").asLong(), q.get(2).get("id").asLong()))
                .containsExactly(c, a, b);
        assertThat(q.get(0).get("status").asText()).isEqualTo("READY");
        assertThat(q.get(1).get("status").asText()).isEqualTo("WAITING");

        expectStatus(mvc.perform(auth(delete("/api/queue/" + a), adminToken)).andReturn(), 204);
        q = body(mvc.perform(auth(get("/api/queue"), adminToken)).andReturn()).get("queue");
        assertThat(q).hasSize(2);
        assertThat(q.get(1).get("queuePosition").asInt()).isEqualTo(2);
        assertThat(matchRepository.findById(a).orElseThrow().getStatus()).isEqualTo(MatchStatus.CANCELLED);

        // a started match cannot be deleted from the queue
        start(c);
        expectStatus(mvc.perform(auth(delete("/api/queue/" + c), adminToken)).andReturn(), 409);
    }

    @Test
    void cancelledMatchDoesNotCountAndHasNoCourtCharge() throws Exception {
        long id = queue(rizal, azis, didi, arif);
        start(id);
        addShuttle(id, 1, UUID.randomUUID());
        expectStatus(mvc.perform(auth(post("/api/matches/" + id + "/cancel"), adminToken)
                .content(toJson(Map.of("reason", "hujan")))).andReturn(), 200);
        assertThat(jdbc.queryForObject("select count(*) from court_daily_charges", Integer.class)).isZero();
        var stats = body(mvc.perform(auth(get("/api/players/" + rizal.getId() + "/statistics"), adminToken)).andReturn());
        assertThat(stats.get("allTimeGames").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='MATCH_CANCELLED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void completedCorrectionIsAudited() throws Exception {
        long id = playCompletedGame(DEFAULT_NOW, 4, rizal, azis, didi, arif);
        var r = mvc.perform(auth(post("/api/matches/" + id + "/corrections"), adminToken)
                .content(toJson(Map.of("shuttlecockCount", 5, "reason", "lupa catat satu kok")))).andReturn();
        expectStatus(r, 200);
        assertThat(body(r).get("shuttlecockCount").asInt()).isEqualTo(5);
        assertThat(body(r).get("shuttlecockCost").asInt()).isEqualTo(16000);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='COMPLETED_MATCH_CORRECTED'",
                Integer.class)).isEqualTo(1);
    }
}
