package id.pbpandawa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import id.pbpandawa.IntegrationTestBase;
import id.pbpandawa.user.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class LoginLockoutTest extends IntegrationTestBase {

    // The lockout state is in memory and shared by all tests in the Spring context: use fresh usernames.
    String owner;

    @BeforeEach
    void setUp() {
        owner = "korban" + UUID.randomUUID().toString().substring(0, 8);
        createUser(owner, Role.ADMIN, null);
    }

    private MvcResult login(String username, String password, String deviceCookie) throws Exception {
        var req = post("/api/auth/login").contentType("application/json")
                .content(toJson(Map.of("username", username, "password", password)));
        if (deviceCookie != null) {
            req.cookie(new Cookie(DeviceCookieService.COOKIE_NAME, deviceCookie));
        }
        return mvc.perform(req).andReturn();
    }

    private String deviceCookieOf(MvcResult r) {
        String header = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(DeviceCookieService.COOKIE_NAME + "=").contains("HttpOnly");
        return header.substring(DeviceCookieService.COOKIE_NAME.length() + 1, header.indexOf(';'));
    }

    private void failFiveTimes(String username, String deviceCookie) throws Exception {
        for (int i = 0; i < 5; i++) {
            expectStatus(login(username, "salah-salah", deviceCookie), 401);
        }
    }

    @Test
    void attackerCannotLockOutOwnerOnTrustedDevice() throws Exception {
        String ownerDevice = deviceCookieOf(login(owner, PASSWORD, null));

        failFiveTimes(owner, null);

        expectStatus(login(owner, PASSWORD, null), 429);
        expectStatus(login(owner, PASSWORD, ownerDevice), 200);
    }

    @Test
    void deviceCookieOnlyCountsForItsOwnUsername() throws Exception {
        createUser(owner + "x", Role.USER, null);
        String otherUsersDevice = deviceCookieOf(login(owner + "x", PASSWORD, null));

        failFiveTimes(owner, null);

        expectStatus(login(owner, PASSWORD, otherUsersDevice), 429);
        expectStatus(login(owner, PASSWORD, "forged.9999999999.signature"), 429);
    }

    @Test
    void wrongPasswordsOnTrustedDeviceLockOnlyThatDevice() throws Exception {
        String device = deviceCookieOf(login(owner, PASSWORD, null));

        failFiveTimes(owner, device);

        expectStatus(login(owner, PASSWORD, device), 429);
        expectStatus(login(owner, PASSWORD, null), 200);
    }

    @Test
    void lockExpiresAfterFiveMinutes() throws Exception {
        failFiveTimes(owner, null);
        expectStatus(login(owner, PASSWORD, null), 429);

        clock.set(DEFAULT_NOW.plusSeconds(5 * 60 + 1));

        expectStatus(login(owner, PASSWORD, null), 200);
    }
}
