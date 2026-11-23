package io.github.rkaushikdev.sheaveline.cli;

import static io.github.rkaushikdev.sheaveline.cli.Common.wholeFeet;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.RopeFilePage;
import io.github.rkaushikdev.sheaveline.reports.Table;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.Finding;
import io.github.rkaushikdev.sheaveline.ropework.Retirement;
import io.github.rkaushikdev.sheaveline.ropework.Trend;
import io.github.rkaushikdev.sheaveline.ropework.Verdict;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Commands about the ropes.
 *
 * <p>The walk command is the one that matters. A mechanic comes in off the hill with a page of
 * caliper readings and types them in, and the readings are given as station, crowns, valleys
 * separated by colons because that is the order they are written on the clipboard, and asking
 * somebody to reorder them at the keyboard is how a reading ends up in the wrong column.
 */
final class RopeCommands {

    private RopeCommands() {}

    /**
     * Read station:crowns:valleys, the order it is written on the clipboard. Each part carries its
     * own unit.
     *
     * @param text the reading as typed
     * @return the reading
     * @throws ParseError when it does not have three parts or a part does not read
     */
    static DiameterReading parseReading(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length != 3) {
            throw new ParseError(text, "a reading written station:crowns:valleys");
        }
        return new DiameterReading(Length.parse(parts[0]), Length.parse(parts[1]), Length.parse(parts[2]));
    }

    /** The haul ropes and what has been done to them. */
    @Command(
            name = "rope",
            description = "The haul ropes and what has been done to them.",
            subcommands = {ListRopes.class, PrintFile.class, WalkRope.class, ListWalks.class, WearForecast.class})
    static final class RopeGroup {}

    /** Every rope on file. */
    @Command(name = "list", description = "Every rope on file.")
    static final class ListRopes implements Action {

        @Option(names = "--spares", description = "Only the ropes on no machine.")
        private boolean spares;

        @Override
        public int run(Console console) {
            List<List<Object>> rows = new ArrayList<>();
            try (UnitOfWork work = console.work()) {
                List<HaulRope> found =
                        spares ? work.ropes().spares() : work.ropes().all();
                for (HaulRope one : found) {
                    rows.add(List.of(
                            one.serial().toString(),
                            one.spec().construction().value(),
                            one.diameter().formatInches(3),
                            wholeFeet(one.length()),
                            one.installed().toString(),
                            one.seasonsInService(console.today())));
                }
            }
            Table table = Table.of(
                    List.of(
                            new Column("serial", 16),
                            new Column("construction", 21),
                            new Column("diameter", 9, Align.RIGHT),
                            new Column("length", 7, Align.RIGHT),
                            new Column("installed", 11),
                            new Column("seasons", 7, Align.RIGHT)),
                    rows);
            console.page(table.lines());
            return Main.OK;
        }
    }

    /** The rope file, printed the way it goes in the folder. */
    @Command(name = "file", description = "The rope file, printed the way it goes in the folder.")
    static final class PrintFile implements Action {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Override
        public int run(Console console) {
            RopeFilePage page;
            try (UnitOfWork work = console.work()) {
                HaulRope held = Common.haulRope(work, serial);
                String machine = "in the shop";
                for (Lift one : work.lifts().all()) {
                    if (one.rope().serial().equals(held.serial())) {
                        machine = one.code() + " " + one.name();
                    }
                }
                page = RopeFilePage.of(
                        held,
                        console.today(),
                        machine,
                        work.wearSurveys().forRope(held),
                        List.of(),
                        List.of(),
                        Optional.empty());
            }
            console.page(page.render().lines());
            return Main.OK;
        }
    }

    /** Record a caliper walk and print what it says about the rope. */
    @Command(name = "walk", description = "Record a caliper walk and print what it says about the rope.")
    static final class WalkRope implements Action {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--taken", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate taken;

        @Option(names = "--inspector", required = true)
        private String inspector;

        @Option(
                names = "--reading",
                required = true,
                paramLabel = "STATION:CROWNS:VALLEYS",
                description = "One caliper reading. Give it once a station.")
        private List<String> readings;

        @Override
        public int run(Console console) {
            HaulRope held;
            WearSurvey survey;
            try (UnitOfWork work = console.work()) {
                held = Common.haulRope(work, serial);
                survey = WearSurvey.of(
                        held,
                        taken,
                        inspector,
                        readings.stream().map(RopeCommands::parseReading).toList());
                work.wearSurveys().add(survey);
                work.commit();
            }
            DiameterReading smallest = survey.smallest();
            console.say(survey.diameters().size() + " readings recorded against " + held.serial());
            console.say("smallest " + smallest.mean().formatInches(3)
                    + " at " + wholeFeet(smallest.station()) + " ft, "
                    + Decimals.fixed(survey.smallestPercent(), 1) + "% of nominal");
            List<Finding> found = Verdict.wearFindings(survey);
            console.say("verdict " + Verdict.worstOf(found).value());
            found.forEach(one -> console.say("  " + one.verdict().value() + " " + one));
            return Main.OK;
        }
    }

    /** Every caliper walk on one rope. */
    @Command(name = "walks", description = "Every caliper walk on one rope.")
    static final class ListWalks implements Action {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Override
        public int run(Console console) {
            List<List<Object>> rows = new ArrayList<>();
            try (UnitOfWork work = console.work()) {
                HaulRope held = Common.haulRope(work, serial);
                for (WearSurvey one : work.wearSurveys().forRope(held)) {
                    rows.add(List.of(
                            one.taken().toString(),
                            one.inspector(),
                            one.smallest().mean().formatInches(3),
                            wholeFeet(one.smallest().station()),
                            Verdict.assessWear(one).value()));
                }
            }
            Table table = Table.of(
                    List.of(
                            new Column("taken", 11),
                            new Column("inspector", 20),
                            new Column("smallest", 10, Align.RIGHT),
                            new Column("station", 8, Align.RIGHT),
                            new Column("verdict", 12)),
                    rows);
            console.page(table.lines());
            return Main.OK;
        }
    }

    /** When a rope reaches the condemning diameter, and when to order the next. */
    @Command(
            name = "forecast",
            description = {
                "When a rope reaches the condemning diameter, and when to order the next.",
                "",
                "With no serial it walks every rope on file and prints only the ones whose replacement should"
                        + " already be on order, which is the version the office runs in September."
            })
    static final class WearForecast implements Action {

        @Parameters(arity = "0..1", paramLabel = "SERIAL")
        private String serial;

        @Option(
                names = "--lead-time",
                defaultValue = Retirement.LEAD_TIME_DAYS + "",
                showDefaultValue = Visibility.ALWAYS,
                description = "Weeks the mill takes, in days.")
        private int leadTime;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            List<Retirement> found;
            try (UnitOfWork work = console.work()) {
                if (serial != null) {
                    HaulRope wanted = work.ropes().get(RopeSerial.parse(serial));
                    found = List.of(Trend.forecast(wanted, work.wearSurveys().forRope(wanted), when, leadTime));
                } else {
                    List<Trend.RopeWalks> walked = new ArrayList<>();
                    for (HaulRope one : work.ropes().all()) {
                        walked.add(new Trend.RopeWalks(one, work.wearSurveys().forRope(one)));
                    }
                    found = Trend.orderable(walked, when, leadTime);
                }
            }
            if (found.isEmpty()) {
                console.say("nothing on the hill wants ordering yet");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (Retirement one : found) {
                rows.add(List.of(
                        one.trend().rope().serial().toString(),
                        one.trend().last().smallest().formatInches(3),
                        one.trend().left().formatInches(3),
                        Common.printed(one.comesOff(), "not wearing"),
                        Common.printed(one.orderBy()),
                        one.confidence()));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("serial", 16),
                                    new Column("now", 8, Align.RIGHT),
                                    new Column("left", 8, Align.RIGHT),
                                    new Column("comes off", 12),
                                    new Column("order by", 12),
                                    new Column("read from", 12)),
                            rows)
                    .lines());
            for (Retirement one : found) {
                if (one.isOverdueToOrder()) {
                    console.say(one.trend().rope().serial() + " should already be on order");
                }
            }
            return Main.OK;
        }
    }
}
