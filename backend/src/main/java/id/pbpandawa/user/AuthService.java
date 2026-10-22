package id.pbpandawa.user;

import id.pbpandawa.audit.AuditService;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.player.Player;
import id.pbpandawa.player.PlayerDtos.PlayerRequest;
import id.pbpandawa.player.PlayerService;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.JwtService;
import id.pbpandawa.security.LoginAttemptService;
import id.pbpandawa.security.PermissionService;
import id.pbpandawa.settings.SettingsService;
import id.pbpandawa.user.UserDtos.ChangePasswordRequest;
import id.pbpandawa.user.UserDtos.LoginRequest;
import id.pbpandawa.user.UserDtos.LoginResponse;
import id.pbpandawa.user.UserDtos.MeResponse;
import id.pbpandawa.user.UserDtos.RegisterRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    /** Dummy hash so that unknown usernames take as long as wrong passwords (no user enumeration). */
    private final String dummyHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final LoginAttemptService loginAttempts;
    private final SettingsService settingsService;
    private final PlayerService playerService;
    private final AuditService auditService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       PermissionService permissionService, LoginAttemptService loginAttempts,
                       SettingsService settingsService, PlayerService playerService, AuditService auditService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.permissionService = permissionService;
        this.loginAttempts = loginAttempts;
        this.settingsService = settingsService;
        this.playerService = playerService;
        this.auditService = auditService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * @param deviceId id from a valid device cookie for this username, or null. Trusted devices are throttled
     *                 on their own, so failed attempts by others cannot lock the owner out.
     */
    @Transactional
    public LoginResponse login(LoginRequest req, String deviceId) {
        String username = req.username().trim();
        String bucket = deviceId != null
                ? LoginAttemptService.deviceBucket(deviceId) : LoginAttemptService.usernameBucket(username);
        if (loginAttempts.isLocked(bucket)) {
            log.warn("Login blocked (too many attempts): username='{}' trustedDevice={}", username, deviceId != null);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Terlalu banyak percobaan login. Coba lagi dalam 5 menit.");
        }
        User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        boolean ok = passwordEncoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok || !user.isEnabled()) {
            loginAttempts.onFailure(bucket);
            // Never log the password itself: only hints that help diagnose typing / autofill problems.
            String pw = req.password();
            log.warn("Login failed: username='{}' reason={} passwordLength={} hasWhitespace={} hasUppercase={}",
                    username, user == null ? "UNKNOWN_USER" : !ok ? "WRONG_PASSWORD" : "DISABLED",
                    pw.length(), pw.chars().anyMatch(Character::isWhitespace), pw.chars().anyMatch(Character::isUpperCase));
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Username atau password salah");
        }
        loginAttempts.onSuccess(bucket);
        user.setLastLoginAt(Instant.now(clock));
        var token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), me(user));
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (!settingsService.current().isSelfRegistrationEnabled()) {
            throw BusinessException.forbidden("Pendaftaran mandiri sedang dinonaktifkan oleh admin");
        }
        if (userRepository.existsByUsernameIgnoreCase(req.username().trim())) {
            throw BusinessException.conflict("DUPLICATE_USERNAME", "Username sudah digunakan");
        }
        Player player = playerService.createEntity(
                new PlayerRequest(req.fullName(), req.nickname(), req.phone(), null, null));
        User user = new User(req.username().trim(), passwordEncoder.encode(req.password()), Role.USER);
        user.setPlayer(player);
        user.setLastLoginAt(Instant.now(clock));
        userRepository.saveAndFlush(user);
        // Act as the new user for the audit trail.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthUser(user.getId(), user.getUsername(), Role.USER, player.getId(), java.util.Set.of()), null,
                java.util.List.of()));
        auditService.record("SELF_REGISTERED", "USER", user.getId(),
                "Pendaftaran mandiri: " + user.getUsername() + " (" + player.getNickname() + ")", Map.of());
        var token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), me(user));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return me(userRepository.findWithPlayerById(userId).orElseThrow(() -> BusinessException.notFound("User")));
    }

    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(User::invalidateTokens);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("INVALID_PASSWORD", "Password lama salah");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.invalidateTokens();
        auditService.record("PASSWORD_CHANGED", "USER", userId, "Password diubah oleh pemilik akun", Map.of());
    }

    private MeResponse me(User u) {
        Player p = u.getPlayer();
        return new MeResponse(u.getId(), u.getUsername(), u.getRole(),
                p != null ? p.getId() : null, p != null ? p.getNickname() : null, p != null ? p.getFullName() : null,
                permissionService.permissionsOf(u.getRole()));
    }
}
