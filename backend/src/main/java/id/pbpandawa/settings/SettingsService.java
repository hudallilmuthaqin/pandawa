package id.pbpandawa.settings;

import id.pbpandawa.audit.AuditService;
import id.pbpandawa.common.BusinessException;
import id.pbpandawa.common.Money;
import id.pbpandawa.security.Permission;
import id.pbpandawa.security.PermissionService;
import id.pbpandawa.settings.SettingsDtos.SettingsResponse;
import id.pbpandawa.settings.SettingsDtos.UpdateSettingsRequest;
import id.pbpandawa.user.Role;
import id.pbpandawa.user.RolePermission;
import id.pbpandawa.user.RolePermissionRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final ClubSettingsRepository repository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public SettingsService(ClubSettingsRepository repository, RolePermissionRepository rolePermissionRepository,
                           PermissionService permissionService, AuditService auditService) {
        this.repository = repository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ClubSettings current() {
        return repository.findById(ClubSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("club_settings row missing"));
    }

    /** Acquires the club-wide row lock used to serialise queue/court operations. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ClubSettings lockForUpdate() {
        return repository.lockById(ClubSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("club_settings row missing"));
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return toResponse(current());
    }

    @Transactional
    public SettingsResponse update(UpdateSettingsRequest req) {
        ClubSettings s = lockForUpdate();
        if (req.version() != null && req.version() != s.getVersion()) {
            throw BusinessException.conflict("CONCURRENT_MODIFICATION",
                    "Pengaturan baru saja diubah admin lain. Muat ulang lalu coba lagi.");
        }
        if (!Money.isWholeRupiah(req.shuttlecockPrice()) || !Money.isWholeRupiah(req.courtPricePerDay())) {
            throw BusinessException.badRequest("INVALID_PRICE", "Harga harus berupa rupiah bulat");
        }
        try {
            ZoneId.of(req.timezone());
        } catch (DateTimeException e) {
            throw BusinessException.badRequest("INVALID_TIMEZONE", "Zona waktu tidak dikenal");
        }
        for (Permission p : req.userPermissions()) {
            if (!p.isGrantableToUser()) {
                throw BusinessException.badRequest("PERMISSION_NOT_GRANTABLE",
                        "Izin " + p + " hanya untuk admin");
            }
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "clubName", s.getClubName(), req.clubName().trim());
        diff(changes, "shuttlecockPrice", Money.normalize(s.getShuttlecockPrice()), Money.normalize(req.shuttlecockPrice()));
        diff(changes, "courtPricePerDay", Money.normalize(s.getCourtPricePerDay()), Money.normalize(req.courtPricePerDay()));
        diff(changes, "timezone", s.getTimezone(), req.timezone());
        diff(changes, "courtSplitMethod", s.getCourtSplitMethod(), req.courtSplitMethod());
        diff(changes, "selfRegistrationEnabled", s.isSelfRegistrationEnabled(), req.selfRegistrationEnabled());
        diff(changes, "scoreEnabled", s.isScoreEnabled(), req.scoreEnabled());
        diff(changes, "correctionRequiresReason", s.isCorrectionRequiresReason(), req.correctionRequiresReason());
        diff(changes, "userExportEnabled", s.isUserExportEnabled(), req.userExportEnabled());

        boolean priceChanged = changes.containsKey("shuttlecockPrice") || changes.containsKey("courtPricePerDay");

        s.setClubName(req.clubName().trim());
        s.setShuttlecockPrice(Money.normalize(req.shuttlecockPrice()));
        s.setCourtPricePerDay(Money.normalize(req.courtPricePerDay()));
        s.setTimezone(req.timezone());
        s.setCourtSplitMethod(req.courtSplitMethod());
        s.setSelfRegistrationEnabled(req.selfRegistrationEnabled());
        s.setScoreEnabled(req.scoreEnabled());
        s.setCorrectionRequiresReason(req.correctionRequiresReason());
        s.setUserExportEnabled(req.userExportEnabled());

        Set<Permission> before = permissionService.permissionsOf(Role.USER);
        Set<Permission> after = req.userPermissions().isEmpty()
                ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(req.userPermissions());
        if (!before.equals(after)) {
            rolePermissionRepository.deleteByRole(Role.USER);
            rolePermissionRepository.flush();
            after.forEach(p -> rolePermissionRepository.save(new RolePermission(Role.USER, p)));
            changes.put("userPermissions", Map.of("from", before, "to", after));
        }

        if (!changes.isEmpty()) {
            auditService.record(priceChanged ? "PRICE_CHANGED" : "SETTINGS_UPDATED", "SETTINGS", 1L,
                    priceChanged ? "Harga diubah (berlaku untuk game baru)" : "Pengaturan klub diubah", changes);
        }
        repository.flush();
        return toResponse(s);
    }

    private static void diff(Map<String, Object> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, Map.of("from", String.valueOf(before), "to", String.valueOf(after)));
        }
    }

    private SettingsResponse toResponse(ClubSettings s) {
        return new SettingsResponse(s.getClubName(), Money.normalize(s.getShuttlecockPrice()),
                Money.normalize(s.getCourtPricePerDay()), s.getCourtCount(), s.getTimezone(), s.getCourtSplitMethod(),
                s.isSelfRegistrationEnabled(), s.isScoreEnabled(), s.isCorrectionRequiresReason(),
                s.isUserExportEnabled(), permissionService.permissionsOf(Role.USER), s.getVersion(), s.getUpdatedAt());
    }
}
