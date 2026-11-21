package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.Configuration;
import io.github.rkaushikdev.sheaveline.journal.Journal;
import io.github.rkaushikdev.sheaveline.journal.Level;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What every command is handed: a database, a clock, a journal and a way to write.
 *
 * <p>The console holds the configuration it was built from rather than a second copy of half of it,
 * so the database url, the resort and the journal level are each read in one place.
 *
 * <p>The database is opened the first time a command asks for it, so a command that never touches
 * the records never creates a file for them, and it is closed with the console.
 */
public final class Console implements TellsTheTime, AutoCloseable {

    private final Terminal terminal;
    private final Configuration configuration;
    private final Journal journal;
    private final Supplier<LocalDateTime> clock;
    private final Records records;

    private Console(
            Terminal terminal,
            Configuration configuration,
            Journal journal,
            Supplier<LocalDateTime> clock,
            Records records) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.records = Objects.requireNonNull(records, "records");
    }

    /**
     * The one place a running copy is built from what it was told. The journal is silent unless the
     * configuration asks for one, and then it writes to the terminal's error stream.
     *
     * @param read the configuration
     * @param terminal where the console writes
     * @return the console, which opens the configured database when it is first asked for it
     */
    public static Console of(Configuration read, Terminal terminal) {
        return of(read, journalFor(read, terminal), terminal);
    }

    /**
     * A console built from a configuration, with the journal it is given.
     *
     * @param read the configuration
     * @param journal what the software writes about itself
     * @param terminal where the console writes
     * @return the console, which opens the configured database when it is first asked for it
     */
    public static Console of(Configuration read, Journal journal, Terminal terminal) {
        return new Console(terminal, read, journal, LocalDateTime::now, Records.opening(read.database()));
    }

    /**
     * A console over a database somebody else opened, and closes: the laptop defaults, a silent
     * journal, the wall clock and the process's own streams.
     *
     * @param database the database
     * @return the console
     */
    public static Console on(Database database) {
        return new Console(
                Terminal.system(),
                Configuration.defaults().withDatabase(database.url()),
                Journal.silent(),
                LocalDateTime::now,
                Records.given(database));
    }

    /**
     * The same console with its own idea of what moment it is, sharing the same database.
     *
     * @param now the clock
     * @return the console
     */
    public Console toldTheTime(Supplier<LocalDateTime> now) {
        return new Console(terminal, configuration, journal, now, records);
    }

    /**
     * The same console with its own idea of what day it is, the moment being the start of that day.
     *
     * @param today the calendar
     * @return the console
     */
    public Console toldTheDay(Supplier<LocalDate> today) {
        return toldTheTime(() -> today.get().atStartOfDay());
    }

    /**
     * The database, opened the first time it is asked for.
     *
     * @return the database
     */
    public Database database() {
        return records.database();
    }

    /**
     * The settings this console was built from.
     *
     * @return the configuration
     */
    public Configuration configuration() {
        return configuration;
    }

    /**
     * The name on the printed pages.
     *
     * @return the resort
     */
    public String resort() {
        return configuration.resort();
    }

    /**
     * What the software writes about itself.
     *
     * @return the journal
     */
    public Journal journal() {
        return journal;
    }

    /**
     * Where the console writes.
     *
     * @return the terminal
     */
    public Terminal terminal() {
        return terminal;
    }

    /** One clock, because a command that reads both the day and the moment wants them to agree. */
    @Override
    public LocalDateTime now() {
        return clock.get();
    }

    @Override
    public LocalDate today() {
        return now().toLocalDate();
    }

    /**
     * A unit of work on the database, open, for a {@code try} block to close.
     *
     * @return the unit of work
     */
    public UnitOfWork work() {
        return UnitOfWork.on(database()).open();
    }

    /**
     * Print a line on the page.
     *
     * @param text the line
     */
    public void say(String text) {
        terminal.out().println(text);
    }

    /** Print an empty line on the page. */
    public void say() {
        say("");
    }

    /**
     * Print every line of a page.
     *
     * @param lines the lines
     */
    public void page(List<String> lines) {
        lines.forEach(this::say);
    }

    /**
     * Print a line on the error stream, for something the person at the keyboard has to act on.
     *
     * @param text the line
     */
    public void problem(String text) {
        terminal.err().println(text);
    }

    /**
     * Ask before doing something that cannot be undone. A yes goes on; a no, an empty answer or
     * nobody there stops the command; anything else asks again.
     *
     * @param question the question, without the choices
     * @throws Stopped when the answer is not a yes
     */
    void confirm(String question) {
        PrintWriter out = terminal.out();
        while (true) {
            out.print(question + " [y/N]: ");
            out.flush();
            String answer;
            try {
                answer = terminal.in().readLine();
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
            if (answer == null) {
                out.println();
                throw new Stopped();
            }
            String said = answer.strip().toLowerCase(Locale.ROOT);
            if (said.equals("y") || said.equals("yes")) {
                return;
            }
            if (said.isEmpty() || said.equals("n") || said.equals("no")) {
                throw new Stopped();
            }
            out.println("Error: invalid input");
        }
    }

    /** Close the database, if this console is the one that opened it. */
    @Override
    public void close() {
        records.close();
    }

    /** Silent unless it was asked for, because reports go to standard output. */
    private static Journal journalFor(Configuration read, Terminal terminal) {
        if (read.journal() == Level.OFF) {
            return Journal.silent();
        }
        return new Journal(terminal.err(), read.journal());
    }

    /** Where the records live: a database handed in, or one opened from a url on first use. */
    private static final class Records {

        private final String url;
        private final boolean owned;
        private Database opened;

        private Records(String url, boolean owned, Database opened) {
            this.url = url;
            this.owned = owned;
            this.opened = opened;
        }

        static Records opening(String url) {
            return new Records(url, true, null);
        }

        static Records given(Database database) {
            return new Records(database.url(), false, database);
        }

        synchronized Database database() {
            if (opened == null) {
                opened = Database.open(url);
            }
            return opened;
        }

        synchronized void close() {
            if (owned && opened != null) {
                opened.close();
                opened = null;
            }
        }
    }
}
