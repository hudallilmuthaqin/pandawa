package id.pbpandawa.settings;

import id.pbpandawa.security.Permission;
import id.pbpandawa.settings.SettingsDtos.PermissionCatalog;
import id.pbpandawa.settings.SettingsDtos.PermissionInfo;
import id.pbpandawa.settings.SettingsDtos.PublicSettingsResponse;
import id.pbpandawa.settings.SettingsDtos.SettingsResponse;
import id.pbpandawa.settings.SettingsDtos.UpdateSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Pengaturan klub (semua user login dapat membaca harga & aturan)")
    public SettingsResponse get() {
        return service.get();
    }

    @GetMapping("/public")
    @Operation(summary = "Info publik untuk halaman login")
    public PublicSettingsResponse publicInfo() {
        var s = service.current();
        return new PublicSettingsResponse(s.getClubName(), s.isSelfRegistrationEnabled());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Ubah pengaturan & harga (admin). Harga baru hanya berlaku untuk game baru.")
    public SettingsResponse update(@Valid @RequestBody UpdateSettingsRequest req) {
        return service.update(req);
    }

    @GetMapping("/permissions")
    @Operation(summary = "Katalog permission")
    public PermissionCatalog permissions() {
        return new PermissionCatalog(Arrays.stream(Permission.values())
                .map(p -> new PermissionInfo(p, p.getDescription(), p.isGrantableToUser()))
                .toList());
    }
}
