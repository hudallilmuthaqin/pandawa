package id.pbpandawa.user;

import id.pbpandawa.security.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public static final String USERNAME_REGEX = "^[a-zA-Z0-9._@-]{3,60}$";

    public record LoginRequest(@NotBlank(message = "Username wajib diisi") String username,
                               @NotBlank(message = "Password wajib diisi") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Nama lengkap wajib diisi") @Size(max = 100) String fullName,
            @NotBlank(message = "Nickname wajib diisi") @Size(max = 40) String nickname,
            @Size(max = 30) String phone,
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "Username 3-60 karakter (huruf, angka, . _ @ -)") String username,
            @NotBlank @Size(min = 8, max = 72, message = "Password minimal 8 karakter") String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72, message = "Password minimal 8 karakter") String newPassword) {
    }

    public record MeResponse(Long id, String username, Role role, Long playerId, String playerNickname,
                             String playerFullName, Set<Permission> permissions) {
    }

    public record LoginResponse(String token, Instant expiresAt, MeResponse user) {
    }

    public record UserResponse(Long id, String username, Role role, boolean enabled, Long playerId,
                               String playerNickname, Instant lastLoginAt, Instant createdAt) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getRole(), u.isEnabled(),
                    u.getPlayer() != null ? u.getPlayer().getId() : null,
                    u.getPlayer() != null ? u.getPlayer().getNickname() : null,
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "Username 3-60 karakter (huruf, angka, . _ @ -)") String username,
            @NotBlank @Size(min = 8, max = 72, message = "Password minimal 8 karakter") String password,
            @NotNull Role role,
            Long playerId) {
    }

    public record UpdateUserRequest(@NotNull Role role, boolean enabled, Long playerId) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72, message = "Password minimal 8 karakter") String newPassword) {
    }
}
