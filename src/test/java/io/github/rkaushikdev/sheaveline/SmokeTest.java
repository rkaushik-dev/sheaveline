package io.github.rkaushikdev.sheaveline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.SheavelineApplication;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.cli.Main;
import io.github.rkaushikdev.sheaveline.cli.Terminal;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * The things that only break outside a test run, checked inside one where they can be.
 *
 * <p>Three failures the ordinary tests cannot see. A dependency only the tests have, so the command
 * will not start once the jar runs on its own. A class that reaches for a test helper, which works
 * until the tests are not on the class path. And a main class naming something that has been
 * renamed.
 *
 * <p>Building and running the jar itself needs the package phase, which comes after the tests. What
 * is here is everything that can be proved without it, so a laptop finds it before a runner does:
 * the program is started in a process of its own, and every class is loaded, from what the program
 * ships with and nothing else.
 */
class SmokeTest {

    private static final String USAGE = "Usage: sheaveline ";

    private static final String PLANTED = "a warning planted to prove the reading hears them";

    /** A class's own name, and the types inside a descriptor or a generic signature. */
    private static final Pattern NAMED_TYPE = Pattern.compile("L([A-Za-z_$][\\w$]*(?:/[A-Za-z_$][\\w$]*)+)[;<]");

    private static final int CLASS_FILE = 0xCAFEBABE;

    /** What a launcher reads from the environment, and says on standard error that it read. */
    private static final Set<String> LAUNCHER_OPTIONS =
            Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

    private static final List<String> VERBS = List.of("get", "put", "post", "delete", "options", "head", "patch");

    private static Settings settings;

    private static JsonNode document;

    private static List<String> warnings;

    @TempDir
    Path tmp;

    /**
     * What one command line came back with.
     *
     * @param code the exit code
     * @param out what went to standard output
     * @param err what went to standard error
     */
    private record Reply(int code, String out, String err) {}

    /**
     * Start the service on a database of its own and ask it for its document, hearing every warning
     * while the document is built, which is on the first request for it. Spring Boot sets the
     * logging up afresh as the service starts, so the ear goes on once it has.
     */
    @BeforeAll
    static void buildTheDocument() {
        ListAppender<ILoggingEvent> heard = new ListAppender<>();
        heard.start();
        settings = Settings.inMemory();
        try (Wire.Running running = Wire.freshClient(settings)) {
            Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.addAppender(heard);
            try {
                document = running.client().get("/openapi.json").json();
                LoggerFactory.getLogger("org.springdoc").warn(PLANTED);
            } finally {
                root.detachAppender(heard);
            }
        }
        warnings = heard.list.stream()
                .filter(one -> one.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @AfterAll
    static void closeTheDatabase() {
        settings.database().close();
    }

    private static Reply run(Map<String, String> environment, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                environment::get,
                new BufferedReader(new StringReader("")),
                new PrintWriter(out, true),
                new PrintWriter(err, true));
        int code = Main.run(terminal, args);
        return new Reply(code, out.toString(), err.toString());
    }

    private static Reply run(String... args) {
        return run(Map.of(), args);
    }

    private static Repository repository() {
        return Repository.ofThisRun();
    }

    private static List<Pom.Dependency> shipped() {
        return repository().project().dependencies().stream()
                .filter(Pom.Dependency::ships)
                .toList();
    }

    /** The program's own classes, and every jar it ships with: what the jar runs on. */
    private static List<Path> runtimeClassPath() {
        List<Path> path = new ArrayList<>();
        path.add(Repository.location(Main.class).orElseThrow());
        repository()
                .brings(shipped())
                .forEach(key -> path.add(repository().jar(key).orElseThrow()));
        return path;
    }

    private static List<Path> classFiles(Path directory) {
        try (Stream<Path> found = Files.walk(directory)) {
            return found.filter(Files::isRegularFile)
                    .filter(one -> one.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** A class file's name inside its directory, as the virtual machine writes it: {@code a/b/C$D}. */
    private static String internalName(Path directory, Path classFile) {
        String relative = directory.relativize(classFile).toString().replace(File.separatorChar, '/');
        return relative.substring(0, relative.length() - ".class".length());
    }

    /**
     * Every class a compiled class names: its class constants, and the types inside its descriptors
     * and signatures, which is where a field's or a parameter's type lives.
     */
    private static Set<String> references(Path classFile) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(classFile)))) {
            if (in.readInt() != CLASS_FILE) {
                throw new IllegalStateException(classFile + " is not a class file");
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] texts = new String[count];
            List<Integer> classes = new ArrayList<>();
            for (int at = 1; at < count; at++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> texts[at] = in.readUTF();
                    case 7 -> classes.add(in.readUnsignedShort());
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 15 -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        at++;
                    }
                    default -> throw new IllegalStateException(classFile + " holds a constant tagged " + tag);
                }
            }
            Set<String> found = new TreeSet<>();
            for (int index : classes) {
                if (!texts[index].startsWith("[")) {
                    found.add(texts[index]);
                }
            }
            for (String text : texts) {
                if (text != null) {
                    Matcher named = NAMED_TYPE.matcher(text);
                    while (named.find()) {
                        found.add(named.group(1));
                    }
                }
            }
            return found;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String packageOf(String internal) {
        int slash = internal.lastIndexOf('/');
        return slash < 0 ? "" : internal.substring(0, slash);
    }

    /** Every package a set of jars holds classes in, as {@code a/b}. */
    private static Set<String> packagesIn(Set<String> keys) {
        Set<String> found = new TreeSet<>();
        for (String key : keys) {
            for (String entry : Repository.entries(repository().jar(key).orElseThrow())) {
                if (entry.endsWith(".class")) {
                    found.add(packageOf(entry));
                }
            }
        }
        return found;
    }

    /** What a compiled class names that is a test helper, or that only a test library provides. */
    private static List<String> forbidden(Set<String> named, Set<String> helpers, Set<String> testPackages) {
        return named.stream()
                .filter(one -> helpers.contains(one.split("\\$", 2)[0]) || testPackages.contains(packageOf(one)))
                .toList();
    }

    @Test
    void theMainClassIsTheCommandLineEntryPoint() throws ReflectiveOperationException {
        String named = repository().project().property("main.class").orElseThrow();
        Class<?> main = Class.forName(named);
        assertThat(main).isEqualTo(Main.class);
        assertThat(main.isAnnotationPresent(CommandLine.Command.class)).isTrue();
        Method entry = main.getMethod("main", String[].class);
        assertThat(Modifier.isStatic(entry.getModifiers())).isTrue();
        assertThat(entry.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void theVersionTheProgramReportsIsTheOneItIsBuiltAs() {
        String built = repository().project().version();
        assertThat(Sheaveline.VERSION).isEqualTo(built);
        assertThat(SheavelineApplication.VERSION).isEqualTo(built);
        assertThat(run("--version").out()).isEqualToIgnoringNewLines("sheaveline, version " + built);
        assertThat(document.at("/info/version").asText()).isEqualTo(built);
    }

    /** A range is a promise that a future release will not break the office. */
    @Test
    void everyDependencyBootDoesNotManageIsPinnedToOneVersion() {
        Pom project = repository().project();
        assertThat(Pom.isExact(project.parentCoordinates().orElseThrow().version()))
                .isTrue();
        List<Pom.Dependency> shipped = shipped();
        assertThat(shipped).isNotEmpty().anyMatch(one -> one.version().isPresent());
        for (Pom.Dependency one : shipped) {
            String version = one.version().orElseGet(() -> repository()
                    .managedVersion(project, one.key())
                    .orElseThrow(() -> new AssertionError(one.key() + " has no version and Boot does not manage it")));
            assertThat(Pom.isExact(version)).as(one.key() + " " + version).isTrue();
            assertThat(repository().jar(one.key()).orElseThrow().getParent().getFileName())
                    .as(one.key() + " as this run resolved it")
                    .hasToString(version);
        }
        assertThat(Pom.isExact("[4.7,5.0)")).isFalse();
    }

    @Test
    void theProgramStartsAndSaysItsOwnVersion() throws IOException, InterruptedException {
        String path = String.join(
                File.pathSeparator,
                runtimeClassPath().stream().map(Path::toString).toList());
        ProcessBuilder started = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        path,
                        Main.class.getName(),
                        "--version")
                .redirectOutput(tmp.resolve("out").toFile())
                .redirectError(tmp.resolve("err").toFile());
        started.environment()
                .keySet()
                .removeIf(name -> name.startsWith(Configuration.PREFIX) || LAUNCHER_OPTIONS.contains(name));
        Process program = started.start();
        assertThat(program.waitFor(2, TimeUnit.MINUTES))
                .as("the program finished")
                .isTrue();
        assertThat(program.exitValue()).isZero();
        assertThat(Files.readString(tmp.resolve("out"), StandardCharsets.UTF_8)).contains(Sheaveline.VERSION);
        assertThat(Files.readString(tmp.resolve("err"), StandardCharsets.UTF_8)).isEmpty();
    }

    /** Help is the one thing that must work before anything is set up. */
    @Test
    void everyCommandGroupAnswersHelpWithoutADatabase() {
        Map<String, String> environment = Map.of(Configuration.DATABASE, Office.url(tmp.resolve("records")));
        Set<String> names = Main.tree(new Terminal(
                        name -> null,
                        new BufferedReader(new StringReader("")),
                        new PrintWriter(new StringWriter(), true),
                        new PrintWriter(new StringWriter(), true)))
                .getSubcommands()
                .keySet();
        assertThat(names).contains("setup", "guide", "lift", "report");
        for (String name : names) {
            Reply reply = run(environment, name, "--help");
            assertThat(reply.code()).as(name).isZero();
            assertThat(reply.out()).as(name).contains(USAGE + name);
        }
        assertThat(tmp).isEmptyDirectory();
        assertThat(run(environment, "setup").code()).isZero();
        assertThat(tmp.resolve("records.mv.db"))
                .as("the database the commands were pointed at")
                .isRegularFile();
    }

    @Test
    void noCompiledClassReachesForATestHelperOrATestLibrary() {
        Path classes = Repository.location(Main.class).orElseThrow();
        Path testClasses = Repository.location(SmokeTest.class).orElseThrow();
        Set<String> helpers = new TreeSet<>();
        for (Path one : classFiles(testClasses)) {
            String name = internalName(testClasses, one);
            if (!name.contains("$")) {
                helpers.add(name);
            }
        }
        Set<String> runtime = repository().brings(shipped());
        Set<String> testOnly = new TreeSet<>(repository()
                .brings(repository().project().dependencies().stream()
                        .filter(one -> one.scope().equals("test"))
                        .toList()));
        testOnly.removeAll(runtime);
        Set<String> testPackages = packagesIn(testOnly);
        testPackages.removeAll(packagesIn(runtime));
        assertThat(helpers)
                .contains("io/github/rkaushikdev/sheaveline/Hill", "io/github/rkaushikdev/sheaveline/api/Wire");
        assertThat(testPackages).contains("org/junit/jupiter/api", "org/assertj/core/api");
        List<Path> compiled = classFiles(classes);
        assertThat(compiled).hasSizeGreaterThan(100);
        for (Path one : compiled) {
            assertThat(forbidden(references(one), helpers, testPackages))
                    .as(classes.relativize(one).toString())
                    .isEmpty();
        }
        assertThat(references(classes.resolve("io/github/rkaushikdev/sheaveline/cli/Main.class")))
                .contains("picocli/CommandLine", "io/github/rkaushikdev/sheaveline/cli/Terminal");
        Path planted = testClasses.resolve("io/github/rkaushikdev/sheaveline/SmokeTest.class");
        assertThat(forbidden(references(planted), helpers, testPackages))
                .as("this test, compiled and read the same way")
                .contains("io/github/rkaushikdev/sheaveline/Office", "org/assertj/core/api/Assertions");
    }

    /** A class that only loads because something else loaded first, or because the tests are there. */
    @Test
    void everyClassLoadsOnItsOwn() throws IOException {
        Path classes = Repository.location(Main.class).orElseThrow();
        List<String> names = classFiles(classes).stream()
                .map(one -> internalName(classes, one).replace('/', '.'))
                .filter(one -> !one.endsWith("package-info"))
                .toList();
        assertThat(names).hasSizeGreaterThan(100);
        List<URL> path = new ArrayList<>();
        for (Path one : runtimeClassPath()) {
            path.add(one.toUri().toURL());
        }
        List<String> refused = new ArrayList<>();
        try (URLClassLoader alone =
                new URLClassLoader(path.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            for (String name : names) {
                try {
                    Class.forName(name, true, alone);
                } catch (ClassNotFoundException | LinkageError broken) {
                    refused.add(name + ": " + broken);
                }
            }
            assertThat(Class.forName(Main.class.getName(), false, alone).getClassLoader())
                    .isSameAs(alone);
            assertThatThrownBy(() -> Class.forName(Hill.class.getName(), true, alone))
                    .as("a test helper, asked for the same way")
                    .isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> Class.forName("org.junit.jupiter.api.Test", true, alone))
                    .isInstanceOf(ClassNotFoundException.class);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("the program's own main class does not load", missing);
        }
        assertThat(refused).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"setup", "tables", "settings", "guide"})
    void theCommandsThatRunBeforeAnythingIsSetUp(String name) {
        Reply reply = run("--database", Office.url(tmp.resolve("fresh")), name);
        assertThat(reply.code()).as(reply.err()).isZero();
    }

    @Test
    void checkRefusesADatabaseThatHasNeverBeenSetUp() {
        Reply reply = run("--database", Office.url(tmp.resolve("fresh")), "check");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.err()).contains("run setup");
    }

    @Test
    void setupIsSafeToRunAgain() {
        String url = Office.url(tmp.resolve("fresh"));
        Reply first = run("--database", url, "setup");
        Reply second = run("--database", url, "setup");
        assertThat(first.code()).isZero();
        assertThat(second.code()).isZero();
        assertThat(first.out()).isEqualTo(second.out());
    }

    /** A shape the document cannot describe vanishes from it with nothing but a warning. */
    @Test
    void theOpenApiDocumentBuildsWithoutDroppingAnything() {
        assertThat(warnings).as("the warnings while it was built").containsExactly(PLANTED);
        List<String> paths = new ArrayList<>();
        document.get("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths).hasSizeGreaterThan(30).contains("/health", "/ready");
    }

    @Test
    void theDocumentNamesTheDefaultAClientWouldActuallySend() {
        JsonNode field = document.at("/components/schemas/ObservationIn/properties/visibility");
        assertThat(field.get("type").asText()).isEqualTo("string");
        assertThat(field.get("default").asText()).isNotBlank().contains("30000");
    }

    @Test
    void everyRouteIsFiledUnderATag() {
        List<String> operations = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : document.get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                if (VERBS.contains(operation.getKey())) {
                    String called = operation.getKey() + " " + path.getKey();
                    operations.add(called);
                    assertThat(operation.getValue().path("tags").size())
                            .as(called)
                            .isPositive();
                }
            }
        }
        assertThat(operations).hasSizeGreaterThan(30);
    }
}
