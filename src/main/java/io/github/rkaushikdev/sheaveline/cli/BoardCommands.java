package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.board.Filing;
import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.persistence.IncidentRepository.Numbered;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Commands for the tramway board.
 *
 * <p>Utah's board wants to hear about some things immediately and about others in the monthly
 * return, and the deadline is the shorter of what the kind of incident asks for and what the injury
 * asks for. Nobody at the keyboard works that out. The incident does, and these commands print the
 * answer.
 *
 * <p>Filing takes the next number in the series and never reuses one. A gap in the series is the
 * sort of thing an auditor asks about for an afternoon, so the number comes off a counter row in the
 * same transaction as the filing.
 */
final class BoardCommands {

    private BoardCommands() {}

    /** The kinds of incident, by name. */
    static final class IncidentKinds extends Common.Choice<IncidentKind> {

        IncidentKinds() {
            super(named(IncidentKind.class));
        }
    }

    /** How badly somebody was hurt, by name. */
    static final class InjuryLevels extends Common.Choice<InjuryLevel> {

        InjuryLevels() {
            super(named(InjuryLevel.class));
        }
    }

    /** The kinds of filing, by name. */
    static final class FilingKinds extends Common.Choice<FilingKind> {

        FilingKinds() {
            super(named(FilingKind.class));
        }
    }

    /** Reportable incidents and what has been filed about them. */
    @Command(
            name = "board",
            description = "Reportable incidents and what has been filed about them.",
            subcommands = {RecordIncident.class, FileIncident.class, Unfiled.class, ShowLift.class, MonthlyReturn.class
            })
    static final class BoardGroup {}

    /** Write an incident into the register and print when it has to be filed. */
    @Command(name = "record", description = "Write an incident into the register and print when it has to be filed.")
    static final class RecordIncident implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "NARRATIVE")
        private String narrative;

        @Option(
                names = "--kind",
                required = true,
                converter = IncidentKinds.class,
                completionCandidates = IncidentKinds.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private IncidentKind kind;

        @Option(
                names = "--injury",
                defaultValue = "none",
                showDefaultValue = Visibility.ALWAYS,
                converter = InjuryLevels.class,
                completionCandidates = InjuryLevels.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private InjuryLevel injury;

        @Option(
                names = "--people",
                defaultValue = "1",
                showDefaultValue = Visibility.ALWAYS,
                description = "How many were involved.")
        private int people;

        @Option(names = "--by", required = true, description = "Who is reporting it.")
        private String by;

        @Mixin
        private Common.MomentOption at;

        @Override
        public int run(Console console) {
            Incident incident =
                    new Incident(LiftCode.parse(code), at.momentOf(console), kind, injury, people, narrative, by);
            int number;
            try (UnitOfWork work = console.work()) {
                if (!work.lifts().exists(incident.lift())) {
                    throw new StorageError("there is no lift " + incident.lift());
                }
                number = work.incidents().record(incident);
                work.commit();
            }
            console.say("incident " + number + "  " + incident);
            console.say("file by   " + Common.printed(incident.dueBy()));
            console.say("driven by " + incident.deadlineDriver());
            if (incident.holdsTheMachine()) {
                console.say(incident.lift() + " does not run again until the board says so");
            }
            return Main.OK;
        }
    }

    /** File one incident with the board and take the next number in the series. */
    @Command(name = "file", description = "File one incident with the board and take the next number in the series.")
    static final class FileIncident implements Action {

        @Parameters(paramLabel = "INCIDENT_ID")
        private int incidentId;

        @Option(
                names = "--kind",
                required = true,
                converter = FilingKinds.class,
                completionCandidates = FilingKinds.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private FilingKind kind;

        @Option(names = "--by", required = true, description = "Who is filing it.")
        private String by;

        @Mixin
        private Common.MomentOption at;

        @Override
        public int run(Console console) {
            LocalDateTime when = at.momentOf(console);
            Incident incident;
            Filing filing;
            try (UnitOfWork work = console.work()) {
                incident = work.incidents().get(incidentId).incident();
                filing = work.incidents().file(incidentId, kind, when, by);
                work.commit();
            }
            console.say(filing.toString());
            if (filing.wasLate(incident)) {
                console.say("late by   " + filing.lateness(incident));
            }
            if (filing.kind().needsAnAcknowledgement()) {
                console.say("the board acknowledges this one, so watch for it coming back");
            }
            return Main.OK;
        }
    }

    /** Everything on the register that has not gone to the board yet, worst first. */
    @Command(
            name = "unfiled",
            description = "Everything on the register that has not gone to the board yet, worst first.")
    static final class Unfiled implements Action {

        @Mixin
        private Common.MomentOption at;

        @Override
        public int run(Console console) {
            LocalDateTime now = at.momentOf(console);
            List<Numbered> waiting;
            try (UnitOfWork work = console.work()) {
                waiting = work.incidents().numberedUnfiled();
            }
            if (waiting.isEmpty()) {
                console.say("nothing is waiting to be filed");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            waiting.stream()
                    .sorted(Comparator.comparing(one -> one.incident().dueBy()))
                    .forEach(one -> rows.add(List.of(
                            one.id(),
                            one.incident().lift().toString(),
                            one.incident().kind().value(),
                            Common.printed(one.incident().at()),
                            Common.printed(one.incident().dueBy()),
                            one.incident().isOverdue(now) ? "LATE" : "waiting")));
            console.page(Table.of(
                            List.of(
                                    new Column("no", 4, Align.RIGHT),
                                    new Column("lift", 5),
                                    new Column("what", 20),
                                    new Column("happened", 17),
                                    new Column("file by", 17),
                                    new Column("state", 8)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }

    /** Every incident on one machine, filed or not. */
    @Command(name = "show", description = "Every incident on one machine, filed or not.")
    static final class ShowLift implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Override
        public int run(Console console) {
            LiftCode lift = LiftCode.parse(code);
            List<Numbered> found;
            try (UnitOfWork work = console.work()) {
                found = work.incidents().numberedForLift(lift);
            }
            if (found.isEmpty()) {
                console.say("nothing is on the register for " + lift);
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (Numbered one : found) {
                rows.add(List.of(
                        one.id(),
                        Common.printed(one.incident().at()),
                        one.incident().kind().value(),
                        one.incident().injury().value(),
                        one.filingNumber().map(FilingNumber::toString).orElse("not filed")));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("no", 4, Align.RIGHT),
                                    new Column("happened", 17),
                                    new Column("what", 20),
                                    new Column("injury", 12),
                                    new Column("filed as", 16)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }

    /** The monthly return, which is every incident that happened in that month. */
    @Command(name = "return", description = "The monthly return, which is every incident that happened in that month.")
    static final class MonthlyReturn implements Action {

        @Parameters(index = "0", paramLabel = "YEAR")
        private int year;

        @Parameters(index = "1", paramLabel = "MONTH")
        private int month;

        @Override
        public int run(Console console) {
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = LocalDate.of(year + (month == 12 ? 1 : 0), Math.floorMod(month, 12) + 1, 1);
            List<Incident> found;
            try (UnitOfWork work = console.work()) {
                found = work.incidents().between(start, end);
            }
            List<Incident> listed =
                    found.stream().filter(Incident::goesInTheMonthlyReturn).toList();
            console.say(String.format(Locale.ROOT, "monthly return %d-%02d", year, month));
            console.say("incidents " + found.size());
            console.say("returned  " + listed.size());
            if (listed.isEmpty()) {
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (Incident one : listed) {
                rows.add(List.of(
                        one.lift().toString(),
                        Common.printed(one.at()),
                        one.kind().value(),
                        one.injury().value(),
                        one.people()));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("lift", 5),
                                    new Column("happened", 17),
                                    new Column("what", 20),
                                    new Column("injury", 12),
                                    new Column("people", 7, Align.RIGHT)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }
}
