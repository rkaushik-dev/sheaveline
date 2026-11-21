package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Configuration;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.Sheaveline;
import io.github.rkaushikdev.sheaveline.SheavelineError;
import io.github.rkaushikdev.sheaveline.journal.Journal;
import io.github.rkaushikdev.sheaveline.journal.Level;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.PlantError;
import jakarta.persistence.PersistenceException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliMainTest {

    /** A file database in a directory nobody can create, so it never opens. */
    private static final String NOWHERE = "jdbc:h2:file:/no/such/place/office";

    /** A line of a stack trace. */
    private static final String STACK_FRAME = "\tat ";

    @TempDir
    Path tmp;

    private final Shell shell = new Shell();

    private String database;

    @BeforeEach
    void aFreshFile() {
        database = Office.url(tmp.resolve("office"));
    }

    /** One row of what the settings command printed, by the variable it names. */
    private static String setting(String printed, String name) {
        return printed.lines()
                .filter(line -> line.startsWith(name + " "))
                .findFirst()
                .map(line -> line.substring(name.length()).strip())
                .orElseThrow(() -> new AssertionError(name + " is not on the page"));
    }

    /** Run one command line on the process's own terminal, with its two streams caught for the test. */
    private static Shell.Reply onTheProcessStreams(String... args) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream caughtOut = new ByteArrayOutputStream();
        ByteArrayOutputStream caughtErr = new ByteArrayOutputStream();
        int code;
        try {
            System.setOut(new PrintStream(caughtOut, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(caughtErr, true, StandardCharsets.UTF_8));
            code = Main.run(Terminal.system(), args);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        return new Shell.Reply(
                code, caughtOut.toString(StandardCharsets.UTF_8), caughtErr.toString(StandardCharsets.UTF_8));
    }

    private static Terminal quiet(StringWriter err) {
        return new Terminal(
                name -> null,
                new BufferedReader(new StringReader("")),
                new PrintWriter(new StringWriter(), true),
                new PrintWriter(err, true));
    }

    @Test
    void theGroupPrintsItsOwnHelp() {
        Shell.Reply reply = shell.run("--help");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("Lift department records", "--database");
    }

    @Test
    void theVersionIsThePackageVersion() {
        Shell.Reply reply = shell.run("--version");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains(Sheaveline.VERSION);
    }

    @Test
    void setupBuildsTheTables() {
        Shell.Reply reply = shell.on(database, "setup");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("tables ready");
    }

    @Test
    void setupIsSafeToRunTwice() {
        shell.on(database, "setup");
        assertThat(shell.on(database, "setup").code()).isZero();
    }

    @Test
    void theTablesCanBeListed() {
        Shell.Reply reply = shell.on(database, "tables");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("lifts", "haul_ropes");
    }

    @Test
    void checkSaysWhatIsInTheDatabase() {
        shell.on(database, "setup");
        Shell.Reply reply = shell.on(database, "check");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("lifts     0", "ropes     0");
    }

    @Test
    void checkNamesTheMachinesWhenThereAreAny() {
        shell.on(database, "setup");
        Shell.change(database, work -> work.lifts().add(Hill.johnPaul()));
        Shell.Reply reply = shell.on(database, "check");
        assertThat(reply.output()).contains("lifts     1", "codes     JP");
    }

    @Test
    void theDatabaseComesFromTheEnvironmentWhenItIsSet() {
        Shell exported = shell.exporting(Configuration.DATABASE, database);
        assertThat(exported.run("setup").code()).isZero();
        assertThat(exported.run("check").output())
                .contains(tmp.resolve("office").toString());
    }

    @Test
    void anExplicitDatabaseBeatsTheEnvironment() {
        Shell exported = shell.exporting(Configuration.DATABASE, Office.url(tmp.resolve("wrong")));
        String chosen = Office.url(tmp.resolve("right"));
        exported.run("--database", chosen, "setup");
        assertThat(exported.run("--database", chosen, "check").output()).contains("right");
    }

    @Test
    void theDefaultUrlIsAFileBesideTheOfficeMachine() {
        assertThat(Configuration.DEFAULT_DATABASE).startsWith("jdbc:h2:file:").endsWith("/sheaveline");
    }

    /** It used to keep its own copy of half of these, under different names. */
    @Test
    void theConsoleCarriesTheConfigurationItWasBuiltFrom() {
        Configuration read = new Configuration(
                Office.url(tmp.resolve("x")),
                "Snowbasin",
                Level.INFO,
                Optional.empty(),
                Configuration.DEFAULT_HOST,
                Configuration.DEFAULT_PORT,
                false);
        try (Console console = Console.of(read, quiet(new StringWriter()))) {
            assertThat(console.configuration()).isSameAs(read);
            assertThat(console.resort()).isEqualTo("Snowbasin");
            assertThat(console.database().url()).endsWith("x");
        }
    }

    @Test
    void theFlagOverridesTheEnvironmentWithoutLosingTheRest() {
        Shell exported = shell.exporting(Configuration.RESORT, "Snowbasin")
                .exporting(Configuration.DATABASE, Office.url(tmp.resolve("wrong")));
        Shell.Reply reply = exported.on(Office.url(tmp.resolve("right")), "settings");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("right", "Snowbasin");
    }

    @Test
    void aConsoleHandsOutAUnitOfWork() {
        try (Database memory = Database.inMemory()) {
            memory.createSchema();
            Console console = Console.on(memory);
            try (UnitOfWork work = console.work()) {
                assertThat(work.lifts().codes()).isEmpty();
            }
        }
    }

    @Test
    void aConsoleCanBeToldWhatDayItIs() {
        try (Database memory = Database.inMemory()) {
            Console console = Console.on(memory).toldTheDay(() -> LocalDate.of(2025, 1, 15));
            assertThat(console.today()).isEqualTo(LocalDate.of(2025, 1, 15));
        }
    }

    @Test
    void aDomainRefusalIsOneSentenceAndAnExitCode() throws Exception {
        StringWriter err = new StringWriter();
        int code = Refusals.handled(quiet(err), Journal::silent, "refuse", () -> {
            throw new PlantError("a quad does not take a six place");
        });
        assertThat(code).isEqualTo(Main.REFUSED);
        assertThat(err.toString()).isEqualToIgnoringNewLines("PlantError: a quad does not take a six place");
    }

    @Test
    void anythingThatIsNotADomainRefusalIsLeftAlone() {
        assertThatThrownBy(() -> Refusals.handled(quiet(new StringWriter()), Journal::silent, "burst", () -> {
                    throw new IllegalStateException("something else entirely");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("something else entirely");
    }

    @Test
    void aRefusalReachingACommandPrintsWithoutAStackTrace() {
        Shell.Reply reply = shell.on(Office.url(tmp.resolve("never-set-up")), "check");
        assertThat(reply.code()).isNotZero();
        assertThat(reply.output()).doesNotContain(STACK_FRAME).contains("run setup");
    }

    @Test
    void everyCommandCarriesALineOfHelp() {
        assertThat(shell.run("--help").output()).contains("setup", "tables", "check");
    }

    /** Read through a real command, so the test does not depend on how the two streams are split. */
    @Test
    void theConsoleWritesEveryLineItIsGiven() {
        shell.on(database, "setup");
        Shell.Reply reply = shell.on(database, "check");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("database", "lifts", "ropes");
    }

    @Test
    void aConsoleHoldsTheDatabaseItWasGiven() {
        try (Database memory = Database.inMemory()) {
            assertThat(Console.on(memory).database()).isSameAs(memory);
        }
    }

    @Test
    void aSheavelineErrorSubclassIsCaughtByTheWrapper() throws Exception {
        final class Odd extends SheavelineError {
            Odd(String message) {
                super(message);
            }
        }
        StringWriter err = new StringWriter();
        int code = Refusals.handled(quiet(err), Journal::silent, "refuse", () -> {
            throw new Odd("something the domain refused");
        });
        assertThat(code).isEqualTo(Main.REFUSED);
        assertThat(err.toString()).contains("Odd: something the domain refused");
    }

    /**
     * The driver can print on the process's own streams as well as the program can, so this runs
     * the command the way the process does, on those streams, and reads what landed on them.
     */
    @Test
    void aDatabaseThatWillNotOpenIsASentenceToo() {
        Shell.Reply reply = shell.on(NOWHERE, "setup");
        assertThat(reply.code()).isEqualTo(Main.NOT_ANSWERING);
        assertThat(reply.output()).contains("the database will not answer").doesNotContain(STACK_FRAME);

        Shell.Reply process = onTheProcessStreams("--database", NOWHERE, "setup");
        assertThat(process.code()).isEqualTo(Main.NOT_ANSWERING);
        assertThat(process.out()).isEmpty();
        assertThat(process.err()).startsWith("the database will not answer: ").hasLineCount(1);
    }

    @Test
    void aDatabaseThatHasNotBeenSetUpSaysToRunSetup() {
        Shell.Reply reply = shell.on(Office.url(tmp.resolve("empty")), "check");
        assertThat(reply.code()).isEqualTo(Main.REFUSED);
        assertThat(reply.output()).contains("run setup").doesNotContain(STACK_FRAME);
    }

    @Test
    void aDatabaseFailureIsToldApartFromOneThatIsMerelyEmpty() {
        assertThat(shell.on(NOWHERE, "check").code()).isEqualTo(2);
        assertThat(shell.on(Office.url(tmp.resolve("empty")), "check").code()).isEqualTo(1);
    }

    @Test
    void theDriverSentenceIsOneLine() {
        PersistenceException error = new PersistenceException(
                "could not prepare statement\n[select 1]",
                new SQLException("unable to open database file\n    under the stack it arrived with"));
        assertThat(Refusals.firstLine(error)).doesNotContain("\n").isEqualTo("unable to open database file");
    }

    @Test
    void theJournalIsOffUnlessItIsAskedFor() {
        Shell.Reply reply = shell.on(database, "tables");
        assertThat(reply.code()).isZero();
        assertThat(reply.err()).isEmpty();
    }

    @Test
    void askingForTheJournalWritesToStandardError() {
        Shell.Reply reply = shell.on(database, "--journal", "debug", "tables");
        assertThat(reply.code()).isZero();
        assertThat(reply.err()).contains("started");
        assertThat(reply.out()).contains("day_logs");
    }

    @Test
    void aRefusalGoesInTheJournalAsWellAsOnThePage() {
        shell.on(database, "setup");
        Shell.Reply reply = shell.on(database, "--journal", "info", "day", "show", "JP");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.err()).contains("refused", "error=StorageError");
    }

    @Test
    void aDatabaseThatWillNotAnswerGoesInTheJournalToo() {
        Shell.Reply reply = shell.on(database, "--journal", "info", "day", "show", "JP");
        assertThat(reply.code()).isEqualTo(2);
        assertThat(reply.err()).contains("error=PersistenceException", "the database will not answer");
    }

    @Test
    void aLevelNobodyHas() {
        Shell.Reply reply = shell.on(database, "--journal", "chatty", "tables");
        assertThat(reply.code()).isEqualTo(2);
        assertThat(reply.err()).contains("'chatty' is not one of");
    }

    @Test
    void theJournalNeverLandsInTheMiddleOfAReport() {
        Shell.Reply reply = shell.on(database, "--journal", "debug", "tables");
        assertThat(reply.out().lines())
                .isNotEmpty()
                .noneMatch(one -> one.contains(" info ") || one.contains(" debug "));
    }

    @Test
    void theSettingsCommandPrintsWhatTheEnvironmentSays() {
        Shell.Reply reply = shell.exporting(Configuration.RESORT, "Snowbasin")
                .exporting(Configuration.PORT, "9000")
                .on(database, "settings");
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("SHEAVELINE_RESORT", "Snowbasin", "9000");
    }

    @Test
    void theSettingsCommandWarnsWhenNoKeysAreConfigured() {
        assertThat(shell.on(database, "settings").out()).contains("would refuse every request");
    }

    @Test
    void aPortTheEnvironmentCannotMean() {
        Shell.Reply reply =
                shell.exporting(Configuration.PORT, "eight thousand").on(database, "settings");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.err()).contains("SHEAVELINE_PORT");
    }

    @Test
    void theDatabaseStillComesFromTheEnvironmentFirst() {
        Path wanted = tmp.resolve("from-env");
        Shell.Reply reply =
                shell.exporting("SHEAVELINE_DATABASE_URL", Office.url(wanted)).run("setup");
        assertThat(reply.code()).isZero();
        assertThat(Files.exists(tmp.resolve("from-env.mv.db"))).isTrue();
    }

    /** It used to print off whatever the environment said, which is a lie. */
    @Test
    void theSettingsCommandReportsTheJournalThatWasAskedFor() {
        Shell.Reply reply = shell.exporting(Configuration.JOURNAL, "warn").on(database, "settings");
        assertThat(reply.code()).isZero();
        assertThat(setting(reply.out(), "SHEAVELINE_JOURNAL")).isEqualTo("warn");
    }

    @Test
    void aJournalNamedInTheEnvironmentIsWrittenTo() {
        Shell.Reply reply = shell.exporting(Configuration.JOURNAL, "debug").on(database, "tables");
        assertThat(reply.code()).isZero();
        assertThat(reply.err()).contains("started");
    }

    @Test
    void theFlagStillBeatsTheEnvironment() {
        Shell.Reply reply = shell.exporting(Configuration.JOURNAL, "debug").on(database, "--journal", "off", "tables");
        assertThat(reply.code()).isZero();
        assertThat(reply.err()).isEmpty();
    }

    @Test
    void nothingNamedAnywhereStillSaysNothing() {
        Shell.Reply reply = shell.on(database, "tables");
        assertThat(reply.code()).isZero();
        assertThat(reply.err()).isEmpty();
        assertThat(setting(shell.on(database, "settings").out(), "SHEAVELINE_JOURNAL"))
                .isEqualTo("off");
    }
}
