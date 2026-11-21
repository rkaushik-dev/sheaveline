package io.github.rkaushikdev.sheaveline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.weather.Station;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class LookupsTest {

    /** Where the route classes live, read as source. */
    static final Path ROUTES = Path.of("src/main/java/io/github/rkaushikdev/sheaveline/api/routes");

    /** A method declaration up to its opening parenthesis: the name is the group. */
    static final Pattern METHOD = Pattern.compile("\\n\\s+public\\s+[\\w<>,.?\\s\\[\\]]+?\\s(\\w+)\\s*\\(");

    @Autowired
    private MockMvc mvc;

    private Settings built;
    private UnitOfWork work;
    private Lookup look;

    @BeforeEach
    void putAMachineAndAStationOnFile() {
        built = Wire.settings();
        try (UnitOfWork seeding = UnitOfWork.on(built.database()).open()) {
            seeding.lifts().add(Hill.johnPaul());
            seeding.weather().addStation(new Station("summit", Length.ofFeet(7981)));
            seeding.commit();
        }
        work = UnitOfWork.on(built.database()).open();
        look = new Lookup(work);
    }

    @AfterEach
    void closeTheLookup() {
        work.close();
    }

    static List<Path> routeSources() throws IOException {
        try (Stream<Path> files = Files.list(ROUTES)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    /** A route method as source: its name, what is between its parentheses, and its body. */
    record Method(String file, String name, String parameters, String body) {}

    static List<Method> methodsOf(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        List<Method> found = new ArrayList<>();
        Matcher header = METHOD.matcher(source);
        while (header.find()) {
            int open = header.end() - 1;
            int close = matching(source, open, '(', ')');
            int brace = source.indexOf('{', close);
            int semicolon = source.indexOf(';', close);
            if (brace < 0 || (semicolon >= 0 && semicolon < brace)) {
                continue;
            }
            int end = matching(source, brace, '{', '}');
            found.add(new Method(
                    file.getFileName().toString(),
                    header.group(1),
                    source.substring(open + 1, close),
                    source.substring(brace, end + 1)));
        }
        return found;
    }

    /** Where the bracket opened at {@code at} closes, stepping over quoted text. */
    static int matching(String source, int at, char opens, char closes) {
        int depth = 0;
        for (int index = at; index < source.length(); index++) {
            char here = source.charAt(index);
            if (here == '"') {
                index++;
                while (source.charAt(index) != '"') {
                    index += source.charAt(index) == '\\' ? 2 : 1;
                }
            } else if (here == opens) {
                depth++;
            } else if (here == closes && --depth == 0) {
                return index;
            }
        }
        throw new IllegalStateException("unbalanced source");
    }

    @Test
    void aMachineOnTheRegisterComesBack() {
        assertThat(look.lift("JP").name()).isEqualTo("John Paul");
        assertThat(look.liftCode("JP")).isEqualTo(new LiftCode("JP"));
    }

    @Test
    void aMachineThatIsNotOnTheRegister() {
        assertThatThrownBy(() -> look.lift("ZZ")).isInstanceOf(NotFound.class).hasMessage("there is no lift ZZ");
        assertThatThrownBy(() -> look.liftCode("ZZ")).isInstanceOf(NotFound.class);
    }

    @Test
    void aRopeAStationAnOrderAndAnIncidentAllAnswerTheSameWay() {
        Map<ThrowingCallable, String> calls = Map.of(
                () -> look.rope("ZZZ-2019-0404"), "there is no rope ZZZ-2019-0404",
                () -> look.station("nowhere"), "there is no station called nowhere",
                () -> look.workOrder(404), "there is no work order 404",
                () -> look.incident(404), "there is no incident 404");
        calls.forEach((call, wanted) ->
                assertThatThrownBy(call).isInstanceOf(NotFound.class).hasMessage(wanted));
    }

    @Test
    void aStationThatIsOnFile() {
        assertThat(look.station("summit").name()).isEqualTo("summit");
    }

    /** The private helpers this replaced, and the blocks they inlined. */
    @Test
    void noRouteModuleWritesItsOwnLookupAnyMore() throws IOException {
        List<Pattern> forbidden = List.of(
                Pattern.compile("private\\s+\\S+\\s+lift\\("),
                Pattern.compile("knownLift\\("),
                Pattern.compile("private\\s+\\S+\\s+rope\\("),
                Pattern.compile("new NotFound\\("));
        List<Path> sources = routeSources();
        assertThat(sources).isNotEmpty();
        for (Path path : sources) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (Pattern pattern : forbidden) {
                assertThat(pattern.matcher(source).find())
                        .as(path.getFileName() + ": " + pattern)
                        .isFalse();
            }
        }
        String lookup = Files.readString(ROUTES.resolveSibling("Lookup.java"), StandardCharsets.UTF_8);
        assertThat(forbidden.get(3).matcher(lookup).find())
                .as("the sweep finds what it looks for")
                .isTrue();
    }

    @Test
    void everyRouteThatLooksSomethingUpAsksForTheLookup() throws IOException {
        int looking = 0;
        for (Path path : routeSources()) {
            for (Method method : methodsOf(path)) {
                if (!method.body().contains("look.")) {
                    continue;
                }
                looking++;
                assertThat(method.parameters())
                        .as(method.file() + ":" + method.name())
                        .containsPattern("\\bLookup\\s+look\\b");
            }
        }
        assertThat(looking).as("route methods that use a lookup").isPositive();
    }

    /** Two arguments, one unit of work, or a withdrawal would not commit. */
    @Test
    void theLookupAndTheRouteShareOneSession() {
        work.close();
        Http.Answer reply = new Http(mvc).delete("/lifts/JP", Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(204);
        try (UnitOfWork after = UnitOfWork.on(built.database()).open()) {
            assertThat(after.lifts().exists(new LiftCode("JP"))).isFalse();
        }
    }

    @Test
    void theSentenceTheClientReadsIsTheSameWhicheverRouteAsked() {
        Http client = new Http(mvc);
        for (String path : List.of("/lifts/ZZ", "/lifts/ZZ/towers", "/lifts/ZZ/spans")) {
            Http.Answer reply = client.get(path, Wire.headers());
            assertThat(reply.status()).as(path).isEqualTo(404);
            assertThat(reply.json().get("detail").asText()).isEqualTo("there is no lift ZZ");
            assertThat(reply.json().get("kind").asText()).isEqualTo("NotFound");
        }
    }
}
