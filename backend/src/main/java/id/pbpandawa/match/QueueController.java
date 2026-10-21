package id.pbpandawa.match;

import id.pbpandawa.match.MatchDtos.CancelRequest;
import id.pbpandawa.match.MatchDtos.CreateMatchRequest;
import id.pbpandawa.match.MatchDtos.MatchResponse;
import id.pbpandawa.match.MatchDtos.MoveRequest;
import id.pbpandawa.match.MatchDtos.QueueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
@Tag(name = "Queue")
public class QueueController {

    private final QueueService service;

    public QueueController(QueueService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Antrean aktif + game yang sedang berlangsung")
    public QueueResponse queue() {
        return service.queue();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tambah pertandingan (2 vs 2) ke antrean")
    public MatchResponse create(@Valid @RequestBody CreateMatchRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}/position")
    @Operation(summary = "Pindahkan pertandingan ke posisi tertentu (1 = terdepan)")
    public List<MatchResponse> move(@PathVariable Long id, @Valid @RequestBody MoveRequest req) {
        return service.move(id, req.position());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hapus pertandingan yang belum dimulai (disimpan sebagai CANCELLED)")
    public void remove(@PathVariable Long id, @RequestBody(required = false) CancelRequest req) {
        service.remove(id, req == null ? null : req.reason());
    }
}
