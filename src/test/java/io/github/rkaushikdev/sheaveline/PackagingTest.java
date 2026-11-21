package io.github.rkaushikdev.sheaveline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.cli.Main;
import io.github.rkaushikdev.sheaveline.cli.Terminal;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

/**
 * What the build declares against what the program actually imports.
 *
 * <p>A test suite structurally cannot catch a misfiled runtime dependency, because a test run is the
 * one place where every test-scoped library is on the class path as well. So this reads the shipped
 * source rather than running it: it collects every import under {@code src/main/java}, finds the
 * jar each imported class comes from, and holds that against what {@code pom.xml} declares and what
 * each declared dependency brings with it.
 *
 * <p>The failure it exists to catch is the quiet one. Something under {@code src/main} imports a
 * library that only the tests have, every test passes, and the first person to run the jar finds a
 * command that cannot start.
 *
 * <p>Every reading here is also made of something planted that it has to catch, so none of them can
 * pass by reading nothing.
 */
class PackagingTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static final Path TESTS = Path.of("src/test/java");

    private static final String ROOT_PACKAGE = "io.github.rkaushikdev.sheaveline";

    private static final Path ROOT = MAIN.resolve(ROOT_PACKAGE.replace('.', '/'));

    private static final String CLASSPATH = "classpath:";

    /** Where Flyway is told to look, as a directory of the sources. */
    private static final Path MIGRATIONS =
            Path.of("src/main/resources").resolve(Database.MIGRATIONS.substring(CLASSPATH.length()));

    private static final Path README = Path.of("README.md");

    /**
     * Declared for what they do while the program runs rather than imported: the H2 driver is the
     * one the database url names, and the validation starter brings the validator that enforces the
     * constraints the request shapes declare. Adding to this list is a decision.
     */
    private static final Set<String> TOOL_DEPENDENCIES =
            Set.of("com.h2database:h2", "org.springframework.boot:spring-boot-starter-validation");

    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Pattern MAIN_METHOD =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*(\\.\\.\\.|\\[\\s*])");

    private static final Pattern MIGRATION = Pattern.compile("V([1-9][0-9]*)__[a-z0-9_]+\\.sql");

    private static final Map<Path, Set<String>> ENTRIES = new HashMap<>();

    /**
     * One import, as written.
     *
     * @param name what follows {@code import} and {@code static}, without a trailing {@code .*}
     * @param isStatic whether it imports a member rather than a type
     * @param wildcard whether it ends in {@code .*}
     */
    private record Import(String name, boolean isStatic, boolean wildcard) {

        /** The class the import needs at run time, or empty for a whole package. */
        Optional<String> type() {
            if (isStatic && !wildcard) {
                return Optional.of(name.substring(0, name.lastIndexOf('.')));
            }
            return isStatic || !wildcard ? Optional.of(name) : Optional.empty();
        }

        /** Whether the import is one of this program's own classes. */
        boolean ours() {
            return name.startsWith(ROOT_PACKAGE + ".");
        }
    }

    private static Pom project() {
        return Repository.ofThisRun().project();
    }

    private static List<Pom.Dependency> shipped() {
        return project().dependencies().stream().filter(Pom.Dependency::ships).toList();
    }

    private static List<Pom.Dependency> testScoped() {
        return project().dependencies().stream()
                .filter(one -> one.scope().equals("test"))
                .toList();
    }

    private static Set<String> testOnly() {
        Set<String> only = new TreeSet<>(Repository.ofThisRun().brings(testScoped()));
        only.removeAll(Repository.ofThisRun().brings(shipped()));
        return only;
    }

    private static Pom.Plugin plugin(String artifact) {
        return project().plugins().stream()
                .filter(one -> one.artifact().equals(artifact))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the build has no " + artifact));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<Path> javaFiles(Path under) {
        try (Stream<Path> found = Files.walk(under)) {
            return found.filter(Files::isRegularFile)
                    .filter(one -> one.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<Path> shippedSources() {
        return javaFiles(MAIN);
    }

    private static List<Import> importsOf(String text) {
        List<Import> found = new ArrayList<>();
        Matcher each = IMPORT.matcher(text);
        while (each.find()) {
            found.add(new Import(each.group(2), each.group(1) != null, each.group(3) != null));
        }
        return found;
    }

    /** A class by the name an import gives it, a nested class's dots read as the compiler reads them. */
    private static Optional<Class<?>> load(String name) {
        String binary = name;
        while (true) {
            try {
                return Optional.of(Class.forName(binary, false, PackagingTest.class.getClassLoader()));
            } catch (ClassNotFoundException missing) {
                int dot = binary.lastIndexOf('.');
                if (dot < 0) {
                    return Optional.empty();
                }
                binary = binary.substring(0, dot) + "$" + binary.substring(dot + 1);
            }
        }
    }

    private static Class<?> loaded(String name, Path where) {
        return load(name).orElseThrow(() -> new AssertionError(name + ", imported by " + where + ", does not load"));
    }

    /** Whether a class is one the platform itself provides, from its boot or platform loader. */
    private static boolean platform(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        return loader == null || loader == ClassLoader.getPlatformClassLoader();
    }

    /**
     * Every class the files import from outside this program and the platform, with the files that
     * import it.
     */
    private static Map<Class<?>, Set<String>> thirdParty(Collection<Path> files) {
        Map<Class<?>, Set<String>> found =
                new TreeMap<>((one, two) -> one.getName().compareTo(two.getName()));
        for (Path file : files) {
            for (Import one : importsOf(read(file))) {
                if (one.ours()) {
                    continue;
                }
                String name = one.type()
                        .orElseThrow(() -> new AssertionError(file + " imports the whole of " + one.name()
                                + ", which cannot be followed to a jar: import the classes by name"));
                Class<?> type = loaded(name, file);
                if (!platform(type)) {
                    found.computeIfAbsent(type, ignored -> new TreeSet<>())
                            .add(file.getFileName().toString());
                }
            }
        }
        return found;
    }

    /** The dependency every third-party class the files import comes from, with the files that import it. */
    private static Map<String, Set<String>> origins(Collection<Path> files) {
        Map<String, Set<String>> found = new TreeMap<>();
        thirdParty(files).forEach((type, where) -> {
            String origin = Repository.ofThisRun()
                    .origin(type)
                    .orElseThrow(() -> new AssertionError(type + " comes from no jar in the repository"));
            found.computeIfAbsent(origin, ignored -> new TreeSet<>()).addAll(where);
        });
        return found;
    }

    /** What one declared dependency brings that none of the others does. */
    private static Set<String> broughtByItAlone(Pom.Dependency one, List<Pom.Dependency> all) {
        List<Pom.Dependency> others =
                all.stream().filter(other -> !other.key().equals(one.key())).toList();
        Set<String> alone = new TreeSet<>(Repository.ofThisRun().brings(List.of(one)));
        alone.removeAll(Repository.ofThisRun().brings(others));
        return alone;
    }

    private static boolean importedFromItAlone(Pom.Dependency one, List<Pom.Dependency> all, Set<String> imported) {
        return broughtByItAlone(one, all).stream().anyMatch(imported::contains);
    }

    private static String versionOf(Pom project, Pom.Plugin one) {
        return one.version()
                .or(() -> project.managedPlugins().stream()
                        .filter(managed -> managed.key().equals(one.key()))
                        .flatMap(managed -> managed.version().stream())
                        .findFirst())
                .orElseThrow(() -> new AssertionError(one.key() + " has no version and nothing manages one"));
    }

    private static String declaredPackage(String text) {
        Matcher found = PACKAGE.matcher(text);
        return found.find() ? found.group(1) : "";
    }

    private static String packageOf(Path file) {
        return MAIN.relativize(file.getParent())
                .toString()
                .replace(file.getFileSystem().getSeparator(), ".");
    }

    private static boolean declaresMain(String text) {
        return MAIN_METHOD.matcher(text).find();
    }

    /** The fully qualified name of every top level class in a source tree. */
    private static Set<String> classesUnder(Path tree) {
        Set<String> names = new TreeSet<>();
        for (Path file : javaFiles(tree)) {
            String simple = file.getFileName().toString().replace(".java", "");
            String inPackage = tree.relativize(file.getParent())
                    .toString()
                    .replace(file.getFileSystem().getSeparator(), ".");
            names.add(inPackage + "." + simple);
        }
        return names;
    }

    private static boolean namesAHelper(Import one, Set<String> helpers) {
        return helpers.stream()
                .anyMatch(helper -> one.name().equals(helper) || one.name().startsWith(helper + "."));
    }

    private static boolean reachesIntoTheTests(String text) {
        return text.contains("src/test") || text.contains("test-classes");
    }

    /** Whether migrations named like these run as one unbroken chain from the first. */
    private static boolean unbrokenChain(List<String> names) {
        List<Integer> versions = new ArrayList<>();
        for (String name : names) {
            Matcher found = MIGRATION.matcher(name);
            if (!found.matches()) {
                return false;
            }
            versions.add(Integer.parseInt(found.group(1)));
        }
        versions.sort(Integer::compare);
        for (int at = 0; at < versions.size(); at++) {
            if (versions.get(at) != at + 1) {
                return false;
            }
        }
        return !versions.isEmpty();
    }

    /** Every directory straight under the root package that holds a class. */
    private static List<String> topLevelPackages() {
        try (Stream<Path> found = Files.list(ROOT)) {
            return found.filter(Files::isDirectory)
                    .filter(one -> !javaFiles(one).isEmpty())
                    .map(one -> one.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> notNamed(String readme, Collection<String> names, String quote) {
        return names.stream()
                .filter(one -> !readme.contains(quote + one + quote))
                .toList();
    }

    private static CommandLine tree() {
        return Main.tree(new Terminal(
                name -> null,
                new BufferedReader(new StringReader("")),
                new PrintWriter(new StringWriter(), true),
                new PrintWriter(new StringWriter(), true)));
    }

    /** The jar the build makes, by the path the README gives it. */
    private static String jar() {
        Pom project = project();
        return "target/" + project.artifact() + "-" + project.version() + ".jar";
    }

    /** Whether a README line runs the program, by its own name or as the jar. */
    private static boolean runsTheProgram(String line) {
        return line.startsWith("sheaveline ") || line.startsWith("java -jar ");
    }

    /**
     * What a README line promises that the program does not have: a jar the build does not make, or
     * commands the tree does not hold, read down the tree as far as the line names commands. A word
     * that is a choice of several, {@code list|show}, is each of them.
     */
    private static List<String> broken(String line, CommandLine tree, String jar) {
        List<String> words = new ArrayList<>(List.of(line.strip().split("\\s+")));
        if (line.startsWith("java -jar ")) {
            if (words.size() < 3 || !words.get(2).equals(jar)) {
                return List.of(words.size() < 3 ? line : words.get(2));
            }
            words = new ArrayList<>(words.subList(2, words.size()));
        } else if (!line.startsWith("sheaveline ")) {
            return List.of();
        }
        if (words.size() < 2 || words.get(1).startsWith("-") || words.get(1).startsWith("[")) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        CommandLine at = tree;
        String said = "";
        for (String word : words.subList(1, words.size())) {
            boolean placeholder =
                    word.chars().anyMatch(Character::isLetter) && word.equals(word.toUpperCase(Locale.ROOT));
            if (at.getSubcommands().isEmpty() || word.startsWith("-") || word.startsWith("[") || placeholder) {
                break;
            }
            String[] choices = word.split("\\|");
            for (String one : choices) {
                if (!at.getSubcommands().containsKey(one)) {
                    missing.add(said + one);
                }
            }
            if (choices.length > 1 || !missing.isEmpty()) {
                break;
            }
            at = at.getSubcommands().get(word);
            said = said + word + " ";
        }
        return missing;
    }

    private static Set<String> entries(Path jar) {
        synchronized (ENTRIES) {
            return ENTRIES.computeIfAbsent(jar, Repository::entries);
        }
    }

    /** Whether any jar the program runs with holds a class straight in that package. */
    private static boolean shipsThePackage(String name) {
        String directory = name.replace('.', '/') + "/";
        Repository repository = Repository.ofThisRun();
        return repository.brings(shipped()).stream()
                .flatMap(key -> repository.jar(key).stream())
                .anyMatch(jar -> entries(jar).stream()
                        .anyMatch(entry -> entry.startsWith(directory)
                                && entry.endsWith(".class")
                                && entry.indexOf('/', directory.length()) < 0));
    }

    static Stream<Arguments> everyThirdPartyPackage() {
        Set<String> packages = new TreeSet<>();
        thirdParty(shippedSources()).keySet().forEach(type -> packages.add(type.getPackageName()));
        return Stream.concat(
                packages.stream().map(one -> Arguments.of(one, true)),
                Stream.of(Arguments.of("org.junit.jupiter.api", false), Arguments.of("org.assertj.core.api", false)));
    }

    @Test
    void thePackageShipsClassesAtAll() {
        assertThat(shippedSources()).hasSizeGreaterThan(50);
    }

    @Test
    void everyThirdPartyImportComesFromADeclaredRuntimeDependency() {
        Set<String> runtime = Repository.ofThisRun().brings(shipped());
        Map<String, Set<String>> imported = origins(shippedSources());
        assertThat(imported).containsKey("info.picocli:picocli");
        for (Map.Entry<String, Set<String>> one : imported.entrySet()) {
            assertThat(runtime)
                    .as(one.getKey() + " imported by " + one.getValue())
                    .contains(one.getKey());
        }
        assertThat(origins(List.of(TESTS.resolve(ROOT_PACKAGE.replace('.', '/')).resolve("PackagingTest.java"))))
                .as("this test's own imports, read the same way")
                .containsKey("org.junit.jupiter:junit-jupiter-api")
                .matches(read -> !runtime.containsAll(read.keySet()));
    }

    @Test
    void nothingShippedImportsALibraryOnlyTheTestsHave() {
        Set<String> testOnly = testOnly();
        assertThat(testOnly)
                .contains(
                        "org.junit.jupiter:junit-jupiter-api", "org.assertj:assertj-core", "org.mockito:mockito-core");
        for (Map.Entry<String, Set<String>> one : origins(shippedSources()).entrySet()) {
            assertThat(testOnly)
                    .as(one.getKey() + " imported by " + one.getValue())
                    .doesNotContain(one.getKey());
        }
        assertThat(origins(javaFiles(TESTS)).keySet())
                .as("the tests' own imports, read the same way")
                .containsAnyElementsOf(testOnly);
    }

    @Test
    void everyRuntimeDependencyIsEitherImportedOrANamedTool() {
        List<Pom.Dependency> shipped = shipped();
        Set<String> imported = origins(shippedSources()).keySet();
        assertThat(shipped).extracting(Pom.Dependency::key).containsAll(TOOL_DEPENDENCIES);
        for (Pom.Dependency one : shipped) {
            boolean used = importedFromItAlone(one, shipped, imported);
            if (TOOL_DEPENDENCIES.contains(one.key())) {
                assertThat(used)
                        .as(one.key() + " is a named tool, and something imports what only it brings")
                        .isFalse();
            } else {
                assertThat(used)
                        .as(one.key() + " brings nothing the program imports")
                        .isTrue();
            }
        }
    }

    @Test
    void everyDependencyIsPinnedToOneVersion() {
        assertThat(List.of("4.7.7", "2.3.232", "6.6.53.Final", "0.0.20131108.vaadin1"))
                .allMatch(Pom::isExact);
        assertThat(List.of("[1.0,2.0)", "(,1.0]", "1.+", "LATEST", "RELEASE", "1.0-SNAPSHOT", "${picocli.version}", ""))
                .noneMatch(Pom::isExact);
        Pom project = project();
        Pom.Coordinates parent = project.parentCoordinates().orElseThrow();
        assertThat(parent.key()).isEqualTo("org.springframework.boot:spring-boot-starter-parent");
        assertThat(Pom.isExact(parent.version())).as(parent.version()).isTrue();
        assertThat(project.dependencies()).hasSizeGreaterThan(5);
        for (Pom.Dependency one : project.dependencies()) {
            String version = Repository.ofThisRun().versionOf(project, one);
            assertThat(Pom.isExact(version)).as(one.key() + " " + version).isTrue();
        }
        assertThat(project.plugins()).isNotEmpty();
        for (Pom.Plugin one : project.plugins()) {
            String version = versionOf(project, one);
            assertThat(Pom.isExact(version)).as(one.key() + " " + version).isTrue();
        }
    }

    @Test
    void theBuildDeclaresTheJavaItNeeds() throws IOException {
        assertThat(project().property("java.version")).contains("21");
        assertThat(plugin("maven-compiler-plugin").configuration()).containsEntry("release", "21");
        try (InputStream compiled = Sheaveline.class.getResourceAsStream("Sheaveline.class")) {
            byte[] head = compiled.readNBytes(8);
            assertThat(((head[6] & 0xff) << 8) | (head[7] & 0xff))
                    .as("the class file version Java 21 writes")
                    .isEqualTo(65);
        }
    }

    @Test
    void theMainClassPointsAtSomethingThatExists() {
        String main = project().property("main.class").orElseThrow();
        Path source = MAIN.resolve(main.replace('.', '/') + ".java");
        assertThat(source).isRegularFile();
        assertThat(declaresMain(read(source))).as(main).isTrue();
        assertThat(declaresMain(read(ROOT.resolve("Sheaveline.java"))))
                .as("a class with no main, read the same way")
                .isFalse();
        assertThat(plugin("spring-boot-maven-plugin").configuration()).containsEntry("mainClass", main);
    }

    @Test
    void theClassesLiveUnderTheRootPackage() {
        Pom project = project();
        assertThat(project.group() + "." + project.artifact()).isEqualTo(ROOT_PACKAGE);
        assertThat(project.buildNames("sourceDirectory")).isFalse();
        List<Path> sources = shippedSources();
        assertThat(sources).isNotEmpty().allMatch(one -> one.startsWith(ROOT));
        for (Path one : sources) {
            assertThat(declaredPackage(read(one))).as(one.toString()).isEqualTo(packageOf(one));
        }
        assertThat(declaredPackage("package " + ROOT_PACKAGE + ".plant;\n"))
                .as("a class filed in the wrong directory, read the same way")
                .isNotEqualTo(packageOf(ROOT.resolve("measures").resolve("Length.java")));
    }

    @Test
    void noShippedClassImportsATestHelper() {
        Set<String> helpers = classesUnder(TESTS);
        assertThat(helpers).contains(Hill.class.getName(), Office.class.getName(), ROOT_PACKAGE + ".api.Wire");
        for (Path file : shippedSources()) {
            for (Import one : importsOf(read(file))) {
                assertThat(namesAHelper(one, helpers))
                        .as(file + " imports " + one.name())
                        .isFalse();
            }
        }
        Path planted = TESTS.resolve(ROOT_PACKAGE.replace('.', '/')).resolve("OfficeTest.java");
        assertThat(importsOf(read(planted)))
                .as("a test that does import a helper, read the same way")
                .anyMatch(one -> namesAHelper(one, helpers));
    }

    @Test
    void noShippedClassReachesBackIntoTheTests() {
        List<Path> sources = shippedSources();
        assertThat(sources).isNotEmpty();
        for (Path one : sources) {
            assertThat(reachesIntoTheTests(read(one))).as(one.toString()).isFalse();
        }
        assertThat(reachesIntoTheTests("Path.of(\"src/test/resources/survey.csv\")"))
                .isTrue();
        assertThat(reachesIntoTheTests("target/test-classes/office.mv.db")).isTrue();
    }

    /** A class that will not load is a command that will not start, and nothing else here would say so. */
    @ParameterizedTest
    @MethodSource("everyThirdPartyPackage")
    void eachThirdPartyPackageIsInAJarTheProgramRunsWith(String name, boolean ships) {
        assertThat(shipsThePackage(name)).as(name).isEqualTo(ships);
    }

    @Test
    void theMigrationsShipInTheResources() throws IOException {
        assertThat(Database.MIGRATIONS).startsWith(CLASSPATH);
        List<String> names;
        try (Stream<Path> found = Files.list(MIGRATIONS)) {
            names = found.map(one -> one.getFileName().toString()).sorted().toList();
        }
        assertThat(names).hasSizeGreaterThan(1);
        assertThat(unbrokenChain(names)).as(names.toString()).isTrue();
        for (String one : names) {
            String resource = "/" + Database.MIGRATIONS.substring(CLASSPATH.length()) + "/" + one;
            assertThat(Database.class.getResource(resource)).as(resource).isNotNull();
        }
        assertThat(unbrokenChain(List.of("V1__baseline.sql", "V3__indexes.sql")))
                .isFalse();
        assertThat(unbrokenChain(List.of("V1__baseline.sql", "V1__again.sql"))).isFalse();
        assertThat(unbrokenChain(List.of("V2__weather_log.sql"))).isFalse();
        assertThat(unbrokenChain(List.of("V1__baseline.sql", "v2_weather_log.sql")))
                .isFalse();
        assertThat(unbrokenChain(List.of())).isFalse();
    }

    /** A package the README does not mention is one nobody knows exists. */
    @Test
    void theReadmeNamesEveryPackageThatShips() {
        String readme = read(README);
        List<String> packages = topLevelPackages();
        assertThat(packages).contains("api", "cli", "journal", "measures", "util");
        assertThat(notNamed(readme, packages, "`")).isEmpty();
        assertThat(notNamed(readme.replace("`journal`", "journal"), packages, "`"))
                .as("a README that forgot one, read the same way")
                .containsExactly("journal");
    }

    @Test
    void theReadmeNamesEveryEnvironmentVariableTheProgramReads() {
        String readme = read(README);
        assertThat(Configuration.EVERY_NAME).isNotEmpty();
        assertThat(notNamed(readme, Configuration.EVERY_NAME, "")).isEmpty();
        assertThat(notNamed(readme, List.of(Configuration.PREFIX + "ZZZ"), ""))
                .as("a variable the README never names, read the same way")
                .containsExactly(Configuration.PREFIX + "ZZZ");
    }

    @Test
    void theReadmeDoesNotPromiseACommandThatIsNotThere() {
        CommandLine tree = tree();
        String jar = jar();
        List<String> lines =
                read(README).lines().filter(PackagingTest::runsTheProgram).toList();
        assertThat(lines).hasSizeGreaterThan(5);
        for (String line : lines) {
            assertThat(broken(line, tree, jar)).as(line).isEmpty();
        }
        assertThat(broken("sheaveline grooming", tree, jar)).containsExactly("grooming");
        assertThat(broken("sheaveline lift sideways JP", tree, jar)).containsExactly("lift sideways");
        assertThat(broken("sheaveline rope list|coil", tree, jar)).containsExactly("rope coil");
        assertThat(broken("java -jar target/sheaveline-9.9.9.jar setup", tree, jar))
                .containsExactly("target/sheaveline-9.9.9.jar");
        assertThat(broken("sheaveline lift show JP", tree, jar)).isEmpty();
    }
}
