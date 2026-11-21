package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/** The options sixteen commands were each writing out for themselves. */
class CliCommonTest {

    private static final Path CLI = Path.of("src/main/java/io/github/rkaushikdev/sheaveline/cli");
    private static final LocalDate TODAY = LocalDate.of(2025, 1, 15);
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 1, 15, 11, 30);

    /** A private helper method: its name is the second group. */
    private static final Pattern PRIVATE_HELPER =
            Pattern.compile("^\\s*private static [\\w<>\\[\\], .?]+? (\\w+)\\(", Pattern.MULTILINE);

    private static final class Clock implements TellsTheTime {

        @Override
        public LocalDate today() {
            return TODAY;
        }

        @Override
        public LocalDateTime now() {
            return NOW;
        }
    }

    @Command(name = "one")
    static final class OnADay implements Runnable {

        @Mixin
        private Common.DayOption on;

        private final PrintWriter out;

        OnADay(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void run() {
            out.println(Common.printed(Optional.of(on.dayOf(new Clock())), "nothing given"));
        }
    }

    @Command(name = "one")
    static final class AtAMoment implements Runnable {

        @Mixin
        private Common.MomentOption at;

        private final PrintWriter out;

        AtAMoment(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void run() {
            out.println(at.momentOf(new Clock()));
        }
    }

    @Command(name = "one")
    @Common.DayHelp("The day the walk was done.")
    static final class Walked implements Runnable {

        @Mixin
        private Common.DayOption on;

        @Override
        public void run() {}
    }

    /** Run a one command program and hand back the exit code and what it printed. */
    private static Map.Entry<Integer, String> ran(Runnable command, StringWriter out, String... args) {
        CommandLine line = new CommandLine(command)
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(new StringWriter(), true));
        int code = line.execute(args);
        return Map.entry(code, out.toString().strip());
    }

    private static Map.Entry<Integer, String> onADay(String... args) {
        StringWriter out = new StringWriter();
        return ran(new OnADay(new PrintWriter(out, true)), out, args);
    }

    private static Map.Entry<Integer, String> atAMoment(String... args) {
        StringWriter out = new StringWriter();
        return ran(new AtAMoment(new PrintWriter(out, true)), out, args);
    }

    /** Every command source, by file name; never empty, or the checks below would read nothing. */
    private static Map<String, String> sources() throws IOException {
        Map<String, String> read = new HashMap<>();
        try (Stream<Path> found = Files.list(CLI)) {
            for (Path one : found.filter(Files::isRegularFile).toList()) {
                if (one.getFileName().toString().endsWith(".java")) {
                    read.put(one.getFileName().toString(), Files.readString(one));
                }
            }
        }
        assertThat(read).containsKeys("Common.java", "Main.java", "Console.java", "DayCommands.java");
        return read;
    }

    @Test
    void aDayThatWasGivenWins() {
        assertThat(Common.dayOf(new Clock(), Optional.of(LocalDate.of(2024, 12, 1))))
                .isEqualTo(LocalDate.of(2024, 12, 1));
    }

    @Test
    void aDayThatWasNotGivenFallsBackToTheConsole() {
        assertThat(Common.dayOf(new Clock(), Optional.empty())).isEqualTo(TODAY);
    }

    @Test
    void aMomentFallsBackTheSameWay() {
        assertThat(Common.momentOf(new Clock(), Optional.empty())).isEqualTo(NOW);
        assertThat(Common.momentOf(new Clock(), Optional.of(LocalDateTime.of(2024, 12, 1, 9, 0))))
                .isEqualTo(LocalDateTime.of(2024, 12, 1, 9, 0));
    }

    @Test
    void aDateOnAPageOrAWordSayingThereIsNotOne() {
        assertThat(Common.printed(Optional.of(LocalDate.of(2025, 1, 15)))).isEqualTo("2025-01-15");
        assertThat(Common.printed(Optional.empty())).isEmpty();
        assertThat(Common.printed(Optional.empty(), "never")).isEqualTo("never");
    }

    @Test
    void theDayOptionTakesTheFormABoardFormTakes() {
        assertThat(onADay("--on", "2024-12-01").getValue()).isEqualTo("2024-12-01");
        assertThat(onADay().getValue()).isEqualTo(TODAY.toString());
        assertThat(onADay("--on", "1 Dec 2024").getKey()).isEqualTo(2);
    }

    @Test
    void theMomentOptionTakesAWholeDayAsWell() {
        assertThat(atAMoment("--at", "2024-12-01 09:15").getValue())
                .isEqualTo(LocalDateTime.of(2024, 12, 1, 9, 15).toString());
        assertThat(atAMoment("--at", "2024-12-01").getValue())
                .isEqualTo(LocalDateTime.of(2024, 12, 1, 0, 0).toString());
        assertThat(atAMoment().getValue()).isEqualTo(NOW.toString());
    }

    @Test
    void theHelpCanBeSaidDifferentlyPerCommand() {
        CommandLine line = new CommandLine(new Walked());
        Common.sayTheDay(line);
        assertThat(line.getUsageMessage())
                .contains("The day the walk was done.")
                .doesNotContain(Common.DAY_HELP);
        assertThat(new CommandLine(new OnADay(new PrintWriter(new StringWriter()))).getUsageMessage())
                .contains(Common.DAY_HELP);
    }

    @Test
    void theFormatsAreWhatATramwayBoardFormUses() {
        assertThat(Common.DAY_PATTERN).isEqualTo("uuuu-MM-dd");
        assertThat(Common.MOMENT_PATTERN).isEqualTo("uuuu-MM-dd HH:mm");
    }

    @Test
    void noCommandModuleDeclaresTheFormatsForItselfAnyMore() throws IOException {
        Map<String, String> read = sources();
        assertThat(read.get("Common.java")).contains('"' + Common.DAY_PATTERN + '"');
        read.forEach((name, source) -> {
            if (!name.equals("Common.java")) {
                assertThat(source).as(name).doesNotContain("\"uuuu-MM-dd\"", "\"uuuu-MM-dd HH:mm\"");
            }
        });
    }

    @Test
    void noCommandModuleWritesTheOnOrAtOptionOutByHand() throws IOException {
        sources()
                .forEach((name, source) ->
                        assertThat(source).as(name).doesNotContain("names = \"--on\"", "names = \"--at\""));
    }

    @Test
    void theTwoLoadersLiveInOnePlace() throws Exception {
        Method machine = Common.class.getDeclaredMethod("machine", UnitOfWork.class, String.class);
        Method rope = Common.class.getDeclaredMethod("haulRope", UnitOfWork.class, String.class);
        assertThat(List.of(machine.getDeclaringClass(), rope.getDeclaringClass()))
                .containsOnly(Common.class);
        Pattern loader = Pattern.compile("(private|static)[^=;(]*\\sload\\(");
        sources()
                .forEach((name, source) ->
                        assertThat(loader.matcher(source).find()).as(name).isFalse());
    }

    @Test
    void noTwoCommandModulesDefineTheSamePrivateHelper() throws IOException {
        Map<String, String> seen = new HashMap<>();
        sources().forEach((name, source) -> {
            Matcher found = PRIVATE_HELPER.matcher(source);
            while (found.find()) {
                String helper = found.group(1);
                String first = seen.putIfAbsent(helper, name);
                assertThat(first == null || first.equals(name))
                        .as(helper + " in " + name + " and " + first)
                        .isTrue();
            }
        });
        assertThat(seen).containsEntry("failures", "DayCommands.java").containsEntry("journalFor", "Console.java");
    }

    /** Otherwise the shared options would have to know the whole console. */
    @Test
    void theClockIsAskedForByShapeRatherThanByType() throws Exception {
        assertThat(TellsTheTime.class.isInterface()).isTrue();
        assertThat(TellsTheTime.class).isAssignableFrom(Console.class);
        assertThat(Common.class.getDeclaredMethod("dayOf", TellsTheTime.class, Optional.class))
                .isNotNull();
        assertThat(sources().get("Common.java")).doesNotContain("Console", "Main.");
    }
}
