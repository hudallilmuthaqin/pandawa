package id.pbpandawa.user;

import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.user.UserDtos.ChangePasswordRequest;
import id.pbpandawa.user.UserDtos.LoginRequest;
import id.pbpandawa.user.UserDtos.LoginResponse;
import id.pbpandawa.user.UserDtos.MeResponse;
import id.pbpandawa.user.UserDtos.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pendaftaran mandiri pemain (jika diizinkan admin)")
    public LoginResponse register(@Valid @RequestBody RegisterRequest req) {
        return service.register(req);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return service.me(CurrentUser.get().id());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout: mencabut semua token milik user ini")
    public void logout() {
        service.logout(CurrentUser.get().id());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        service.changePassword(CurrentUser.get().id(), req);
    }
}
