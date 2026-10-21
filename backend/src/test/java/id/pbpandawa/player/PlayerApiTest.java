package id.pbpandawa.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import id.pbpandawa.IntegrationTestBase;
import id.pbpandawa.user.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerApiTest extends IntegrationTestBase {

    @Test
    void adminCanRegisterPlayer() throws Exception {
        var r = mvc.perform(auth(post("/api/players"), adminToken).content(toJson(Map.of(
                "fullName", "  Rizal   Fahmi ", "nickname", "Rizal", "phone", "0812-3456-7890")))).andReturn();
        expectStatus(r, 201);
        assertThat(body(r).get("fullName").asText()).isEqualTo("Rizal Fahmi");
        assertThat(body(r).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body(r).get("registeredOn").asText()).isEqualTo("2026-10-12");
        assertThat(playerRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateNicknameIsRejectedCaseInsensitive() throws Exception {
        player("Didi");
        var r = mvc.perform(auth(post("/api/players"), adminToken).content(toJson(Map.of(
                "fullName", "Didi Lain", "nickname", "didi")))).andReturn();
        expectStatus(r, 409);
        assertThat(body(r).get("code").asText()).isEqualTo("DUPLICATE_PLAYER");
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        var r = mvc.perform(auth(post("/api/players"), adminToken).content(toJson(Map.of("fullName", "", "nickname", ""))))
                .andReturn();
        expectStatus(r, 400);
        assertThat(body(r).get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body(r).get("fieldErrors")).hasSize(2);
    }

    @Test
    void deactivatingKeepsPlayerAndHistory() throws Exception {
        Player p = player("Arif");
        var r = mvc.perform(auth(patch("/api/players/" + p.getId() + "/status"), adminToken)
                .content(toJson(Map.of("status", "INACTIVE")))).andReturn();
        expectStatus(r, 200);
        assertThat(playerRepository.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PlayerStatus.INACTIVE);
        var list = mvc.perform(auth(get("/api/players?status=INACTIVE"), adminToken)).andReturn();
        assertThat(body(list).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void userCannotCreatePlayersOrSeeOthersPhone() throws Exception {
        Player rizal = player("Rizal");
        Player other = playerRepository.saveAndFlush(new Player("Oji Lengkap", "Oji", "08123", java.time.LocalDate.now()));
        createUser("rizal", Role.USER, rizal);
        String token = token("rizal");
        expectStatus(mvc.perform(auth(post("/api/players"), token).content(toJson(Map.of(
                "fullName", "X", "nickname", "X")))).andReturn(), 403);
        var r = mvc.perform(auth(get("/api/players/" + other.getId()), token)).andReturn();
        expectStatus(r, 200);
        assertThat(body(r).get("phone").isNull()).isTrue();
    }
}
