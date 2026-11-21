package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.Sheaveline;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.schemas.HealthOut;
import io.github.rkaushikdev.sheaveline.api.schemas.ReadyOut;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a load balancer asks, and what somebody on the telephone asks.
 *
 * <p>Two endpoints that look the same and are not. Health says the process is up and answering,
 * which is what a container orchestrator polls every few seconds and what must never touch the
 * database, because a health check that opens a connection is a health check that takes the service
 * down when the disk is slow.
 *
 * <p>Readiness says the database is there and has the tables in it, which is the thing that is
 * actually wrong when the office rings up. It costs a query, so it is not on the fast path and it
 * says plainly which check failed.
 *
 * <p>Neither needs a key. A service that will not say whether it is alive without being
 * authenticated is a service nobody can monitor.
 */
@RestController
@Tag(name = "service")
public class ServiceController {

    /** What answers. */
    public static final String SERVICE = "sheaveline";

    private final Settings settings;

    /**
     * The service routes for one running copy.
     *
     * @param settings its settings
     */
    public ServiceController(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Up and answering. Deliberately does not touch the database.
     *
     * @return the service, its version and its resort
     */
    @GetMapping("/health")
    public HealthOut health() {
        return new HealthOut(SERVICE, Sheaveline.VERSION, settings.resort());
    }

    /**
     * The database is there, has the tables in it, and answers a count.
     *
     * @return what was found: a 200 when ready, a 503 saying which check failed when not
     */
    @GetMapping("/ready")
    @ApiResponse(responseCode = "200", description = "Ready")
    @ApiResponse(
            responseCode = "503",
            description = "Not ready",
            content = @Content(schema = @Schema(implementation = ReadyOut.class)))
    public ResponseEntity<ReadyOut> ready() {
        Database database = settings.database();
        String url = database.url();
        List<String> built;
        try {
            built = database.existingTables();
        } catch (StorageError broken) {
            return notReady(url, List.of(), "the database will not open: " + broken.getMessage());
        }
        TreeSet<String> missing = new TreeSet<>(Database.tableNames());
        missing.removeAll(built);
        if (!missing.isEmpty()) {
            return notReady(url, List.copyOf(missing), missing.size() + " tables are not built yet");
        }
        int machines;
        try (UnitOfWork work = UnitOfWork.on(database).open()) {
            machines = work.lifts().codes().size();
        }
        return ResponseEntity.ok(new ReadyOut(true, url, built.size(), List.of(), machines, settings.today(), "ready"));
    }

    private ResponseEntity<ReadyOut> notReady(String url, List<String> missing, String detail) {
        LocalDate today = settings.today();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ReadyOut(false, url, 0, missing, 0, today, detail));
    }
}
