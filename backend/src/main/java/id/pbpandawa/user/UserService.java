package id.pbpandawa.user;

import id.pbpandawa.audit.AuditService;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.player.Player;
import id.pbpandawa.player.PlayerService;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.user.UserDtos.CreateUserRequest;
import id.pbpandawa.user.UserDtos.UpdateUserRequest;
import id.pbpandawa.user.UserDtos.UserResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository repository;
    private final PlayerService playerService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository repository, PlayerService playerService, PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.repository = repository;
        this.playerService = playerService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return repository.findAllWithPlayer().stream().map(UserResponse::of).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (repository.existsByUsernameIgnoreCase(req.username().trim())) {
            throw BusinessException.conflict("DUPLICATE_USERNAME", "Username sudah digunakan");
        }
        User user = new User(req.username().trim(), passwordEncoder.encode(req.password()), req.role());
        user.setPlayer(resolvePlayer(req.playerId(), null));
        repository.saveAndFlush(user);
        auditService.record("USER_CREATED", "USER", user.getId(),
                "Akun dibuat: " + user.getUsername() + " (" + user.getRole() + ")", Map.of());
        return UserResponse.of(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = repository.findWithPlayerById(id).orElseThrow(() -> BusinessException.notFound("User"));
        boolean isSelf = Objects.equals(CurrentUser.get().id(), id);
        boolean losesAdmin = user.getRole() == Role.ADMIN && (req.role() != Role.ADMIN || !req.enabled());
        if (losesAdmin && isSelf) {
            throw BusinessException.badRequest("SELF_DEMOTION", "Anda tidak dapat menurunkan / menonaktifkan akun sendiri");
        }
        if (losesAdmin && user.isEnabled() && repository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw BusinessException.badRequest("LAST_ADMIN", "Minimal harus ada satu admin aktif");
        }
        String before = user.getRole() + "/" + (user.isEnabled() ? "aktif" : "nonaktif");
        boolean securityChanged = user.getRole() != req.role() || user.isEnabled() != req.enabled();
        user.setRole(req.role());
        user.setEnabled(req.enabled());
        user.setPlayer(resolvePlayer(req.playerId(), user.getId()));
        if (securityChanged) {
            user.invalidateTokens();
        }
        repository.flush();
        auditService.record(securityChanged ? "USER_ROLE_CHANGED" : "USER_UPDATED", "USER", id,
                "Akun " + user.getUsername() + ": " + before + " → " + user.getRole() + "/"
                        + (user.isEnabled() ? "aktif" : "nonaktif"),
                Map.of("playerId", String.valueOf(req.playerId())));
        return UserResponse.of(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = repository.findById(id).orElseThrow(() -> BusinessException.notFound("User"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.invalidateTokens();
        auditService.record("PASSWORD_RESET", "USER", id, "Password direset oleh admin: " + user.getUsername(), Map.of());
    }

    private Player resolvePlayer(Long playerId, Long selfUserId) {
        if (playerId == null) {
            return null;
        }
        Player player = playerService.find(playerId);
        repository.findByPlayerId(playerId)
                .filter(other -> !other.getId().equals(selfUserId))
                .ifPresent(other -> {
                    throw BusinessException.conflict("PLAYER_ALREADY_LINKED",
                            "Pemain " + player.getNickname() + " sudah terhubung ke akun " + other.getUsername());
                });
        return player;
    }
}
