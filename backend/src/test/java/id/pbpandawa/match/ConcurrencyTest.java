package id.pbpandawa.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import id.pbpandawa.IntegrationTestBase;
import id.pbpandawa.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Simulates several phones pressing buttons at the same moment. */
class ConcurrencyTest extends IntegrationTestBase {

    @Autowired MatchRepository matchRepository;

    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> t : tasks) {
            futures.add(pool.submit(() -> {
                go.await();
                return t.call();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        return statuses;
    }

    @Test
    void onlyOneOfManySimultaneousStartsSucceeds() throws Exception {
        Player a = player("A"), b = player("B"), c = player("C"), d = player("D");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(queue(a, b, c, d));
        }
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (Long id : ids) {
            tasks.add(() -> mvc.perform(auth(post("/api/matches/" + id + "/start"), adminToken)).andReturn()
                    .getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);
        assertThat(statuses.stream().filter(s -> s == 200)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(3);
        assertThat(matchRepository.countByStatus(MatchStatus.IN_PROGRESS)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateShuttleRequestsCountOnce() throws Exception {
        Player a = player("A"), b = player("B"), c = player("C"), d = player("D");
        long id = queue(a, b, c, d);
        start(id);
        UUID requestId = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> addShuttle(id, 1, requestId).getResponse().getStatus());
        }
        // Plus 5 distinct taps that must all count.
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> addShuttle(id, 1, UUID.randomUUID()).getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);
        assertThat(statuses).containsOnly(200);
        assertThat(matchRepository.findById(id).orElseThrow().getShuttlecockCount()).isEqualTo(6);
    }

    @Test
    void concurrentCompletionsCompleteOnce() throws Exception {
        Player a = player("A"), b = player("B"), c = player("C"), d = player("D");
        long id = queue(a, b, c, d);
        start(id);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> complete(id).getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);
        assertThat(statuses.stream().filter(s -> s == 200)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from match_events where type='COMPLETED'", Integer.class))
                .isEqualTo(1);
    }
}
