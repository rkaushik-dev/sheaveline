package io.github.rkaushikdev.sheaveline.cli;

import static io.github.rkaushikdev.sheaveline.cli.Common.wholeFeet;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.capacity.LineSpacing;
import io.github.rkaushikdev.sheaveline.capacity.UphillCapacity;
import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.AlignmentHistory;
import io.github.rkaushikdev.sheaveline.plant.AlignmentSurvey;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.SpanLine;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.plant.TowerRef;
import io.github.rkaushikdev.sheaveline.plant.TowerShot;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.Report;
import io.github.rkaushikdev.sheaveline.reports.Table;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Commands about the machines.
 *
 * <p>These are what the office runs when somebody telephones and asks a question about a lift.
 * They print the eighty column form rather than a table of json, because the answer usually ends up
 * pinned to a noticeboard.
 */
final class PlantCommands {

    private static final BigDecimal FIVE_YEARS = BigDecimal.valueOf(5);

    private PlantCommands() {}

    /**
     * Read a shot written tower:station:crossarm:offset, the order it comes off the instrument. The
     * station and the crossarm are in feet and the offset in inches.
     *
     * @param text the shot as typed
     * @return the shot
     * @throws ParseError when it does not have four parts or a part does not read
     */
    static TowerShot parseShot(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length != 4) {
            throw new ParseError(text, "a shot written tower:station:crossarm:offset");
        }
        return new TowerShot(
                TowerRef.parse(parts[0].strip()),
                Length.parse(parts[1].strip() + " ft"),
                Length.parse(parts[2].strip() + " ft"),
                Length.parse(parts[3].strip() + " in"));
    }

    private static String rate(Optional<Angle> found) {
        return found.filter(one -> one.millidegrees() != 0)
                .map(Angle::formatDegrees)
                .orElse("flat");
    }

    private static String years(Optional<BigDecimal> found) {
        return found.map(one -> Decimals.fixed(one, 1)).orElse("not moving");
    }

    /** The machines on the hill. */
    @Command(
            name = "lift",
            description = "The machines on the hill.",
            subcommands = {
                ListLifts.class,
                ShowLift.class,
                ShowTowers.class,
                ShowSpans.class,
                WithdrawLift.class,
                ShootTowers.class,
                TowerDrift.class,
                DrawingCommands.LoadLift.class,
                DrawingCommands.ExportLift.class,
                DrawingCommands.CheckDrawing.class
            })
    static final class LiftGroup {}

    /** Every machine on file, one a line. */
    @Command(name = "list", description = "Every machine on file, one a line.")
    static final class ListLifts implements Action {

        @Override
        public int run(Console console) {
            List<List<Object>> rows = new ArrayList<>();
            try (UnitOfWork work = console.work()) {
                for (Lift one : work.lifts().all()) {
                    rows.add(List.of(
                            one.code().toString(),
                            one.name(),
                            one.liftClass().value(),
                            one.carriersOnLine(),
                            one.designSpeed().formatFpm()));
                }
            }
            Table table = Table.of(
                    List.of(
                            new Column("code", 6),
                            new Column("name", 20),
                            new Column("class", 22),
                            new Column("carriers", 8, Align.RIGHT),
                            new Column("speed", 10, Align.RIGHT)),
                    rows);
            console.page(table.lines());
            return Main.OK;
        }
    }

    /** Everything about one machine, on one page. */
    @Command(name = "show", description = "Everything about one machine, on one page.")
    static final class ShowLift implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Override
        public int run(Console console) {
            Report page = new Report();
            try (UnitOfWork work = console.work()) {
                Lift found = Common.machine(work, code);
                LineSpacing line = LineSpacing.of(found);
                UphillCapacity figures =
                        new UphillCapacity(line, found.carriers().kind().seats());
                page.heading(found.code() + " " + found.name());
                page.field("class", found.liftClass().value());
                page.field("commissioned", found.commissioned().toString());
                page.field("age", found.ageInYears(console.today()) + " years");
                page.field("bearing", found.bearing().toString());
                page.field("design speed", found.designSpeed().formatFpm());
                page.field("horizontal length", wholeFeet(found.horizontalLength()) + " ft");
                page.field("vertical rise", wholeFeet(found.verticalRise()) + " ft");
                page.field("slope length", wholeFeet(found.slopeLength()) + " ft");
                page.field("ride time", found.rideTime().formatClock());
                page.field("carriers", found.carriersOnLine());
                page.field("seats a carrier", found.carriers().kind().seats());
                page.field("carrier interval", line.interval().formatClock());
                page.field("people an hour", String.format(Locale.ROOT, "%,d", figures.personsPerHour()));
                page.field("rope", found.rope().serial().toString());
                page.field("rope length agrees", found.ropeLengthAgrees() ? "yes" : "no");
            }
            console.page(page.lines());
            return Main.OK;
        }
    }

    /** The tower schedule. */
    @Command(name = "towers", description = "The tower schedule.")
    static final class ShowTowers implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Override
        public int run(Console console) {
            List<List<Object>> rows = new ArrayList<>();
            try (UnitOfWork work = console.work()) {
                for (Tower one : Common.machine(work, code).towers()) {
                    rows.add(List.of(
                            one.ref().toString(),
                            wholeFeet(one.station()),
                            wholeFeet(one.baseElevation()),
                            wholeFeet(one.height()),
                            wholeFeet(one.crossarmElevation()),
                            one.uphill().kind().value(),
                            one.sheaveCount()));
                }
            }
            Table table = Table.of(
                    List.of(
                            new Column("tower", 8),
                            new Column("station", 8, Align.RIGHT),
                            new Column("base", 8, Align.RIGHT),
                            new Column("height", 7, Align.RIGHT),
                            new Column("crossarm", 9, Align.RIGHT),
                            new Column("kind", 12),
                            new Column("sheaves", 8, Align.RIGHT)),
                    rows);
            console.page(table.lines());
            return Main.OK;
        }
    }

    /** The line cut into spans, longest and steepest marked. */
    @Command(name = "spans", description = "The line cut into spans, longest and steepest marked.")
    static final class ShowSpans implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Override
        public int run(Console console) {
            List<List<Object>> rows = new ArrayList<>();
            try (UnitOfWork work = console.work()) {
                SpanLine spans = Common.machine(work, code).spans();
                int longest = spans.longest().index();
                int steepest = spans.steepest().index();
                for (Span span : spans) {
                    List<String> marks = new ArrayList<>();
                    if (span.index() == longest) {
                        marks.add("longest");
                    }
                    if (span.index() == steepest) {
                        marks.add("steepest");
                    }
                    rows.add(List.of(
                            span.index(),
                            span.label(),
                            wholeFeet(span.horizontal()),
                            wholeFeet(span.rise()),
                            span.slope().formatDegrees(1),
                            String.join(" ", marks)));
                }
            }
            Table table = Table.of(
                    List.of(
                            new Column("no", 3, Align.RIGHT),
                            new Column("between", 28),
                            new Column("run", 7, Align.RIGHT),
                            new Column("rise", 7, Align.RIGHT),
                            new Column("slope", 10, Align.RIGHT),
                            new Column("note", 18)),
                    rows);
            console.page(table.lines());
            return Main.OK;
        }
    }

    /** Take a machine off the register. It takes its towers with it. */
    @Command(name = "withdraw", description = "Take a machine off the register. It takes its towers with it.")
    static final class WithdrawLift implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(names = "--yes", description = "Do it without asking.")
        private boolean yes;

        @Override
        public int run(Console console) {
            try (UnitOfWork work = console.work()) {
                Lift found = Common.machine(work, code);
                if (!yes) {
                    console.confirm("take " + found.code() + " " + found.name() + " off the register?");
                }
                work.lifts().remove(found.code());
                work.commit();
            }
            console.say(code.toUpperCase(Locale.ROOT) + " withdrawn");
            return Main.OK;
        }
    }

    /** Write an autumn total station survey into the file. */
    @Command(
            name = "shoot",
            description = {
                "Write an autumn total station survey into the file.",
                "",
                "A shot is tower:station:crossarm:offset, all in feet except the offset, which is how far the"
                        + " top is out of plumb and is written in inches because that is what it is read to."
            })
    @Common.DayHelp("The day it was shot.")
    static final class ShootTowers implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(names = "--surveyor", required = true, description = "Who ran the instrument.")
        private String surveyor;

        @Option(
                names = "--shot",
                required = true,
                paramLabel = "TOWER:STATION:CROSSARM:OFFSET",
                description = "One tower. Give it once a tower.")
        private List<String> shots;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            Lift found;
            AlignmentSurvey survey;
            try (UnitOfWork work = console.work()) {
                found = Common.machine(work, code);
                survey = AlignmentSurvey.of(
                        found.towers(),
                        when,
                        surveyor,
                        shots.stream().map(PlantCommands::parseShot).toList());
                work.alignment().add(survey);
                work.commit();
            }
            console.say(found.code() + " shot " + when + " by " + surveyor + ", " + survey.size() + " towers");
            for (String line : survey.findings()) {
                console.say("  " + line);
            }
            if (survey.isClean()) {
                console.say("  every tower is where the drawing says it is");
            }
            return Main.OK;
        }
    }

    /** How fast each tower is going over, and when it reaches the tolerance. */
    @Command(name = "drift", description = "How fast each tower is going over, and when it reaches the tolerance.")
    static final class TowerDrift implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Override
        public int run(Console console) {
            LiftCode wanted;
            AlignmentHistory history;
            try (UnitOfWork work = console.work()) {
                wanted = LiftCode.parse(code);
                history = work.alignment().history(wanted);
            }
            AlignmentSurvey latest = history.latest();
            console.say(wanted + " read across " + history.size() + " surveys to " + latest.taken());
            List<List<Object>> rows = new ArrayList<>();
            for (TowerShot one : latest.shots()) {
                rows.add(List.of(
                        one.ref().toString(),
                        latest.lean(one.ref()).formatDegrees(),
                        rate(history.leanAYear(one.ref())),
                        latest.settlement(one.ref()).formatInches(2),
                        years(history.yearsUntilOutOfPlumb(one.ref()))));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("tower", 8),
                                    new Column("lean now", 10, Align.RIGHT),
                                    new Column("a year", 10, Align.RIGHT),
                                    new Column("settled", 10, Align.RIGHT),
                                    new Column("years left", 11, Align.RIGHT)),
                            rows)
                    .lines());
            for (TowerRef ref : history.moving()) {
                Optional<BigDecimal> years = history.yearsUntilOutOfPlumb(ref);
                if (years.isPresent() && years.get().compareTo(FIVE_YEARS) <= 0) {
                    console.say(ref + " is out of plumb inside five years at this rate");
                }
            }
            return Main.OK;
        }
    }
}
