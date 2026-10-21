package id.pbpandawa.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class PlayerDtos {

    private PlayerDtos() {
    }

    public record PlayerRequest(
            @NotBlank(message = "Nama lengkap wajib diisi") @Size(max = 100, message = "Nama maksimal 100 karakter")
            String fullName,
            @NotBlank(message = "Nickname wajib diisi") @Size(max = 40, message = "Nickname maksimal 40 karakter")
            String nickname,
            @Size(max = 30) @Pattern(regexp = "^$|^[0-9+\\-\\s()]{6,30}$", message = "Nomor telepon tidak valid")
            String phone,
            LocalDate registeredOn,
            PlayerStatus status) {
    }

    public record StatusRequest(@NotNull PlayerStatus status) {
    }

    /** phone and username are only populated for admins. */
    public record PlayerResponse(
            Long id,
            String fullName,
            String nickname,
            String phone,
            PlayerStatus status,
            LocalDate registeredOn,
            String username,
            long totalGames,
            Instant createdAt) {
    }

    /** Minimal info used inside match cards. */
    public record PlayerRef(Long id, String nickname, String fullName, PlayerStatus status) {
        public static PlayerRef of(Player p) {
            return new PlayerRef(p.getId(), p.getNickname(), p.getFullName(), p.getStatus());
        }
    }
}
