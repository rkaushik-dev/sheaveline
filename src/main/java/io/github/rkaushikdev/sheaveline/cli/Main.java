package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.Configuration;
import io.github.rkaushikdev.sheaveline.Sheaveline;
import io.github.rkaushikdev.sheaveline.journal.Journal;
import io.github.rkaushikdev.sheaveline.journal.Level;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * The command line, which is how the office actually works.
 *
 * <p>The http surface is for the tablets. Everything the office does at a keyboard happens here:
 * loading a machine from a survey, printing the morning report, walking the rope, closing a work
 * order at the end of the day.
 *
 * <p>Two decisions worth stating. The database url comes from the environment first, so the office
 * machine never has it typed into a command. And a refusal is printed as one sentence and an exit
 * code rather than a stack trace, because the person reading it is a lift mechanic and a stack trace
 * tells them nothing they can act on.
 *
 * <p>The exit code is the answer: {@link #OK}; {@link #REFUSED} for a refusal or for something found
 * that somebody has to act on; {@link #MISUSED} for a command line that was wrong; {@link
 * #NOT_ANSWERING} for a database that will not open or will not answer. A fault in the program is not
 * caught here and ends the process with its stack trace.
 */
@Command(
        name = "sheaveline",
        version = "sheaveline, version " + Sheaveline.VERSION,
        scope = ScopeType.INHERIT,
        sortOptions = false,
        synopsisHeading = "Usage: ",
        descriptionHeading = "%n",
        parameterListHeading = "%nArguments:%n",
        optionListHeading = "%nOptions:%n",
        commandListHeading = "%nCommands:%n",
        description = {
            "Lift department records for a passenger ropeway operation.",
            "",
            "Everything is read from the environment and the flags override it, so the box in the"
                    + " equipment room is configured once and a person at a keyboard can still point one"
                    + " command somewhere else.",
            "",
            "The journal is off unless it is asked for. These commands print reports to standard output"
                    + " and a stray line in the middle of an eighty column page is a page nobody can read.",
            "",
            "This one is wrapped like every other command, because a port the environment cannot mean is a"
                    + " refusal and deserves a sentence rather than a stack trace."
        },
        subcommands = {
            Main.Setup.class,
            Main.Tables.class,
            Main.Check.class,
            Main.Settings.class,
            Guide.class,
            PlantCommands.LiftGroup.class,
            RopeCommands.RopeGroup.class,
            DayCommands.DayGroup.class,
            CrewCommands.CrewGroup.class,
            EvacCommands.EvacGroup.class,
            UpkeepCommands.UpkeepGroup.class,
            BoardCommands.BoardGroup.class,
            ReportCommands.ReportGroup.class
        })
public final class Main {

    /** It worked. */
    public static final int OK = 0;

    /** Something was refused, or something was found that somebody has to act on. */
    public static final int REFUSED = 1;

    /** The command line was wrong. */
    public static final int MISUSED = 2;

    /** The database would not open or would not answer. */
    public static final int NOT_ANSWERING = 2;

    private final Terminal terminal;

    @Option(
            names = "--database",
            paramLabel = "URL",
            description = "Where the records live. $" + Configuration.DATABASE + " if you leave it off.")
    private String database;

    @Option(
            names = "--journal",
            paramLabel = "LEVEL",
            converter = Levels.class,
            completionCandidates = Levels.class,
            description = "What the software writes about itself, on standard error. $" + Configuration.JOURNAL
                    + " otherwise. One of ${COMPLETION-CANDIDATES}.")
    private Level level;

    @Option(names = "--version", versionHelp = true, description = "Show the version and exit.")
    private boolean versionAsked;

    @Option(names = "--help", usageHelp = true, scope = ScopeType.INHERIT, description = "Show this message and exit.")
    private boolean helpAsked;

    private Console console;

    private Main(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * The console script.
     *
     * @param args the command line
     */
    public static void main(String... args) {
        System.exit(run(Terminal.system(), args));
    }

    /**
     * Run one command line, with the house rules about output and exit codes.
     *
     * @param terminal the environment, keyboard and streams
     * @param args the command line
     * @return the exit code
     */
    public static int run(Terminal terminal, String... args) {
        Main main = new Main(terminal);
        try {
            return main.execute(args);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        } finally {
            main.close();
            terminal.out().flush();
            terminal.err().flush();
        }
    }

    /**
     * The command tree, wired to a terminal: the same tree {@link #run} parses.
     *
     * @param terminal where help and errors go
     * @return the tree
     */
    static CommandLine tree(Terminal terminal) {
        return tree(new Main(terminal));
    }

    private static CommandLine tree(Main main) {
        CommandLine tree = new CommandLine(main)
                .setOut(main.terminal.out())
                .setErr(main.terminal.err())
                .setExpandAtFiles(false)
                .setOverwrittenOptionsAllowed(true);
        Common.sayTheDay(tree);
        return tree;
    }

    private int execute(String... args) throws IOException {
        CommandLine tree = tree(this);
        try {
            ParseResult parsed = tree.parseArgs(args);
            Integer helped = CommandLine.executeHelpRequest(parsed);
            if (helped != null) {
                return helped;
            }
            List<CommandLine> named = parsed.asCommandLineList();
            CommandLine last = named.getLast();
            if (named.size() == 1) {
                tree.usage(terminal.out());
                return OK;
            }
            return Refusals.handled(terminal, this::journal, commandName(last), () -> {
                Console ready = console();
                if (last.getCommandSpec().userObject() instanceof Action action) {
                    return action.run(ready);
                }
                last.usage(terminal.out());
                return OK;
            });
        } catch (ParameterException wrong) {
            return misused(wrong);
        } catch (Stopped stopped) {
            terminal.err().println(stopped.getMessage());
            return REFUSED;
        }
    }

    /**
     * The console every command is handed, built the first time one asks: the environment, with the
     * flags laid over it.
     */
    private Console console() {
        if (console == null) {
            Configuration read = Configuration.fromEnvironment(terminal.variables(Configuration.EVERY_NAME));
            if (database != null) {
                read = read.withDatabase(database);
            }
            if (level != null) {
                read = read.withJournal(level);
            } else if (!read.journalGiven()) {
                // Nobody asked, so nothing is said. The environment's own default suits the server
                // and not a program whose whole output is eighty column pages on standard out.
                read = silenced(read);
            }
            console = Console.of(read, terminal);
            console.journal().debug("started", "database", read.database());
        }
        return console;
    }

    private Journal journal() {
        return console == null ? Journal.silent() : console.journal();
    }

    private static Configuration silenced(Configuration read) {
        return new Configuration(
                read.database(),
                read.resort(),
                Level.OFF,
                read.keyFile(),
                read.host(),
                read.port(),
                read.journalGiven());
    }

    private static String commandName(CommandLine command) {
        String qualified = command.getCommandSpec().qualifiedName(" ");
        String root = command.getCommandSpec().root().name();
        return qualified.startsWith(root + " ") ? qualified.substring(root.length() + 1) : qualified;
    }

    /** A command line that was wrong: what it should look like, where to read more, and what was wrong. */
    private int misused(ParameterException wrong) {
        CommandLine where = wrong.getCommandLine();
        PrintWriter err = terminal.err();
        String usage = "Usage: ";
        err.print(usage + where.getHelp().synopsis(usage.length()));
        err.println("Try '" + where.getCommandSpec().qualifiedName(" ") + " --help' for help.");
        err.println();
        err.println("Error: " + wrong.getMessage());
        UnmatchedArgumentException.printSuggestions(wrong, err);
        return MISUSED;
    }

    private void close() {
        if (console != null) {
            console.close();
        }
    }

    /** The journal levels, by name. */
    static final class Levels extends Common.Choice<Level> {

        Levels() {
            super(named(Level.class));
        }
    }

    /** Build the tables. Safe to run on a database that already has them. */
    @Command(name = "setup", description = "Build the tables. Safe to run on a database that already has them.")
    static final class Setup implements Action {

        @Override
        public int run(Console console) {
            console.database().createSchema();
            console.say(Database.tableNames().size() + " tables ready");
            return OK;
        }
    }

    /** List the tables the package knows about. */
    @Command(name = "tables", description = "List the tables the package knows about.")
    static final class Tables implements Action {

        @Override
        public int run(Console console) {
            Database.tableNames().forEach(console::say);
            return OK;
        }
    }

    /** Say whether the database answers and what is in it. */
    @Command(name = "check", description = "Say whether the database answers and what is in it.")
    static final class Check implements Action {

        @Override
        public int run(Console console) {
            Database database = console.database();
            if (!new HashSet<>(database.existingTables()).containsAll(Database.tableNames())) {
                console.problem(database.url() + " has no tables yet, run setup");
                return REFUSED;
            }
            List<LiftCode> lifts;
            List<HaulRope> ropes;
            try (UnitOfWork work = console.work()) {
                lifts = work.lifts().codes();
                ropes = work.ropes().all();
            }
            console.say("database  " + database.url());
            console.say("lifts     " + lifts.size());
            console.say("ropes     " + ropes.size());
            if (!lifts.isEmpty()) {
                console.say(
                        "codes     " + lifts.stream().map(LiftCode::toString).collect(Collectors.joining(" ")));
            }
            return OK;
        }
    }

    /**
     * Print what the environment says, so the box in the equipment room can be checked.
     *
     * <p>Nothing printed here is a secret. The key file is named and not opened, and the database url
     * is the one that was going to be used anyway.
     */
    @Command(
            name = "settings",
            description = {
                "Print what the environment says, so the box in the equipment room can be checked.",
                "",
                "Nothing printed here is a secret. The key file is named and not opened, and the database"
                        + " url is the one that was going to be used anyway."
            })
    static final class Settings implements Action {

        @Override
        public int run(Console console) {
            Configuration read = console.configuration();
            for (Map.Entry<String, String> one : read.described().entrySet()) {
                console.say(String.format(Locale.ROOT, "%-26s %s", one.getKey(), one.getValue()));
            }
            console.say();
            console.say(read.toString());
            if (!read.servesKeys()) {
                console.say(Configuration.KEYS + " is not set, so the http surface would refuse every request");
            }
            return OK;
        }
    }
}
