package id.pbpandawa.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import id.pbpandawa.IntegrationTestBase;
import id.pbpandawa.player.Player;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportCalculationTest extends IntegrationTestBase {

    static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

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

    static Instant wib(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(WIB).toInstant();
    }

    JsonNode range(String from, String to) throws Exception {
        var r = mvc.perform(auth(get("/api/reports/range?from=" + from + "&to=" + to), adminToken)).andReturn();
        expectStatus(r, 200);
        return body(r);
    }

    static JsonNode playerRow(JsonNode report, String nickname) {
        return StreamSupport.stream(report.get("players").spliterator(), false)
                .filter(p -> p.get("nickname").asText().equals(nickname)).findFirst().orElseThrow();
    }

    @Test
    void everyPlayerPaysTheShuttlecockPriceOncePerGame() throws Exception {
        playCompletedGame(wib("2026-10-12T19:00:00"), 4, rizal, azis, didi, arif);
        playCompletedGame(wib("2026-10-12T19:40:00"), 3, rizal, hudal, oji, didi);

        JsonNode report = range("2026-10-12", "2026-10-12");
        record Expect(int games, int kok, int cost) { }
        Map<String, Expect> expected = Map.of(
                "Rizal", new Expect(2, 7, 8000), "Azis", new Expect(1, 4, 4000),
                "Didi", new Expect(2, 7, 8000), "Arif", new Expect(1, 4, 4000),
                "Hudal", new Expect(1, 3, 4000), "Oji", new Expect(1, 3, 4000));
        expected.forEach((nick, e) -> {
            JsonNode row = playerRow(report, nick);
            assertThat(row.get("games").asInt()).as(nick + " games").isEqualTo(e.games());
            assertThat(row.get("shuttlecocks").asInt()).as(nick + " kok").isEqualTo(e.kok());
            assertThat(row.get("shuttlecockCost").asInt()).as(nick + " cost").isEqualTo(e.cost());
        });
    }

    @Test
    void clubShuttlecockCostIsPlayersTimesPricePerGame() throws Exception {
        playCompletedGame(wib("2026-10-12T19:00:00"), 4, rizal, azis, didi, arif);
        playCompletedGame(wib("2026-10-12T19:40:00"), 3, rizal, hudal, oji, didi);

        JsonNode report = range("2026-10-12", "2026-10-12");
        JsonNode s = report.get("summary");
        assertThat(s.get("totalGames").asInt()).isEqualTo(2);
        assertThat(s.get("uniquePlayers").asInt()).isEqualTo(6);
        assertThat(s.get("totalShuttlecocks").asInt()).isEqualTo(7);
        assertThat(s.get("shuttlecockCost").asInt()).isEqualTo(32000);    // 2 games x 4 players x 4.000
        assertThat(s.get("courtCost").asInt()).isEqualTo(7000);
        assertThat(s.get("totalExpense").asInt()).isEqualTo(39000);        // 32.000 + 7.000

        // Every player pays the price once per game, so the players' shares add up to the club's shuttlecock cost.
        int sumOfShares = StreamSupport.stream(report.get("players").spliterator(), false)
                .mapToInt(p -> p.get("shuttlecockCost").asInt()).sum();
        assertThat(sumOfShares).isEqualTo(s.get("shuttlecockCost").asInt());

        // Equal split of the court fee over 6 unique players adds up to exactly 7.000.
        int courtShares = StreamSupport.stream(report.get("players").spliterator(), false)
                .mapToInt(p -> p.get("courtShare").asInt()).sum();
        assertThat(courtShares).isEqualTo(7000);
        assertThat(playerRow(report, "Rizal").get("courtShare").asInt()).isIn(1166, 1167);
    }

    @Test
    void courtFeeIsChargedOncePerPlayingDate() throws Exception {
        for (int i = 0; i < 5; i++) {
            playCompletedGame(wib("2026-10-12T18:00:00").plusSeconds(i * 2400L), 2, rizal, azis, didi, arif);
        }
        JsonNode oneDay = range("2026-10-12", "2026-10-12").get("summary");
        assertThat(oneDay.get("totalGames").asInt()).isEqualTo(5);
        assertThat(oneDay.get("playDays").asInt()).isEqualTo(1);
        assertThat(oneDay.get("courtCost").asInt()).isEqualTo(7000);
        assertThat(jdbc.queryForObject("select count(*) from court_daily_charges", Integer.class)).isEqualTo(1);

        playCompletedGame(wib("2026-10-13T19:00:00"), 2, rizal, azis, didi, arif);
        playCompletedGame(wib("2026-10-15T19:00:00"), 2, rizal, azis, didi, arif);
        JsonNode threeDays = range("2026-10-12", "2026-10-15").get("summary");
        assertThat(threeDays.get("playDays").asInt()).isEqualTo(3);
        assertThat(threeDays.get("courtCost").asInt()).isEqualTo(21000);
    }

    @Test
    void rangeReportOverEightPlayingDays() throws Exception {
        int[] days = {1, 2, 4, 5, 7, 9, 11, 12};
        for (int d : days) {
            playCompletedGame(wib(String.format("2026-10-%02dT19:00:00", d)), 3, rizal, azis, didi, arif);
        }
        // outside the range: must be excluded
        playCompletedGame(wib("2026-09-30T19:00:00"), 3, rizal, azis, didi, arif);
        playCompletedGame(wib("2026-10-13T19:00:00"), 3, rizal, azis, didi, arif);

        JsonNode report = range("2026-10-01", "2026-10-12");
        JsonNode s = report.get("summary");
        assertThat(s.get("playDays").asInt()).isEqualTo(8);
        assertThat(s.get("courtCost").asInt()).isEqualTo(56000);        // 8 x 7.000
        assertThat(s.get("totalGames").asInt()).isEqualTo(8);
        assertThat(s.get("totalShuttlecocks").asInt()).isEqualTo(24);
        assertThat(s.get("shuttlecockCost").asInt()).isEqualTo(128000);       // 8 games x 4 x 4.000
        assertThat(s.get("totalExpense").asInt()).isEqualTo(184000);
        assertThat(report.get("daily")).hasSize(8);
        assertThat(report.get("daily").get(0).get("date").asText()).isEqualTo("2026-10-01");
        assertThat(report.get("daily").get(0).get("totalExpense").asInt()).isEqualTo(23000);

        // Monthly and dashboard numbers are consistent with the range report.
        clock.set(wib("2026-10-12T21:00:00"));
        JsonNode monthly = body(mvc.perform(auth(get("/api/reports/monthly?month=2026-10"), adminToken)).andReturn());
        assertThat(monthly.get("summary").get("playDays").asInt()).isEqualTo(9); // includes 13 Oct
        JsonNode month1to12 = body(mvc.perform(auth(get("/api/reports/dashboard?period=CUSTOM&from=2026-10-01&to=2026-10-12"),
                adminToken)).andReturn());
        assertThat(month1to12.get("summary")).isEqualTo(s);
    }

    @Test
    void invalidRangeIsRejected() throws Exception {
        var r = mvc.perform(auth(get("/api/reports/range?from=2026-10-12&to=2026-10-01"), adminToken)).andReturn();
        expectStatus(r, 400);
        assertThat(body(r).get("code").asText()).isEqualTo("INVALID_RANGE");
    }

    @Test
    void unfinishedMatchesAreExcluded() throws Exception {
        playCompletedGame(wib("2026-10-12T19:00:00"), 4, rizal, azis, didi, arif);
        long waiting = queue(rizal, hudal, oji, didi);
        long running = queue(azis, hudal, oji, arif);
        expectStatus(start(running), 200);
        addShuttle(running, 1, UUID.randomUUID());

        JsonNode report = range("2026-10-12", "2026-10-12");
        assertThat(report.get("summary").get("totalGames").asInt()).isEqualTo(1);
        assertThat(report.get("summary").get("totalShuttlecocks").asInt()).isEqualTo(4);
        assertThat(report.get("players")).hasSize(4);
        assertThat(waiting).isPositive();
    }

    @Test
    void playDateUsesAsiaJakartaTimezone() throws Exception {
        // 23:50 WIB on 12 Oct = 16:50 UTC → belongs to 12 Oct
        playCompletedGame(Instant.parse("2026-10-12T16:20:00Z"), 2, rizal, azis, didi, arif);
        // completes 00:20 WIB on 13 Oct (17:20 UTC 12 Oct) → belongs to 13 Oct, although UTC date is still 12 Oct
        playCompletedGame(Instant.parse("2026-10-12T16:50:00Z"), 2, rizal, hudal, oji, didi);

        JsonNode d12 = range("2026-10-12", "2026-10-12").get("summary");
        JsonNode d13 = range("2026-10-13", "2026-10-13").get("summary");
        assertThat(d12.get("totalGames").asInt()).isEqualTo(1);
        assertThat(d13.get("totalGames").asInt()).isEqualTo(1);
        assertThat(d12.get("courtCost").asInt() + d13.get("courtCost").asInt()).isEqualTo(14000);
    }

    @Test
    void priceChangesDoNotRewriteHistory() throws Exception {
        playCompletedGame(wib("2026-10-12T19:00:00"), 4, rizal, azis, didi, arif);  // 4 players x 4.000

        // Game in progress keeps its snapshot even if the price changes mid-game.
        clock.set(wib("2026-10-12T20:00:00"));
        long running = queue(rizal, hudal, oji, didi);
        start(running);
        addShuttle(running, 1, UUID.randomUUID());

        Map<String, Object> settings = new HashMap<>(Map.of(
                "clubName", "PB PANDAWA", "shuttlecockPrice", 5000, "courtPricePerDay", 10000,
                "timezone", "Asia/Jakarta", "courtSplitMethod", "EQUAL_SPLIT",
                "selfRegistrationEnabled", false, "scoreEnabled", true, "correctionRequiresReason", true,
                "userExportEnabled", false, "userPermissions", List.of("QUEUE_CREATE", "MATCH_START")));
        expectStatus(mvc.perform(auth(put("/api/settings"), adminToken).content(toJson(settings))).andReturn(), 200);

        complete(running);
        JsonNode day12 = range("2026-10-12", "2026-10-12").get("summary");
        assertThat(day12.get("shuttlecockCost").asInt()).isEqualTo(16000 + 16000); // both at 4.000 snapshot
        assertThat(day12.get("courtCost").asInt()).isEqualTo(7000);                 // court snapshot of the day

        // A new game on a new day uses the new prices.
        playCompletedGame(wib("2026-10-13T19:00:00"), 2, rizal, azis, didi, arif);
        JsonNode day13 = range("2026-10-13", "2026-10-13").get("summary");
        assertThat(day13.get("shuttlecockCost").asInt()).isEqualTo(20000); // 4 players x 5.000
        assertThat(day13.get("courtCost").asInt()).isEqualTo(10000);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='PRICE_CHANGED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void clubOnlyCourtMethodKeepsCourtOutOfPersonalEstimates() throws Exception {
        jdbc.update("UPDATE club_settings SET court_split_method='CLUB_ONLY'");
        playCompletedGame(wib("2026-10-12T19:00:00"), 4, rizal, azis, didi, arif);
        JsonNode report = range("2026-10-12", "2026-10-12");
        assertThat(report.get("summary").get("courtCost").asInt()).isEqualTo(7000);
        assertThat(playerRow(report, "Rizal").get("courtShare").asInt()).isZero();
        assertThat(playerRow(report, "Rizal").get("totalEstimate").asInt()).isEqualTo(4000);
    }

    @Test
    void perPlayerCourtMethodChargesEveryPlayerTheFullDailyPrice() throws Exception {
        jdbc.update("UPDATE club_settings SET court_split_method='PER_PLAYER'");
        playCompletedGame(wib("2026-10-12T19:00:00"), 3, rizal, azis, didi, arif);
        playCompletedGame(wib("2026-10-12T19:40:00"), 2, rizal, hudal, oji, didi);
        JsonNode report = range("2026-10-12", "2026-10-12");
        JsonNode s = report.get("summary");
        assertThat(s.get("shuttlecockCost").asInt()).isEqualTo(32000);  // 2 games x 4 players x 4.000
        assertThat(s.get("courtCost").asInt()).isEqualTo(42000);         // 6 unique players x 7.000
        assertThat(s.get("totalExpense").asInt()).isEqualTo(74000);
        assertThat(report.get("daily").get(0).get("courtCost").asInt()).isEqualTo(42000);
        // Two games on the same day: the court is still paid once per player per day.
        assertThat(playerRow(report, "Rizal").get("courtShare").asInt()).isEqualTo(7000);
        assertThat(playerRow(report, "Rizal").get("totalEstimate").asInt()).isEqualTo(8000 + 7000);
        assertThat(playerRow(report, "Azis").get("totalEstimate").asInt()).isEqualTo(4000 + 7000);
    }

    @Test
    void csvExportContainsDailyRows() throws Exception {
        playCompletedGame(wib("2026-10-12T19:00:00"), 4, rizal, azis, didi, arif);
        var r = mvc.perform(auth(get("/api/reports/export?type=daily&from=2026-10-01&to=2026-10-31"), adminToken))
                .andReturn();
        expectStatus(r, 200);
        String csv = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Tanggal,Game Selesai").contains("2026-10-12,1,4,4,16000,7000,EQUAL_SPLIT,23000");
        assertThat(LocalDate.parse("2026-10-12")).isNotNull();
    }
}
