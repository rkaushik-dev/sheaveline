package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.journal.Level;
import io.github.rkaushikdev.sheaveline.util.Quote;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * One running copy's settings, read once at startup from the environment.
 *
 * <p>The command line takes a database url and a journal level, which is right for somebody at a
 * keyboard and wrong for the box in the equipment room that starts this on boot. That box wants
 * its settings in the environment, where they are set once and read by whatever supervises the
 * process.
 *
 * <p>Everything has a default that works on a laptop, so the program starts with nothing set. The
 * one thing with no default is the key file, because a server with no keys refuses every request
 * and doing that silently is worse than saying so.
 *
 * <p>The reading is strict on purpose. A journal level nobody has and a port that is not a number
 * are configuration mistakes, and finding out at startup with a sentence naming the variable is
 * better than finding out at three in the afternoon with a stack trace.
 *
 * @param database where the records live, as a JDBC url
 * @param resort the name on the printed pages
 * @param journal how much the software says about itself
 * @param keyFile the api key file, when one is set
 * @param host where the http surface listens
 * @param port the port it listens on
 * @param journalGiven whether the journal level was actually named, which is not the same question
 *     as what it is: the server wants a journal by default and the command line does not, and
 *     neither can decide that from a level that has already been defaulted
 */
public record Configuration(
        String database,
        String resort,
        Level journal,
        Optional<Path> keyFile,
        String host,
        int port,
        boolean journalGiven) {

    /** What every variable this program reads starts with. */
    public static final String PREFIX = "SHEAVELINE_";

    /** Where the records live. */
    public static final String DATABASE = PREFIX + "DATABASE_URL";

    /** The name on the printed pages. */
    public static final String RESORT = PREFIX + "RESORT";

    /** What the software says about itself. */
    public static final String JOURNAL = PREFIX + "JOURNAL";

    /** The api keys. */
    public static final String KEYS = PREFIX + "KEY_FILE";

    /** The port the http surface listens on. */
    public static final String PORT = PREFIX + "PORT";

    /** The address the http surface listens on. */
    public static final String HOST = PREFIX + "HOST";

    /** A database file beside the program. */
    public static final String DEFAULT_DATABASE = "jdbc:h2:file:./sheaveline";

    /** The hill this was written for. */
    public static final String DEFAULT_RESORT = "Ogden Peak";

    /** The port the office has always used. */
    public static final int DEFAULT_PORT = 8451;

    /** Listening on this machine only. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Every variable read, in the order they are described. */
    public static final List<String> EVERY_NAME = List.of(DATABASE, RESORT, JOURNAL, KEYS, HOST, PORT);

    /** The settings, with nothing optional left null. */
    public Configuration {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(resort, "resort");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(keyFile, "keyFile");
        Objects.requireNonNull(host, "host");
    }

    /**
     * The laptop defaults: nothing set at all.
     *
     * @return the configuration
     */
    public static Configuration defaults() {
        return new Configuration(
                DEFAULT_DATABASE, DEFAULT_RESORT, Level.INFO, Optional.empty(), DEFAULT_HOST, DEFAULT_PORT, false);
    }

    /**
     * Read the process environment.
     *
     * @return the configuration
     * @throws ConfigurationError naming the variable that is wrong
     */
    public static Configuration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Read the given environment, and say which variable is wrong rather than which line broke.
     *
     * @param env the variables, by name
     * @return the configuration
     * @throws ConfigurationError naming the variable that is wrong
     */
    public static Configuration fromEnvironment(Map<String, String> env) {
        String keys = found(env, KEYS);
        return new Configuration(
                text(env, DATABASE, DEFAULT_DATABASE),
                text(env, RESORT, DEFAULT_RESORT),
                level(env),
                keys.isEmpty() ? Optional.empty() : Optional.of(Path.of(keys)),
                text(env, HOST, DEFAULT_HOST),
                port(env),
                !found(env, JOURNAL).isEmpty());
    }

    /**
     * Whether the records live in memory and vanish with the process.
     *
     * @return true for an in-memory database url
     */
    public boolean isInMemory() {
        return database.contains(":mem:");
    }

    /**
     * Whether a key file was named.
     *
     * @return true when one was
     */
    public boolean servesKeys() {
        return keyFile.isPresent();
    }

    /**
     * The key file, which a server cannot start without.
     *
     * @return its path
     * @throws ConfigurationError when none is set or the one named is not there
     */
    public Path requireKeys() {
        Path keys = keyFile.orElseThrow(
                () -> new ConfigurationError(KEYS + " is not set, and a server with no keys refuses every request"));
        if (!Files.exists(keys)) {
            throw new ConfigurationError(KEYS + " names " + keys + ", which is not there");
        }
        return keys;
    }

    /**
     * What was read, for the startup line. Nothing here is a secret.
     *
     * @return every variable and what it was read as, in a fixed order
     */
    public SequencedMap<String, String> described() {
        SequencedMap<String, String> told = new LinkedHashMap<>();
        told.put(DATABASE, database);
        told.put(RESORT, resort);
        told.put(JOURNAL, journal.label());
        told.put(KEYS, keyFile.map(Path::toString).orElse("not set"));
        told.put(HOST, host);
        told.put(PORT, Integer.toString(port));
        return Collections.unmodifiableSequencedMap(told);
    }

    /**
     * The same settings with another database, which is how the command line's option wins over
     * the environment.
     *
     * @param url the database url
     * @return the configuration
     */
    public Configuration withDatabase(String url) {
        return new Configuration(url, resort, journal, keyFile, host, port, journalGiven);
    }

    /**
     * The same settings with the journal level named.
     *
     * @param level the level
     * @return the configuration, recording that the level was given
     */
    public Configuration withJournal(Level level) {
        return new Configuration(database, resort, level, keyFile, host, port, true);
    }

    /** One line saying where the records are. */
    @Override
    public String toString() {
        return resort + " on " + host + ":" + port + ", records in " + database;
    }

    private static String found(Map<String, String> env, String name) {
        String value = env.get(name);
        return value == null ? "" : value.strip();
    }

    private static String text(Map<String, String> env, String name, String fallback) {
        String value = found(env, name);
        return value.isEmpty() ? fallback : value;
    }

    private static int port(Map<String, String> env) {
        String value = found(env, PORT);
        if (value.isEmpty()) {
            return DEFAULT_PORT;
        }
        long wanted;
        try {
            wanted = Long.parseLong(value);
        } catch (NumberFormatException wrong) {
            throw new ConfigurationError(PORT + " is " + Quote.of(value) + ", which is not a port", wrong);
        }
        if (wanted < 1 || wanted > 65535) {
            throw new ConfigurationError(PORT + " is " + wanted + ", which is not a port");
        }
        return (int) wanted;
    }

    private static Level level(Map<String, String> env) {
        String value = found(env, JOURNAL);
        if (value.isEmpty()) {
            return Level.INFO;
        }
        try {
            return Level.named(value);
        } catch (IllegalArgumentException wrong) {
            throw new ConfigurationError(JOURNAL + ": " + wrong.getMessage(), wrong);
        }
    }
}
