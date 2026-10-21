package id.pbpandawa.player;

import id.pbpandawa.common.PageResponse;
import id.pbpandawa.player.PlayerDtos.PlayerRequest;
import id.pbpandawa.player.PlayerDtos.PlayerResponse;
import id.pbpandawa.player.PlayerDtos.StatusRequest;
import id.pbpandawa.report.ReportDtos.PlayerStatistics;
import id.pbpandawa.report.ReportService;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.common.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players")
public class PlayerController {

    private final PlayerService service;
    private final ReportService reportService;

    public PlayerController(PlayerService service, ReportService reportService) {
        this.service = service;
        this.reportService = reportService;
    }

    @GetMapping
    @Operation(summary = "Cari pemain (user non-admin tidak melihat nomor telepon orang lain)")
    public PageResponse<PlayerResponse> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) PlayerStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return service.search(q, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PLAYER_MANAGE')")
    public PlayerResponse create(@Valid @RequestBody PlayerRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public PlayerResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLAYER_MANAGE')")
    public PlayerResponse update(@PathVariable Long id, @Valid @RequestBody PlayerRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PLAYER_MANAGE')")
    @Operation(summary = "Aktifkan / nonaktifkan pemain (riwayat tetap tersimpan)")
    public PlayerResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(id, req.status());
    }

    @GetMapping("/{id}/statistics")
    @Operation(summary = "Statistik pemain. User hanya dapat melihat statistik miliknya sendiri.")
    public PlayerStatistics statistics(@PathVariable Long id,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AuthUser me = CurrentUser.get();
        if (!me.isAdmin() && !id.equals(me.playerId())) {
            throw BusinessException.forbidden("Anda hanya dapat melihat statistik milik sendiri");
        }
        return reportService.playerStatistics(id, from, to);
    }
}
