package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.cli.Guide.Sequence;
import io.github.rkaushikdev.sheaveline.cli.Guide.Step;
import io.github.rkaushikdev.sheaveline.reports.Forms;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The guide is only worth having if every command it names is a real one. */
class GuideTest {

    private final Shell shell = new Shell();

    private static CommandLine tree() {
        return Main.tree(new Terminal(
                name -> null,
                new BufferedReader(new StringReader("")),
                new PrintWriter(new StringWriter(), true),
                new PrintWriter(new StringWriter(), true)));
    }

    /** Walk the command tree the way the parser does when somebody types it. */
    private static boolean resolves(String words) {
        CommandLine found = tree();
        for (String word : words.split(" ")) {
            found = found.getSubcommands().get(word);
            if (found == null) {
                return false;
            }
        }
        return true;
    }

    private static List<Step> everyStep() {
        return Guide.SEQUENCES.stream().flatMap(one -> one.steps().stream()).toList();
    }

    private static String head(Step step) {
        return step.command().split(" ")[0];
    }

    @Test
    void everyCommandTheGuideNamesIsOneThisProgramHas() {
        assertThat(resolves("lift drift")).isTrue();
        assertThat(resolves("lift sideways")).isFalse();
        for (Sequence one : Guide.SEQUENCES) {
            for (Step step : one.steps()) {
                assertThat(resolves(step.command()))
                        .as(one.name() + ": " + step.command())
                        .isTrue();
            }
        }
    }

    @Test
    void everyStepSaysWhyItIsWhereItIs() {
        for (Step step : everyStep()) {
            assertThat(step.why()).as(step.command()).isNotBlank();
            assertThat(Character.isUpperCase(step.why().charAt(0)))
                    .as(step.why())
                    .isFalse();
        }
    }

    @Test
    void noTwoSequencesShareAName() {
        List<String> names = Guide.SEQUENCES.stream().map(Sequence::name).toList();
        assertThat(new HashSet<>(names)).hasSameSizeAs(names);
    }

    @Test
    void everySequenceSaysWhenItIsRun() {
        assertThat(Guide.SEQUENCES).allMatch(one -> !one.when().isBlank());
    }

    @Test
    void aSequenceIsFoundByAnyWordOfItsName() {
        assertThat(Guide.sequence("day"))
                .containsSame(Guide.sequence("a day on the hill").orElseThrow());
        assertThat(Guide.sequence("season")).map(Sequence::name).contains("starting a season");
        assertThat(Guide.sequence("  DAY  ")).isPresent();
    }

    @Test
    void aSequenceNobodyHas() {
        assertThat(Guide.sequence("grooming")).isEmpty();
        assertThat(Guide.sequence("")).isEmpty();
    }

    @Test
    void thePageIsEightyColumns() {
        assertThat(Guide.render(Guide.SEQUENCES).lines()).allMatch(one -> one.length() <= Forms.PAGE_WIDTH);
    }

    @Test
    void theWholeGuidePrints() {
        Shell.Reply reply = shell.run("guide");
        assertThat(reply.code()).isZero();
        for (Sequence one : Guide.SEQUENCES) {
            assertThat(reply.out()).contains(one.name());
        }
        assertThat(reply.out()).contains(Guide.SEQUENCES.size() + " sequences");
    }

    @Test
    void oneSequencePrintsOnItsOwn() {
        Shell.Reply reply = shell.run("guide", "day");
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("a day on the hill").doesNotContain("closing a season");
    }

    @Test
    void askingForASequenceNobodyHasNamesTheOnesThereAre() {
        Shell.Reply reply = shell.run("guide", "grooming");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.out()).contains("no sequence called grooming", "closing a season");
    }

    @Test
    void theStepsAreNumberedFromOne() {
        assertThat(shell.run("guide", "opening").out()).contains("1. sheaveline setup", "4. sheaveline lift shoot");
    }

    /** A group nobody's sequence mentions is a group nobody knows when to run. */
    @Test
    void theGuideReachesEveryCommandGroupThisProgramHas() {
        Set<String> named = everyStep().stream().map(GuideTest::head).collect(Collectors.toSet());
        Set<String> groups = tree().getSubcommands().entrySet().stream()
                .filter(one -> !one.getValue().getSubcommands().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertThat(groups).isNotEmpty();
        assertThat(named).containsAll(groups);
    }

    /** The drawing commands hang off lift. A list typed by hand said otherwise. */
    @Test
    void theGuideDoesNotNameAGroupThisProgramDoesNotHave() {
        Map<String, CommandLine> commands = tree().getSubcommands();
        for (Step step : everyStep()) {
            assertThat(commands).as("there is no " + head(step) + " command").containsKey(head(step));
        }
    }
}
