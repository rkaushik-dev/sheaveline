package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A stocked database for the command tests, built once and then copied.
 *
 * <p>Every command test wants the same thing: a file on disk with the schema in it and the three
 * machines on the register. Building that costs far more than the test does, between creating the
 * tables and writing eighty four carriers and seven towers, and the command tests would pay it a
 * couple of hundred times.
 *
 * <p>So it is built once into a template and copied after that. A copy of a small database file
 * is a filesystem operation, and each test still gets its own file that it can write to and throw
 * away. Which is the point of the fixture: isolation, not construction.
 *
 * <p>Templates are keyed on what the caller asked to be put in them, because a test that only wants
 * John Paul should not have to pay for the other two, and because a template with the wrong
 * machines in it is worse than no template at all.
 */
public final class Office {

    /** A machine the register can be stocked with. */
    public enum Machine implements Supplier<Lift> {
        /** John Paul, {@link Hill#johnPaul()}. */
        JOHN_PAUL(Hill::johnPaul),
        /** Strawberry, {@link Hill#strawberry()}. */
        STRAWBERRY(Hill::strawberry),
        /** Wildcat, {@link Hill#wildcat()}. */
        WILDCAT(Hill::wildcat);

        private final Supplier<Lift> maker;

        Machine(Supplier<Lift> maker) {
            this.maker = maker;
        }

        /**
         * A fresh copy of the machine.
         *
         * @return the lift
         */
        @Override
        public Lift get() {
            return maker.get();
        }

        /**
         * The machine's part of a template's name.
         *
         * @return the name in lower case
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The three machines the fixtures use, which is what a test gets when it names none. */
    public static final List<Machine> WHOLE_HILL = List.of(Machine.JOHN_PAUL, Machine.STRAWBERRY, Machine.WILDCAT);

    /** What every copy is called inside the directory it is copied to. */
    public static final String COPY = "office";

    private static final String FILE_URL = "jdbc:h2:file:";

    private static final String DATA_FILE = ".mv.db";

    private static final Map<String, Path> TEMPLATES = new HashMap<>();

    private static Path holding;

    private Office() {}

    /**
     * A database with that filling in it, built the first time it is asked for and handed back after
     * that. The database is closed before the file is handed back, so it can be copied.
     *
     * @param name what the template is called, which is what it is kept under
     * @param fill what goes in it, committed once the schema is built
     * @return the template's data file
     */
    public static synchronized Path template(String name, Consumer<UnitOfWork> fill) {
        Path held = TEMPLATES.get(name);
        if (held != null) {
            return held;
        }
        Path base = holding().resolve(name);
        try (Database database = Database.open(url(base))) {
            database.createSchema();
            try (UnitOfWork work = UnitOfWork.on(database).open()) {
                fill.accept(work);
                work.commit();
            }
        }
        Path made = dataFile(base);
        TEMPLATES.put(name, made);
        return made;
    }

    /**
     * A url for a private copy of a template, which the test may write to and throw away.
     *
     * @param directory where the copy goes; made if it is not there
     * @param name the template
     * @param fill what goes in the template, the first time it is built
     * @return the copy's url
     */
    public static String copied(Path directory, String name, Consumer<UnitOfWork> fill) {
        Path base = directory.resolve(COPY);
        try {
            Files.createDirectories(directory);
            Files.copy(template(name, fill), dataFile(base), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        return url(base);
    }

    /**
     * A database with those machines already on the register, or the whole hill when none are named.
     *
     * @param directory where the copy goes
     * @param machines the machines
     * @return the copy's url
     */
    public static String stocked(Path directory, Machine... machines) {
        List<Machine> wanted = machines.length == 0 ? WHOLE_HILL : Arrays.asList(machines);
        String name = wanted.stream().map(Machine::label).collect(Collectors.joining("-"));
        return copied(
                directory, name, work -> wanted.forEach(one -> work.lifts().add(one.get())));
    }

    /**
     * A database with the tables in it and nothing on the register.
     *
     * @param directory where the copy goes
     * @return the copy's url
     */
    public static String empty(Path directory) {
        return copied(directory, "empty", work -> {});
    }

    /**
     * The url of a file database, named without its extension.
     *
     * @param base the file, without the extension the database adds
     * @return the url
     */
    public static String url(Path base) {
        return FILE_URL + base.toAbsolutePath();
    }

    private static Path dataFile(Path base) {
        return base.resolveSibling(base.getFileName() + DATA_FILE);
    }

    private static Path holding() {
        if (holding == null) {
            try {
                holding = Files.createTempDirectory("sheaveline-templates-");
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
            Path made = holding;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> removeAll(made)));
        }
        return holding;
    }

    /** Take the templates away as the suite's process ends, deepest first so each directory is empty. */
    private static void removeAll(Path directory) {
        try (Stream<Path> found = Files.walk(directory)) {
            found.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
