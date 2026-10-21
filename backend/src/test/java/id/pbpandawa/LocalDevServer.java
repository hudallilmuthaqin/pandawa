package id.pbpandawa;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;

/**
 * Runs the backend locally WITHOUT Docker or an installed PostgreSQL:
 * starts a real embedded PostgreSQL (persistent data in backend/.local-pg)
 * and then the application with the dev profile (demo seed data enabled).
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 */
public final class LocalDevServer {

    private LocalDevServer() {
    }

    public static void main(String[] args) throws Exception {
        File dataDir = new File(".local-pg");
        boolean fresh = !new File(dataDir, "PG_VERSION").exists();
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .setPort(Integer.parseInt(System.getProperty("pg.port", "54329")))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // shutting down
            }
        }));
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        // The throwaway embedded database keeps demo data for quick manual testing.
        System.setProperty("app.seed.enabled", System.getProperty("app.seed.enabled", "true"));
        System.out.println("Embedded PostgreSQL on port " + pg.getPort() + (fresh ? " (new data dir)" : ""));
        PandawaApplication.main(args);
    }
}
