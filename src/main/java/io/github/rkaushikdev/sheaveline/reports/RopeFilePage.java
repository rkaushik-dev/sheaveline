package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.Finding;
import io.github.rkaushikdev.sheaveline.ropework.MagneticTest;
import io.github.rkaushikdev.sheaveline.ropework.RopeFile;
import io.github.rkaushikdev.sheaveline.ropework.SpliceRegister;
import io.github.rkaushikdev.sheaveline.ropework.Verdict;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The rope file, printed.
 *
 * <p>This is the document that goes in the folder with the rope's name on it and follows the rope to
 * whatever machine it ends up on. The board asks for it when a rope is retired and an insurer asks
 * for it when something has gone wrong, and in both cases the question is the same: show me every
 * time somebody looked at this rope and what they found.
 *
 * <p>So it is a history rather than a status. The current verdict is on it, once, at the top, and
 * the rest of the page is the walks in the order they were walked.
 *
 * @param rope the rope
 * @param on the day the page is printed, which the rope's age and the verdict are read against
 * @param machine the machine the rope is on, as the page names it
 * @param wear the caliper walks, oldest first
 * @param breaks the broken wire counts, oldest first
 * @param tests the magnetic tests, oldest first
 * @param splices the splice register, when there is one
 */
public record RopeFilePage(
        HaulRope rope,
        LocalDate on,
        String machine,
        List<WearSurvey> wear,
        List<BreakSurvey> breaks,
        List<MagneticTest> tests,
        Optional<SpliceRegister> splices) {

    /** A rope file page. */
    public RopeFilePage {
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(splices, "splices");
        wear = List.copyOf(wear);
        breaks = List.copyOf(breaks);
        tests = List.copyOf(tests);
    }

    /**
     * A page for a rope nobody has looked at yet.
     *
     * @param rope the rope
     * @param on the day the page is printed
     * @param machine the machine the rope is on
     * @return the page
     */
    public static RopeFilePage of(HaulRope rope, LocalDate on, String machine) {
        return of(rope, on, machine, List.of(), List.of(), List.of(), Optional.empty());
    }

    /**
     * A page with every survey put in the order it was taken.
     *
     * @param rope the rope
     * @param on the day the page is printed
     * @param machine the machine the rope is on
     * @param wear the caliper walks, in any order
     * @param breaks the broken wire counts, in any order
     * @param tests the magnetic tests, in any order
     * @param splices the splice register, when there is one
     * @return the page
     */
    public static RopeFilePage of(
            HaulRope rope,
            LocalDate on,
            String machine,
            List<WearSurvey> wear,
            List<BreakSurvey> breaks,
            List<MagneticTest> tests,
            Optional<SpliceRegister> splices) {
        return new RopeFilePage(
                rope,
                on,
                machine,
                wear.stream().sorted(Comparator.comparing(WearSurvey::taken)).toList(),
                breaks.stream().sorted(Comparator.comparing(BreakSurvey::taken)).toList(),
                tests.stream().sorted(Comparator.comparing(MagneticTest::taken)).toList(),
                splices);
    }

    /**
     * The verdict, read off the most recent of each kind of survey and the splice register.
     *
     * @return the rope file
     */
    public RopeFile file() {
        return new RopeFile(latest(wear), latest(breaks), latest(tests), splices, Optional.empty(), Optional.empty());
    }

    /**
     * How many times somebody looked at the rope: every walk, count and test.
     *
     * @return the count
     */
    public int timesLookedAt() {
        return wear.size() + breaks.size() + tests.size();
    }

    /**
     * The page.
     *
     * @return the report
     */
    public Report render() {
        Report page = new Report();
        page.heading("rope file " + rope.serial());
        page.field("printed", on.toString());
        page.field("on machine", machine);
        page.field("construction", rope.spec().construction().value());
        page.field("grade", rope.spec().grade().value());
        page.field("lay", rope.spec().lay().value());
        page.field("nominal diameter", rope.diameter().formatInches(3));
        page.field("condemning diameter", rope.spec().condemningDiameter().formatInches(3));
        page.field("breaking strength", rope.spec().breakingStrength().formatPounds());
        page.field("length", feet(rope.length()));
        page.field("installed", rope.installed().toString());
        page.field("splices", rope.splices());
        page.field("seasons in service", rope.seasonsInService(on));

        RopeFile held = file();
        page.section("verdict");
        page.field("standing", held.verdict(on).value());
        page.field("may run", held.mayRun(on) ? "yes" : "no");
        List<Finding> findings = held.findings(on);
        for (Finding one : findings) {
            page.line("    " + one);
        }
        if (findings.isEmpty()) {
            page.line("    nothing recorded against it");
        }

        if (!wear.isEmpty()) {
            page.section("caliper walks");
            page.table(Table.of(
                    List.of(
                            new Column("taken", 11),
                            new Column("inspector", 18),
                            new Column("smallest", 10, Align.RIGHT),
                            new Column("station", 10, Align.RIGHT),
                            new Column("verdict", 12)),
                    wear.stream()
                            .map(one -> List.of(
                                    one.taken().toString(),
                                    one.inspector(),
                                    one.smallest().mean().formatInches(3),
                                    feet(one.smallest().station()),
                                    Verdict.assessWear(one).value()))
                            .toList()));
        }

        if (!breaks.isEmpty()) {
            page.section("broken wire counts");
            page.table(Table.of(
                    List.of(
                            new Column("taken", 11),
                            new Column("inspector", 18),
                            new Column("counted", 8, Align.RIGHT),
                            new Column("verdict", 12)),
                    breaks.stream()
                            .map(one -> List.of(
                                    one.taken().toString(),
                                    one.inspector(),
                                    one.totalBreaks(),
                                    Verdict.assessBreaks(one).value()))
                            .toList()));
        }

        if (!tests.isEmpty()) {
            page.section("magnetic tests");
            page.table(Table.of(
                    List.of(
                            new Column("taken", 11),
                            new Column("contractor", 24),
                            new Column("peak loss", 10, Align.RIGHT),
                            new Column("fault", 6, Align.RIGHT),
                            new Column("verdict", 12)),
                    tests.stream()
                            .map(one -> List.of(
                                    one.taken().toString(),
                                    one.contractor(),
                                    one.peakLoss() + "%",
                                    one.largestFault(),
                                    Verdict.assessTest(one).value()))
                            .toList()));
        }

        splices.ifPresent(register -> {
            page.section("splices");
            page.table(Table.of(
                    List.of(
                            new Column("station", 10, Align.RIGHT),
                            new Column("made", 11),
                            new Column("by", 18),
                            new Column("tucks", 6, Align.RIGHT),
                            new Column("kind", 12)),
                    register.splices().stream()
                            .map(one -> List.of(
                                    feet(one.station()),
                                    one.made().toString(),
                                    one.madeBy(),
                                    one.tucks(),
                                    one.mill() ? "mill" : "field"))
                            .toList()));
        });

        page.blank();
        page.line("looked at " + timesLookedAt() + " times");
        return page;
    }

    /**
     * The page as text.
     *
     * @return every line, each ended by a newline
     */
    public String text() {
        return render().text();
    }

    private static String feet(Length length) {
        return Decimals.fixed(length.feet(), 0) + " ft";
    }

    private static <T> Optional<T> latest(List<T> taken) {
        return taken.isEmpty() ? Optional.empty() : Optional.of(taken.get(taken.size() - 1));
    }
}
