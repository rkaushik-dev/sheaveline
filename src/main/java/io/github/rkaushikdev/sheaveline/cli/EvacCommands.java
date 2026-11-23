package io.github.rkaushikdev.sheaveline.cli;

import static io.github.rkaushikdev.sheaveline.cli.Common.wholeFeet;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.capacity.LineSpacing;
import io.github.rkaushikdev.sheaveline.evac.Beat;
import io.github.rkaushikdev.sheaveline.evac.Drill;
import io.github.rkaushikdev.sheaveline.evac.DrillKind;
import io.github.rkaushikdev.sheaveline.evac.DrillRegister;
import io.github.rkaushikdev.sheaveline.evac.EvacuationProfile;
import io.github.rkaushikdev.sheaveline.evac.EvacuationTiming;
import io.github.rkaushikdev.sheaveline.evac.RescueKit;
import io.github.rkaushikdev.sheaveline.evac.RescueTeam;
import io.github.rkaushikdev.sheaveline.evac.Staging;
import io.github.rkaushikdev.sheaveline.evac.StagingPlan;
import io.github.rkaushikdev.sheaveline.evac.TeamPool;
import io.github.rkaushikdev.sheaveline.line.Catenary;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.Table;
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
 * Commands about getting people off the line.
 *
 * <p>The drill command is the one with teeth. A machine whose annual full line drill has run out
 * does not carry the public, and the register is the only thing that says whether it has. So the
 * check exits badly when a machine is out of date, because it runs on a timer in November and
 * somebody needs to be told.
 *
 * <p>The staging command is the planning one. It takes the teams that are on the hill and works out
 * where to park them so the busiest one finishes soonest, which is what actually sets the clearing
 * time, and prints the answer as a beat sheet somebody can carry.
 */
final class EvacCommands {

    private EvacCommands() {}

    private static SagModel sag(Lift lift, String tension, String ice) {
        LoadPerFoot weight = Catenary.loadedLine(lift, LineSpacing.of(lift).spacing(), Length.parse(ice));
        return new SagModel(weight, Load.parse(tension));
    }

    /** A team off the truck, written name:ladder:rope in feet; a dash or nothing for no ladder or rope. */
    private static RescueTeam team(String given, LocalDate on) {
        String[] parts = given.split(":", -1);
        if (parts.length != 3) {
            throw new ParseError(given, "a team written name:ladder:rope");
        }
        String name = parts[0].strip();
        return RescueTeam.of(
                name,
                List.of(name + " lead", name + " two", name + " three"),
                new RescueKit(reach(parts[1]), reach(parts[2]), 2, 3, 1),
                new Length(0),
                LocalDate.of(on.getYear() + 1, on.getMonth(), on.getDayOfMonth()));
    }

    private static Optional<Length> reach(String feet) {
        String given = feet.strip();
        return given.isEmpty() || given.equals("-") ? Optional.empty() : Optional.of(Length.parse(feet + " ft"));
    }

    /** The kinds of drill, by name. */
    static final class DrillKinds extends Common.Choice<DrillKind> {

        DrillKinds() {
            super(named(DrillKind.class));
        }
    }

    /** Drills, staging and whether a machine may still carry the public. */
    @Command(
            name = "evac",
            description = "Drills, staging and whether a machine may still carry the public.",
            subcommands = {RecordDrill.class, CheckDrills.class, PlanStaging.class})
    static final class EvacGroup {}

    /** Write a drill into the register. */
    @Command(name = "drill", description = "Write a drill into the register.")
    @Common.DayHelp("The day it was run.")
    static final class RecordDrill implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(
                names = "--kind",
                defaultValue = "full_line",
                showDefaultValue = Visibility.ALWAYS,
                converter = DrillKinds.class,
                completionCandidates = DrillKinds.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private DrillKind kind;

        @Option(names = "--team", required = true, description = "Give it once a team.")
        private List<String> teams;

        @Option(names = "--carriers", required = true, description = "How many were actually worked.")
        private int carriers;

        @Option(names = "--minutes", required = true, description = "How long it took.")
        private int minutes;

        @Option(names = "--observer", required = true, description = "Who watched and signed it.")
        private String observer;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            Drill drill = Drill.of(
                    LiftCode.parse(code),
                    on.dayOf(console),
                    kind,
                    teams,
                    carriers,
                    Duration.ofMinutes(minutes),
                    observer);
            try (UnitOfWork work = console.work()) {
                work.drills().add(drill);
                work.commit();
            }
            console.say(drill.toString());
            if (drill.countsForTheAnnual()) {
                console.say("the annual now runs to " + drill.expires());
            } else {
                console.say("this one does not satisfy the annual");
            }
            return Main.OK;
        }
    }

    /** Whether every machine's annual drill is still current. */
    @Command(name = "check", description = "Whether every machine's annual drill is still current.")
    @Common.DayHelp("The day to read the register against.")
    static final class CheckDrills implements Action {

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            DrillRegister held;
            List<LiftCode> codes;
            try (UnitOfWork work = console.work()) {
                held = work.drills().register();
                codes = work.lifts().codes();
            }
            List<List<Object>> rows = new ArrayList<>();
            List<LiftCode> stale = new ArrayList<>();
            for (LiftCode code : codes) {
                boolean current = held.liftIsCurrent(code, when);
                if (!current) {
                    stale.add(code);
                }
                rows.add(List.of(
                        code.toString(),
                        held.lastFull(code).map(last -> last.on().toString()).orElse("never"),
                        Common.printed(held.liftExpires(code), "never"),
                        current ? "current" : "OUT OF DATE"));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("lift", 5),
                                    new Column("last full drill", 16),
                                    new Column("runs out", 12),
                                    new Column("state", 12)),
                            rows)
                    .lines());
            if (stale.isEmpty()) {
                return Main.OK;
            }
            console.say();
            for (LiftCode code : stale) {
                console.say(code + " does not carry the public until it has been drilled");
            }
            return Main.REFUSED;
        }
    }

    /** Where to park the teams so the busiest one finishes soonest. */
    @Command(
            name = "staging",
            description = {
                "Where to park the teams so the busiest one finishes soonest.",
                "",
                "The teams are given as name:ladder:rope, in feet, because that is what is on the truck. A team"
                        + " with no rope gets a dash."
            })
    @Common.DayHelp("The day to read the training currency against.")
    static final class PlanStaging implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(names = "--team", required = true, paramLabel = "NAME:LADDER:ROPE")
        private List<String> teams;

        @Option(
                names = "--seats",
                defaultValue = "3",
                showDefaultValue = Visibility.ALWAYS,
                description = "How full the carriers are.")
        private int seats;

        @Option(names = "--snow", defaultValue = "2 ft", showDefaultValue = Visibility.ALWAYS)
        private String snow;

        @Option(names = "--tension", defaultValue = "34500 lb", showDefaultValue = Visibility.ALWAYS)
        private String tension;

        @Option(
                names = "--ice",
                defaultValue = "0 in",
                showDefaultValue = Visibility.ALWAYS,
                description = "Ice on the rope, measured out from its surface. It lowers every carrier.")
        private String ice;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            Lift lift;
            try (UnitOfWork work = console.work()) {
                lift = Common.machine(work, code);
            }
            EvacuationProfile profile = new EvacuationProfile(lift, sag(lift, tension, ice), Length.parse(snow));
            TeamPool pool =
                    TeamPool.of(teams.stream().map(one -> team(one, when)).toList());
            StagingPlan plan = Staging.stage(profile, pool, new Length(0), when, seats);
            console.say(plan.toString());
            console.say("teams walking at "
                    + EvacuationTiming.travelThrough(profile.snow()).formatFpm() + " through "
                    + wholeFeet(profile.snow()) + " ft of snow");
            if (plan.beats().isEmpty()) {
                plan.refusals().forEach(console::say);
                return Main.REFUSED;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (Beat one : plan.beats()) {
                rows.add(List.of(
                        one.team().name(),
                        wholeFeet(one.stagedAt()) + " ft",
                        one.stops().size(),
                        wholeFeet(one.walking()) + " ft",
                        one.work().formatClock()));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("team", 12),
                                    new Column("staged at", 12, Align.RIGHT),
                                    new Column("carriers", 9, Align.RIGHT),
                                    new Column("walking", 12, Align.RIGHT),
                                    new Column("busy for", 12, Align.RIGHT)),
                            rows)
                    .lines());
            plan.refusals().forEach(console::say);
            return plan.isWorkable() ? Main.OK : Main.REFUSED;
        }
    }
}
