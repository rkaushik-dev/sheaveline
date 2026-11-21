package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import jakarta.servlet.Filter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * One key ring and one application for the whole suite, both built once.
 *
 * <p>Minting four keys in the set up of every http test would cost more than the tests, and none of
 * them cares which four keys they are, so the ring is minted once. The application is the test
 * context the http tests share: they all import {@link Service}, so Spring builds it once and hands
 * the same one to every class, over the suite's database, which each test empties before it starts.
 *
 * <p>No class under this package in the tests carries a Spring stereotype, because the factory
 * scans the package: what a test adds to an application of its own it names explicitly.
 *
 * <p>The tests that are about the factory, or need a copy with other settings than the suite's,
 * start their own with {@link #freshClient}.
 */
public final class Wire {

    /** The day the suite's service thinks it is. */
    public static final LocalDate TODAY = LocalDate.of(2025, 1, 15);

    /** When the suite's keys were issued. */
    public static final LocalDate ISSUED = LocalDate.of(2024, 11, 1);

    /** When the suite's keys run out. */
    public static final LocalDate EXPIRES = LocalDate.of(2025, 12, 1);

    /** Start an application on a port of its own, so several can run at once. */
    public static final String RANDOM_PORT = "--server.port=0";

    /** One key of each role. */
    public static final KeyRing RING;

    private static final Map<Role, String> SECRETS = new EnumMap<>(Role.class);

    static {
        List<ApiKey> keys = new ArrayList<>();
        for (Role role : Role.values()) {
            ApiKey.Issued minted = ApiKey.issue(
                    role.value() + " client",
                    role,
                    ISSUED,
                    EXPIRES,
                    Optional.empty(),
                    Optional.of(String.format(Locale.ROOT, "%012d", role.ordinal())));
            SECRETS.put(role, minted.presented());
            keys.add(minted.key());
        }
        RING = KeyRing.of(keys);
    }

    private Wire() {}

    /**
     * What a reader puts on a request to be allowed to make it.
     *
     * @return the headers
     */
    public static Map<String, String> headers() {
        return headers(Role.READER);
    }

    /**
     * What a client with a role puts on a request to be allowed to make it.
     *
     * @param role the role
     * @return the headers
     */
    public static Map<String, String> headers(Role role) {
        return Map.of(Gate.API_KEY_HEADER, SECRETS.get(role));
    }

    /**
     * The suite's settings: its database, emptied, its keys and its today.
     *
     * @return the settings
     */
    public static Settings settings() {
        return settings(TODAY);
    }

    /**
     * The suite's settings on another day.
     *
     * @param today the day
     * @return the settings
     */
    public static Settings settings(LocalDate today) {
        return Settings.of(TestDatabase.fresh()).withKeys(RING).withToday(() -> today);
    }

    /**
     * A client on an application of its own, started through the factory the way the service is
     * started, for the tests that are about the factory or need other settings.
     *
     * @param settings what the application is started with
     * @return the running application and a client on it, which the caller closes
     */
    public static Running freshClient(Settings settings) {
        return new Running(SheavelineApplication.run(settings, RANDOM_PORT));
    }

    /**
     * A client on an application started on its own.
     *
     * @param application the application
     * @return the client
     */
    public static Http clientOf(ConfigurableApplicationContext application) {
        return new Http(MockMvcBuilders.webAppContextSetup((WebApplicationContext) application)
                .addFilters(application.getBeansOfType(Filter.class).values().toArray(Filter[]::new))
                .build());
    }

    /**
     * An application started on its own, with a client on it.
     *
     * @param application the application
     */
    public record Running(ConfigurableApplicationContext application) implements AutoCloseable {

        /**
         * A client on the application.
         *
         * @return the client
         */
        public Http client() {
            return clientOf(application);
        }

        @Override
        public void close() {
            application.close();
        }
    }

    /**
     * What the suite's application is handed in place of the settings the factory would get.
     *
     * <p>A plain class rather than a test configuration, so an application a test starts through the
     * factory, which scans this package, does not pick it up as well.
     */
    public static class Service {

        @Bean
        Settings settings() {
            return Wire.settings();
        }
    }
}
