package id.pbpandawa.report;

import id.pbpandawa.common.BusinessException;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchService;
import id.pbpandawa.match.MatchService.HistoryFilter;
import id.pbpandawa.player.PlayerDtos.PlayerRef;
import id.pbpandawa.report.ReportDtos.DailyReport;
import id.pbpandawa.report.ReportDtos.DailyRow;
import id.pbpandawa.report.ReportDtos.DashboardReport;
import id.pbpandawa.report.ReportDtos.MonthlyReport;
import id.pbpandawa.report.ReportDtos.PlayerRow;
import id.pbpandawa.report.ReportDtos.PlayerStatistics;
import id.pbpandawa.report.ReportDtos.RangeReport;
import id.pbpandawa.report.ReportService.DateRange;
import id.pbpandawa.report.ReportService.Period;
import id.pbpandawa.security.AuthUser;
import id.pbpandawa.security.CurrentUser;
import id.pbpandawa.security.Permission;
import id.pbpandawa.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ReportService service;
    private final MatchService matchService;
    private final SettingsService settingsService;

    public ReportController(ReportService service, MatchService matchService, SettingsService settingsService) {
        this.service = service;
        this.matchService = matchService;
        this.settingsService = settingsService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Dashboard admin untuk periode (TODAY/WEEK/MONTH/CUSTOM)")
    public DashboardReport dashboard(@RequestParam(defaultValue = "TODAY") Period period,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange r = service.resolve(period, from, to);
        return service.dashboard(r.from(), r.to());
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public DailyReport daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date);
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Rekap bulanan, parameter month=YYYY-MM")
    public MonthlyReport monthly(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.monthly(month);
    }

    @GetMapping("/range")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Rekap range date (inklusif, tanggal lokal klub)")
    public RangeReport range(@RequestParam(defaultValue = "CUSTOM") Period period,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange r = service.resolve(period, from, to);
        return service.range(r.from(), r.to());
    }

    @GetMapping("/players")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Rekap per pemain untuk periode")
    public List<PlayerRow> players(@RequestParam(defaultValue = "CUSTOM") Period period,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange r = service.resolve(period, from, to);
        return service.range(r.from(), r.to()).players();
    }

    @GetMapping("/me")
    @Operation(summary = "Statistik & estimasi biaya pribadi user yang login")
    public PlayerStatistics me(@RequestParam(defaultValue = "MONTH") Period period,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AuthUser me = CurrentUser.get();
        if (me.playerId() == null) {
            throw BusinessException.conflict("NO_PLAYER_PROFILE", "Akun Anda belum terhubung dengan profil pemain");
        }
        DateRange r = service.resolve(period, from, to);
        return service.playerStatistics(me.playerId(), r.from(), r.to());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Ekspor CSV: type=players|matches|daily")
    public ResponseEntity<byte[]> export(@RequestParam String type,
                                         @RequestParam(defaultValue = "CUSTOM") Period period,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AuthUser me = CurrentUser.get();
        boolean admin = me.has(Permission.REPORT_VIEW);
        if (!admin && !(settingsService.current().isUserExportEnabled() && "matches".equals(type) && me.playerId() != null)) {
            throw BusinessException.forbidden("Ekspor laporan hanya untuk admin");
        }
        DateRange r = service.resolve(period, from, to);
        String csv = switch (type) {
            case "players" -> playersCsv(service.range(r.from(), r.to()).players());
            case "daily" -> dailyCsv(service.range(r.from(), r.to()).daily());
            case "matches" -> matchesCsv(matchService.historyAll(
                    new HistoryFilter(r.from(), r.to(), admin ? null : me.playerId(), null)));
            default -> throw BusinessException.badRequest("INVALID_TYPE", "type harus players, matches, atau daily");
        };
        String filename = "pandawa-" + type + "-" + r.from() + "_" + r.to() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String playersCsv(List<PlayerRow> rows) {
        CsvWriter w = new CsvWriter().row("ID Pemain", "Nickname", "Nama Lengkap", "Total Bermain", "Hari Bermain",
                "Alokasi Kok", "Biaya Kok Pribadi", "Alokasi Biaya Lapangan", "Total Estimasi", "Menang", "Kalah");
        rows.forEach(p -> w.row(p.playerId(), p.nickname(), p.fullName(), p.games(), p.playDays(), p.shuttlecocks(),
                p.shuttlecockCost(), p.courtShare(), p.totalEstimate(), p.wins(), p.losses()));
        return w.toString();
    }

    private String dailyCsv(List<DailyRow> rows) {
        CsvWriter w = new CsvWriter().row("Tanggal", "Game Selesai", "Pemain Unik", "Kok Terpakai", "Biaya Kok Klub",
                "Biaya Lapangan", "Metode Lapangan", "Total Pengeluaran");
        rows.forEach(d -> w.row(d.date(), d.games(), d.uniquePlayers(), d.shuttlecocks(), d.shuttlecockCost(),
                d.courtCost(), d.courtSplitMethod(), d.totalExpense()));
        return w.toString();
    }

    private String matchesCsv(List<MatchResponse> rows) {
        ZoneId zone = settingsService.current().zoneId();
        CsvWriter w = new CsvWriter().row("ID Game", "Tanggal", "Mulai", "Selesai", "Durasi (menit)", "Pasangan 1",
                "Pasangan 2", "Jumlah Kok", "Harga Kok", "Biaya Kok", "Pemenang", "Skor", "Catatan", "Dimulai oleh",
                "Diselesaikan oleh");
        rows.forEach(m -> w.row(m.id(), m.playDate(),
                m.startedAt() == null ? "" : TIME.format(m.startedAt().atZone(zone)),
                m.completedAt() == null ? "" : TIME.format(m.completedAt().atZone(zone)),
                m.durationSeconds() == null ? "" : Math.round(m.durationSeconds() / 60.0),
                names(m.team1()), names(m.team2()), m.shuttlecockCount(), m.shuttlecockPrice(), m.shuttlecockCost(),
                m.winnerTeam() == null ? "" : "Pasangan " + m.winnerTeam(), m.score(), m.notes(), m.startedBy(),
                m.completedBy()));
        return w.toString();
    }

    private static String names(List<PlayerRef> team) {
        return team.stream().map(PlayerRef::nickname).collect(Collectors.joining(" - "));
    }
}
