package io.github.rkaushikdev.sheaveline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * One Maven project file, read: who it is, what it inherits from, its properties, what it depends
 * on and the plugins it builds with, with every {@code ${name}} filled in the way Maven fills it in:
 * from the file's own properties, then its parents', with the {@code project.*} names meaning the
 * file being read.
 *
 * <p>Only what the build checks need is read. Profiles, and versions settled by mediation, are
 * not: the class path a test run is given already carries the versions Maven chose.
 */
final class Pom {

    /** The project file at the root of this repository. */
    static final Path PROJECT = Path.of("pom.xml");

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private static final int PASSES = 10;

    private static final Pattern EXACT = Pattern.compile("[0-9]+(\\.[0-9]+)*([.-][A-Za-z0-9]+)*");

    /**
     * Where a project file sits in a repository.
     *
     * @param group the group id
     * @param artifact the artifact id
     * @param version the version
     */
    record Coordinates(String group, String artifact, String version) {

        /** All three, and none missing. */
        Coordinates {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(version, "version");
        }

        /**
         * The group and the artifact, which is what a dependency is known by whatever its version.
         *
         * @return {@code group:artifact}
         */
        String key() {
            return group + ":" + artifact;
        }
    }

    /**
     * A dependency as a project file declares it.
     *
     * @param group the group id
     * @param artifact the artifact id
     * @param version the version written beside it, if one is
     * @param scope the scope, {@code compile} when none is written
     * @param optional whether it is marked optional, so that nothing depending on this file gets it
     * @param exclusions what it is declared without, as {@code group:artifact} with {@code *} for any
     * @param type what kind of artifact it is, {@code jar} when none is written, with its classifier
     *     after a colon when it has one: a project can depend on its neighbour's jar and on the same
     *     neighbour's test jar
     */
    record Dependency(
            String group,
            String artifact,
            Optional<String> version,
            String scope,
            boolean optional,
            List<String> exclusions,
            String type) {

        /** A dependency with its exclusions copied. */
        Dependency {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(scope, "scope");
            exclusions = List.copyOf(exclusions);
            Objects.requireNonNull(type, "type");
        }

        /**
         * What the dependency is known by.
         *
         * @return {@code group:artifact}
         */
        String key() {
            return group + ":" + artifact;
        }

        /**
         * What tells this dependency apart from another on the same artifact.
         *
         * @return {@code group:artifact:type}
         */
        String identity() {
            return key() + ":" + type;
        }

        /**
         * Whether it is on the class path the program runs with, rather than only the tests'.
         *
         * @return true for compile and runtime scope
         */
        boolean ships() {
            return scope.equals("compile") || scope.equals("runtime");
        }

        /**
         * Whether a dependency with that key is left out of everything this one brings.
         *
         * @param key {@code group:artifact}
         * @return true when an exclusion names it
         */
        boolean excludes(String key) {
            String[] wanted = key.split(":", 2);
            for (String one : exclusions) {
                String[] rule = one.split(":", 2);
                if ((rule[0].equals("*") || rule[0].equals(wanted[0]))
                        && (rule[1].equals("*") || rule[1].equals(wanted[1]))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A plugin the build runs.
     *
     * @param group the group id, {@code org.apache.maven.plugins} when none is written
     * @param artifact the artifact id
     * @param version the version written beside it, if one is
     * @param configuration its configuration's top level values, by name, filled in
     */
    record Plugin(String group, String artifact, Optional<String> version, Map<String, String> configuration) {

        /** A plugin with its configuration copied. */
        Plugin {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(version, "version");
            configuration = Map.copyOf(configuration);
        }

        /**
         * What the plugin is known by.
         *
         * @return {@code group:artifact}
         */
        String key() {
            return group + ":" + artifact;
        }
    }

    private final Element project;
    private final Optional<Pom> parent;
    private final Map<String, String> properties;

    private Pom(Element project, Optional<Pom> parent) {
        this.project = project;
        this.parent = parent;
        this.properties = new HashMap<>();
        parent.ifPresent(above -> properties.putAll(above.properties));
        child(project, "properties").ifPresent(own -> elements(own)
                .forEach(one ->
                        properties.put(one.getTagName(), one.getTextContent().strip())));
        Map<String, String> builtIn = new LinkedHashMap<>();
        builtIn.put("groupId", group());
        builtIn.put("artifactId", artifact());
        builtIn.put("version", version());
        parentCoordinates().ifPresent(above -> {
            builtIn.put("parent.groupId", above.group());
            builtIn.put("parent.artifactId", above.artifact());
            builtIn.put("parent.version", above.version());
        });
        builtIn.forEach((name, value) -> {
            properties.put("project." + name, value);
            properties.put("pom." + name, value);
        });
    }

    /**
     * Whether a version is one exact release: no range, no {@code LATEST} or {@code RELEASE}, no
     * snapshot, and nothing left unfilled.
     *
     * @param version the version as written, filled in
     * @return true for one release
     */
    static boolean isExact(String version) {
        return EXACT.matcher(version).matches()
                && !version.toUpperCase(Locale.ROOT).endsWith("SNAPSHOT");
    }

    /**
     * Read a project file on its own, its parent not consulted.
     *
     * @param file the file
     * @return the project
     */
    static Pom read(Path file) {
        return read(file, coordinates -> Optional.empty());
    }

    /**
     * Read a project file and the parents it inherits from.
     *
     * @param file the file
     * @param parents where a parent's file is, when it can be found
     * @return the project
     */
    static Pom read(Path file, Function<Coordinates, Optional<Path>> parents) {
        Element project = parse(file);
        Optional<Pom> parent =
                child(project, "parent").map(Pom::coordinates).flatMap(parents).map(found -> read(found, parents));
        return new Pom(project, parent);
    }

    /**
     * The group id, the parent's when the file does not name its own.
     *
     * @return the group id
     */
    String group() {
        return own("groupId")
                .or(() -> parentCoordinates().map(Coordinates::group))
                .orElse("");
    }

    /**
     * The artifact id.
     *
     * @return the artifact id
     */
    String artifact() {
        return own("artifactId").orElse("");
    }

    /**
     * The version, the parent's when the file does not name its own.
     *
     * @return the version
     */
    String version() {
        return own("version")
                .or(() -> parentCoordinates().map(Coordinates::version))
                .orElse("");
    }

    /**
     * The parent this file names, whether or not it could be read.
     *
     * @return the parent's coordinates, or empty when there is none
     */
    Optional<Coordinates> parentCoordinates() {
        return child(project, "parent").map(Pom::coordinates);
    }

    /**
     * A property, as this file and its parents define it.
     *
     * @param name the property
     * @return its value, filled in, or empty when nothing defines it
     */
    Optional<String> property(String name) {
        return Optional.ofNullable(properties.get(name)).map(this::resolve);
    }

    /**
     * Fill in every {@code ${name}} this file or its parents can answer. One nobody answers is left
     * as it is written.
     *
     * @param text the text
     * @return the text filled in
     */
    String resolve(String text) {
        String done = text;
        for (int pass = 0; pass < PASSES && done.contains("${"); pass++) {
            Matcher found = PROPERTY.matcher(done);
            StringBuilder next = new StringBuilder();
            while (found.find()) {
                String value = properties.get(found.group(1));
                found.appendReplacement(next, Matcher.quoteReplacement(value == null ? found.group() : value));
            }
            found.appendTail(next);
            if (next.toString().equals(done)) {
                break;
            }
            done = next.toString();
        }
        return done;
    }

    /**
     * What the project depends on, its parents' dependencies included, a dependency the file
     * declares itself winning over a parent's with the same key.
     *
     * @return the dependencies, filled in
     */
    List<Dependency> dependencies() {
        return List.copyOf(inherited(this).values());
    }

    /**
     * The versions this file and its parents manage, without following imported bills of
     * materials.
     *
     * @return the managed dependencies, filled in
     */
    List<Dependency> managed() {
        List<Dependency> found = new ArrayList<>();
        for (Pom one = this; one != null; one = one.parent.orElse(null)) {
            found.addAll(one.managedIn(this));
        }
        return found;
    }

    /**
     * The plugins this file builds with.
     *
     * @return the plugins, filled in
     */
    List<Plugin> plugins() {
        return child(project, "build")
                .flatMap(build -> child(build, "plugins"))
                .map(plugins -> plugins(plugins, this))
                .orElse(List.of());
    }

    /**
     * The plugins whose versions this file and its parents manage.
     *
     * @return the managed plugins, filled in
     */
    List<Plugin> managedPlugins() {
        List<Plugin> found = new ArrayList<>();
        for (Pom one = this; one != null; one = one.parent.orElse(null)) {
            child(one.project, "build")
                    .flatMap(build -> child(build, "pluginManagement"))
                    .flatMap(management -> child(management, "plugins"))
                    .ifPresent(plugins -> found.addAll(plugins(plugins, this)));
        }
        return found;
    }

    /**
     * Whether the build section names something, such as a source directory of its own.
     *
     * @param name the element
     * @return true when the file's build section has it
     */
    boolean buildNames(String name) {
        return child(project, "build").flatMap(build -> child(build, name)).isPresent();
    }

    /** This file's dependencies over its parents', filled in as the file being read sees them. */
    private Map<String, Dependency> inherited(Pom context) {
        Map<String, Dependency> found =
                parent.map(above -> above.inherited(context)).orElseGet(LinkedHashMap::new);
        child(project, "dependencies")
                .map(list -> dependencies(list, context))
                .orElse(List.of())
                .forEach(one -> found.put(one.identity(), one));
        return found;
    }

    private List<Dependency> managedIn(Pom context) {
        return child(project, "dependencyManagement")
                .flatMap(management -> child(management, "dependencies"))
                .map(list -> dependencies(list, context))
                .orElse(List.of());
    }

    private Optional<String> own(String name) {
        return child(project, name).map(one -> one.getTextContent().strip());
    }

    private static List<Dependency> dependencies(Element list, Pom context) {
        List<Dependency> found = new ArrayList<>();
        for (Element one : elements(list)) {
            List<String> exclusions = child(one, "exclusions").stream()
                    .flatMap(all -> elements(all).stream())
                    .map(rule -> context.resolve(text(rule, "groupId").orElse("*")) + ":"
                            + context.resolve(text(rule, "artifactId").orElse("*")))
                    .toList();
            found.add(new Dependency(
                    context.resolve(text(one, "groupId").orElse("")),
                    context.resolve(text(one, "artifactId").orElse("")),
                    text(one, "version").map(context::resolve),
                    text(one, "scope").map(context::resolve).orElse("compile"),
                    text(one, "optional")
                            .map(context::resolve)
                            .filter("true"::equals)
                            .isPresent(),
                    exclusions,
                    context.resolve(text(one, "type").orElse("jar"))
                            + text(one, "classifier")
                                    .map(context::resolve)
                                    .map(named -> ":" + named)
                                    .orElse("")));
        }
        return found;
    }

    private static List<Plugin> plugins(Element list, Pom context) {
        List<Plugin> found = new ArrayList<>();
        for (Element one : elements(list)) {
            Map<String, String> configuration = new LinkedHashMap<>();
            child(one, "configuration").ifPresent(all -> elements(all)
                    .forEach(value -> configuration.put(
                            value.getTagName(),
                            context.resolve(value.getTextContent().strip()))));
            found.add(new Plugin(
                    context.resolve(text(one, "groupId").orElse("org.apache.maven.plugins")),
                    context.resolve(text(one, "artifactId").orElse("")),
                    text(one, "version").map(context::resolve),
                    configuration));
        }
        return found;
    }

    private static Coordinates coordinates(Element parent) {
        return new Coordinates(
                text(parent, "groupId").orElse(""),
                text(parent, "artifactId").orElse(""),
                text(parent, "version").orElse(""));
    }

    private static Element parse(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        } catch (ParserConfigurationException | SAXException malformed) {
            throw new IllegalStateException(file + " is not a project file", malformed);
        }
    }

    private static Optional<Element> child(Element parent, String name) {
        return elements(parent).stream()
                .filter(one -> one.getTagName().equals(name))
                .findFirst();
    }

    private static Optional<String> text(Element parent, String name) {
        return child(parent, name).map(one -> one.getTextContent().strip());
    }

    private static List<Element> elements(Element parent) {
        List<Element> found = new ArrayList<>();
        for (Node one = parent.getFirstChild(); one != null; one = one.getNextSibling()) {
            if (one instanceof Element element) {
                found.add(element);
            }
        }
        return found;
    }
}
