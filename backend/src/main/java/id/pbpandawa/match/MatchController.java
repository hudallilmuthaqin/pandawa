package id.pbpandawa.match;

import id.pbpandawa.common.PageResponse;
import id.pbpandawa.match.MatchDtos.CancelRequest;
import id.pbpandawa.match.MatchDtos.CompleteRequest;
import id.pbpandawa.match.MatchDtos.CorrectionRequest;
import id.pbpandawa.match.MatchDtos.MatchDetailResponse;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchDtos.ShuttlecockRequest;
import id.pbpandawa.match.MatchService.HistoryFilter;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.security.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@Tag(name = "Matches")
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Daftar semua pertandingan (admin), opsional filter status")
    public PageResponse<MatchResponse> list(@RequestParam(required = false) MatchStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/current")
    @Operation(summary = "Game yang sedang berlangsung (204 jika tidak ada)")
    public ResponseEntity<MatchResponse> current() {
        return service.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    @Operation(summary = "Riwayat game selesai. User non-admin hanya melihat game yang ia ikuti.")
    public PageResponse<MatchResponse> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(new HistoryFilter(from, to, scopedPlayer(playerId), q), page, size);
    }

    @GetMapping("/{id}")
    public MatchDetailResponse get(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/start-next")
    @Operation(summary = "Mulai pertandingan terdepan di antrean")
    public MatchResponse startNext() {
        return service.startNext();
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Mulai pertandingan (user: hanya yang terdepan; admin: boleh memilih)")
    public MatchResponse start(@PathVariable Long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/shuttlecocks")
    @Operation(summary = "Catat kok (+1) atau koreksi (-1). requestId wajib untuk idempotensi.")
    public MatchResponse shuttlecocks(@PathVariable Long id, @Valid @RequestBody ShuttlecockRequest req) {
        return service.recordShuttlecock(id, req);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Selesaikan pertandingan (transaksional, tidak dapat diulang)")
    public MatchResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRequest req) {
        return service.complete(id, req == null ? new CompleteRequest(null, null, null, null) : req);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Batalkan pertandingan (antrean: pembuat/admin; berlangsung: admin)")
    public MatchResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }

    @PostMapping("/{id}/corrections")
    @PreAuthorize("hasAuthority('MATCH_CORRECT')")
    @Operation(summary = "Koreksi jumlah kok game yang sudah selesai (admin, tercatat di audit log)")
    public MatchResponse correct(@PathVariable Long id, @Valid @RequestBody CorrectionRequest req) {
        return service.correctCompleted(id, req);
    }

    private static Long scopedPlayer(Long requested) {
        AuthUser me = CurrentUser.get();
        if (me.has(Permission.REPORT_VIEW)) {
            return requested;
        }
        // Users see only their own history; no player profile → match nothing.
        return me.playerId() != null ? me.playerId() : -1L;
    }
}
