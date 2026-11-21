package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.journal.Journal;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What one running copy of the service needs to know: the database behind it, the resort it answers
 * for, the keys it lets in, the day it thinks it is, and the journal it writes to.
 *
 * <p>Handed to {@link SheavelineApplication#run} rather than read from anywhere global, because the
 * tests start several copies side by side, each over its own database, and the office machine can
 * have last season's database open beside this season's. Whoever opened the database closes it.
 */
public final class Settings {

    /** The resort a copy answers for when nobody says otherwise. */
    public static final String DEFAULT_RESORT = "Ogden Peak";

    private final Database database;
    private final String resort;
    private final KeyRing keys;
    private final Supplier<LocalDate> today;
    private final Journal journal;

    /**
     * Settings with everything given.
     *
     * @param database the database
     * @param resort the resort's name
     * @param keys the keys that may come in
     * @param today what day it is, asked afresh on every request
     * @param journal where the request lines go
     */
    public Settings(Database database, String resort, KeyRing keys, Supplier<LocalDate> today, Journal journal) {
        this.database = Objects.requireNonNull(database, "database");
        this.resort = Objects.requireNonNull(resort, "resort");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.today = Objects.requireNonNull(today, "today");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /**
     * Settings over a database, with the default resort, no keys, the calendar's own today and a
     * silent journal.
     *
     * @param database the database
     * @return the settings
     */
    public static Settings of(Database database) {
        return new Settings(database, DEFAULT_RESORT, KeyRing.of(), LocalDate::now, Journal.silent());
    }

    /**
     * Settings over a database of their own, held in memory, with the schema built.
     *
     * @return the settings
     */
    public static Settings inMemory() {
        Database database = Database.inMemory();
        database.createSchema();
        return of(database);
    }

    /**
     * The same settings for another resort.
     *
     * @param changed the resort's name
     * @return the settings
     */
    public Settings withResort(String changed) {
        return new Settings(database, changed, keys, today, journal);
    }

    /**
     * The same settings with another key ring.
     *
     * @param changed the keys
     * @return the settings
     */
    public Settings withKeys(KeyRing changed) {
        return new Settings(database, resort, changed, today, journal);
    }

    /**
     * The same settings with another idea of what day it is, so a test can say otherwise.
     *
     * @param changed what day it is
     * @return the settings
     */
    public Settings withToday(Supplier<LocalDate> changed) {
        return new Settings(database, resort, keys, changed, journal);
    }

    /**
     * The same settings writing to another journal.
     *
     * @param changed the journal
     * @return the settings
     */
    public Settings withJournal(Journal changed) {
        return new Settings(database, resort, keys, today, changed);
    }

    /**
     * The database behind the service.
     *
     * @return the database
     */
    public Database database() {
        return database;
    }

    /**
     * The resort the service answers for.
     *
     * @return its name
     */
    public String resort() {
        return resort;
    }

    /**
     * The keys that may come in.
     *
     * @return the ring
     */
    public KeyRing keys() {
        return keys;
    }

    /**
     * Where the request lines go.
     *
     * @return the journal
     */
    public Journal journal() {
        return journal;
    }

    /**
     * The day the running copy thinks it is.
     *
     * @return today
     */
    public LocalDate today() {
        return today.get();
    }
}
