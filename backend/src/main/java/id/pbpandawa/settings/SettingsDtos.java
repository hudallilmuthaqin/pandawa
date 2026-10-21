package id.pbpandawa.settings;

import id.pbpandawa.security.Permission;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsResponse(
            String clubName,
            BigDecimal shuttlecockPrice,
            BigDecimal courtPricePerDay,
            int courtCount,
            String timezone,
            CourtSplitMethod courtSplitMethod,
            boolean selfRegistrationEnabled,
            boolean scoreEnabled,
            boolean correctionRequiresReason,
            boolean userExportEnabled,
            Set<Permission> userPermissions,
            long version,
            Instant updatedAt) {
    }

    public record PublicSettingsResponse(String clubName, boolean selfRegistrationEnabled) {
    }

    public record UpdateSettingsRequest(
            @NotBlank(message = "Nama klub wajib diisi") @Size(max = 80) String clubName,
            @NotNull(message = "Harga kok wajib diisi")
            @DecimalMin(value = "0", message = "Harga kok tidak boleh negatif")
            @DecimalMax(value = "1000000", message = "Harga kok terlalu besar") BigDecimal shuttlecockPrice,
            @NotNull(message = "Harga lapangan wajib diisi")
            @DecimalMin(value = "0", message = "Harga lapangan tidak boleh negatif")
            @DecimalMax(value = "10000000", message = "Harga lapangan terlalu besar") BigDecimal courtPricePerDay,
            @NotBlank String timezone,
            @NotNull CourtSplitMethod courtSplitMethod,
            boolean selfRegistrationEnabled,
            boolean scoreEnabled,
            boolean correctionRequiresReason,
            boolean userExportEnabled,
            @NotNull Set<Permission> userPermissions,
            Long version) {
    }

    public record PermissionInfo(Permission permission, String description, boolean grantableToUser) {
    }

    public record PermissionCatalog(List<PermissionInfo> permissions) {
    }
}
