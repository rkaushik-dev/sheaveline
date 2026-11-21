package io.github.rkaushikdev.sheaveline;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import picocli.CommandLine;

/**
 * The local Maven repository this test run's jars came from, and what each declared dependency
 * brings with it.
 *
 * <p>A test run cannot tell the jars the program ships with from the ones only the tests have,
 * because both are on its class path. The project files Maven resolved them with can: a dependency
 * brings what its own project file declares for compile and runtime, less what is optional or
 * excluded on the way, and so on down. The versions are the ones on the class path, which are the
 * ones Maven settled on.
 */
final class Repository {

    /** A class whose jar is known to come out of the repository, to find the repository by. */
    private static final Class<?> LANDMARK = CommandLine.class;

    private static Repository thisRun;

    private final Path root;
    private final Map<String, Path> jars;
    private final Map<Path, String> keys;
    private final Map<String, Pom> poms = new HashMap<>();

    private Repository(Path root, Map<String, Path> jars) {
        this.root = root;
        this.jars = Map.copyOf(jars);
        this.keys = new HashMap<>();
        jars.forEach((key, jar) -> keys.put(jar, key));
    }

    /**
     * The repository behind the class path this test run was given.
     *
     * @return the repository
     */
    static synchronized Repository ofThisRun() {
        if (thisRun == null) {
            Path landmark = location(LANDMARK)
                    .orElseThrow(() -> new IllegalStateException(LANDMARK + " was not loaded from a jar"));
            Path root = landmark.getParent().getParent().getParent().getParent().getParent();
            Map<String, Path> jars = new TreeMap<>();
            for (Path entry : classPath()) {
                coordinates(root, entry).ifPresent(found -> jars.put(found.key(), entry));
            }
            thisRun = new Repository(root, jars);
            if (!thisRun.keyOf(landmark).equals(Optional.of("info.picocli:picocli"))) {
                throw new IllegalStateException(landmark + " is not where a repository keeps picocli");
            }
        }
        return thisRun;
    }

    /**
     * The project file at the root of this repository, read with the parents it inherits from.
     *
     * @return the project
     */
    Pom project() {
        return Pom.read(Pom.PROJECT, this::pomFile);
    }

    /**
     * Every jar on this test run's class path, as {@code group:artifact}.
     *
     * @return the keys, sorted
     */
    Set<String> onTheClassPath() {
        return new TreeSet<>(jars.keySet());
    }

    /**
     * The jar a dependency resolved to in this run.
     *
     * @param key {@code group:artifact}
     * @return the jar, or empty when it is not on the class path
     */
    Optional<Path> jar(String key) {
        return Optional.ofNullable(jars.get(key));
    }

    /**
     * The dependency a jar on the class path is.
     *
     * @param jar the jar
     * @return {@code group:artifact}, or empty for anything that is not a jar out of the repository
     */
    Optional<String> keyOf(Path jar) {
        return Optional.ofNullable(keys.get(jar.toAbsolutePath().normalize()));
    }

    /**
     * The dependency a class was loaded from.
     *
     * @param type the class
     * @return {@code group:artifact}, or empty for a class of the platform's own or one compiled here
     */
    Optional<String> origin(Class<?> type) {
        return location(type).flatMap(this::keyOf);
    }

    /**
     * Everything those dependencies bring onto the class path, themselves included: the dependencies
     * their project files declare for compile and runtime, not optional and not excluded on the way
     * down, as far as this run's class path has them.
     *
     * @param declared the dependencies to start from
     * @return the keys, sorted
     */
    Set<String> brings(Collection<Pom.Dependency> declared) {
        Set<String> found = new TreeSet<>();
        Deque<Pom.Dependency> pending = new ArrayDeque<>(declared);
        while (!pending.isEmpty()) {
            Pom.Dependency one = pending.removeFirst();
            if (!jars.containsKey(one.key()) || !found.add(one.key())) {
                continue;
            }
            for (Pom.Dependency next : pom(one.key()).dependencies()) {
                if (next.ships() && !next.optional() && !one.excludes(next.key())) {
                    List<String> exclusions = Stream.concat(one.exclusions().stream(), next.exclusions().stream())
                            .toList();
                    pending.addLast(new Pom.Dependency(
                            next.group(),
                            next.artifact(),
                            next.version(),
                            next.scope(),
                            false,
                            exclusions,
                            next.type()));
                }
            }
        }
        return found;
    }

    /**
     * The project file of a jar on the class path, read with its parents.
     *
     * @param key {@code group:artifact}
     * @return the project
     */
    synchronized Pom pom(String key) {
        Pom known = poms.get(key);
        if (known == null) {
            Path jar = jar(key).orElseThrow(() -> new IllegalArgumentException(key + " is not on the class path"));
            Path versionDirectory = jar.getParent();
            String artifact = versionDirectory.getParent().getFileName().toString();
            String version = versionDirectory.getFileName().toString();
            known = Pom.read(versionDirectory.resolve(artifact + "-" + version + ".pom"), this::pomFile);
            poms.put(key, known);
        }
        return known;
    }

    /**
     * A project file in the repository, whether or not its jar is on the class path: a parent, or a
     * bill of materials.
     *
     * @param coordinates where it is
     * @return the file, or empty when the repository does not have it
     */
    Optional<Path> pomFile(Pom.Coordinates coordinates) {
        Path file = root.resolve(coordinates.group().replace('.', '/'))
                .resolve(coordinates.artifact())
                .resolve(coordinates.version())
                .resolve(coordinates.artifact() + "-" + coordinates.version() + ".pom");
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /**
     * The managed version of a dependency, as a bill of materials says it, following the bills it
     * imports.
     *
     * @param bill the bill of materials, read with its parents
     * @param key {@code group:artifact}
     * @return the version, filled in, or empty when the bill does not manage it
     */
    Optional<String> managedVersion(Pom bill, String key) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<Pom> pending = new ArrayDeque<>(List.of(bill));
        while (!pending.isEmpty()) {
            Pom one = pending.removeFirst();
            if (!seen.add(one.group() + ":" + one.artifact() + ":" + one.version())) {
                continue;
            }
            for (Pom.Dependency managed : one.managed()) {
                if (managed.scope().equals("import") && managed.version().isPresent()) {
                    pomFile(new Pom.Coordinates(
                                    managed.group(),
                                    managed.artifact(),
                                    managed.version().get()))
                            .map(file -> Pom.read(file, this::pomFile))
                            .ifPresent(pending::addLast);
                } else if (managed.key().equals(key) && managed.version().isPresent()) {
                    return managed.version();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The version a project's dependency gets: the one written beside it, or the one its parents'
     * bills of materials manage for it.
     *
     * @param project the project, read with its parents
     * @param one the dependency
     * @return the version, filled in
     * @throws IllegalStateException when it has none and nothing manages one
     */
    String versionOf(Pom project, Pom.Dependency one) {
        return one.version()
                .or(() -> managedVersion(project, one.key()))
                .orElseThrow(() -> new IllegalStateException(one.key() + " has no version and nothing manages one"));
    }

    /**
     * Where a class was loaded from.
     *
     * @param type the class
     * @return the jar or directory, or empty for the platform's own classes, which come out of the
     *     runtime image rather than a file
     */
    static Optional<Path> location(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        try {
            URI where = source.getLocation().toURI();
            if (!"file".equals(where.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(where).toAbsolutePath().normalize());
        } catch (URISyntaxException unreadable) {
            throw new IllegalStateException(source.getLocation() + " is not a path", unreadable);
        }
    }

    /**
     * The classes and resources inside a jar.
     *
     * @param jar the jar
     * @return every entry's name
     */
    static Set<String> entries(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Set<String> names = new TreeSet<>();
            zip.stream().forEach(one -> names.add(one.getName()));
            return names;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The class path the tests were started with, whichever way the runner handed it over. */
    private static Set<Path> classPath() {
        Set<Path> entries = new LinkedHashSet<>();
        for (String property : List.of("java.class.path", "surefire.test.class.path")) {
            String value = System.getProperty(property, "");
            for (String one : value.split(File.pathSeparator)) {
                if (!one.isBlank()) {
                    entries.add(Path.of(one).toAbsolutePath().normalize());
                }
            }
        }
        return entries;
    }

    /** A jar's coordinates, read off where the repository keeps it: group, artifact, version, file. */
    private static Optional<Pom.Coordinates> coordinates(Path root, Path entry) {
        if (!entry.startsWith(root) || !entry.getFileName().toString().endsWith(".jar")) {
            return Optional.empty();
        }
        Path relative = root.relativize(entry);
        int count = relative.getNameCount();
        if (count < 4) {
            return Optional.empty();
        }
        String artifact = relative.getName(count - 3).toString();
        String version = relative.getName(count - 2).toString();
        if (!relative.getFileName().toString().startsWith(artifact + "-" + version)) {
            return Optional.empty();
        }
        String group = String.join(
                ".",
                IntStream.range(0, count - 3)
                        .mapToObj(at -> relative.getName(at).toString())
                        .toList());
        return Optional.of(new Pom.Coordinates(group, artifact, version));
    }
}
