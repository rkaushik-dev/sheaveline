package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.LogEntry;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.ops.StopLog;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.Table;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Commands for a day on the hill.
 *
 * <p>This is the one part of the program a lift operator touches, and they touch it in the dark at
 * ten past seven wearing gloves, so every command here takes as few words as it can get away with
 * and prints back what it just wrote down.
 *
 * <p>Times are given as times, not timestamps. The operator types 11:30 because that is what the
 * clock on the wall says, and the day the command is working on supplies the date. Where a stop runs
 * past midnight the restart takes a flag as well, because that is the one case where the wall clock
 * is not enough.
 */
final class DayCommands {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private DayCommands() {}

    private static DayLog logOn(UnitOfWork work, String code, LocalDate when) {
        return work.dayLogs().get(LiftCode.parse(code), when);
    }

    /** A wall clock time on the day being worked on. */
    private static LocalDateTime moment(LocalDate when, String clock) {
        return when.atTime(TimeOfDay.parse(clock).toLocalTime());
    }

    /** A failed check comes with its reason, because the sheet says so. */
    private static Map<String, String> failures(List<String> given) {
        Map<String, String> failed = new LinkedHashMap<>();
        for (String one : given) {
            int colon = one.indexOf(':');
            String reason = colon < 0 ? "" : one.substring(colon + 1).strip();
            if (colon < 0 || reason.isEmpty()) {
                throw new ParseError(one, "a failed check written CODE:reason");
            }
            failed.put(one.substring(0, colon).strip().toUpperCase(Locale.ROOT), reason);
        }
        return failed;
    }

    /** The kinds of stop, by name. */
    static final class StopKinds extends Common.Choice<StopKind> {

        StopKinds() {
            super(named(StopKind.class));
        }
    }

    /** The day book: opening, stopping, restarting and closing. */
    @Command(
            name = "day",
            description = "The day book: opening, stopping, restarting and closing.",
            subcommands = {
                StartDay.class,
                AddNote.class,
                CallStop.class,
                RestartLift.class,
                ReadMeter.class,
                ShowDay.class,
                OpenStops.class
            })
    static final class DayGroup {}

    /** Open the book on a machine for the day and walk the pre op sheet. */
    @Command(name = "start", description = "Open the book on a machine for the day and walk the pre op sheet.")
    static final class StartDay implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(names = "--first-load", defaultValue = "09:00", showDefaultValue = Visibility.ALWAYS)
        private String firstLoad;

        @Option(names = "--last-load", defaultValue = "16:00", showDefaultValue = Visibility.ALWAYS)
        private String lastLoad;

        @Option(names = "--sweep-by", defaultValue = "16:30", showDefaultValue = Visibility.ALWAYS)
        private String sweepBy;

        @Option(names = "--mechanic", defaultValue = "", description = "Who walked the sheet.")
        private String mechanic;

        @Option(names = "--operator", defaultValue = "", description = "Who is running it.")
        private String operator;

        @Option(
                names = "--fail",
                paramLabel = "CODE:REASON",
                description = "A check that did not pass. The reason is not optional.")
        private List<String> fail = new ArrayList<>();

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            OperatingWindow window = OperatingWindow.parse(firstLoad, lastLoad, sweepBy);
            Lift lift;
            Optional<PreOpSheet> sheet = Optional.empty();
            DayLog log;
            try (UnitOfWork work = console.work()) {
                lift = work.lifts().get(LiftCode.parse(code));
                if (!mechanic.isEmpty() && !operator.isEmpty()) {
                    Map<String, String> failed = failures(fail);
                    List<CheckResult> results = new ArrayList<>();
                    for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
                        if (item.appliesTo(lift)) {
                            results.add(new CheckResult(
                                    item,
                                    failed.containsKey(item.code()) ? CheckOutcome.FAIL : CheckOutcome.PASS,
                                    mechanic,
                                    failed.getOrDefault(item.code(), "")));
                        }
                    }
                    sheet = Optional.of(new PreOpSheet(lift, when, results, mechanic, operator));
                }
                log = sheet.map(walked -> DayLog.start(lift.code(), when, window, walked))
                        .orElseGet(() -> DayLog.start(lift.code(), when, window));
                work.dayLogs().add(log);
                work.commit();
            }
            console.say(lift.code() + " " + when + " open, " + window.firstLoad() + " to " + window.sweepBy());
            if (sheet.isEmpty()) {
                console.say("no sheet walked yet, so it may not run");
            }
            for (String blocker : log.blockers()) {
                console.say("blocker  " + blocker);
            }
            return Main.OK;
        }
    }

    /** Write a line in the book. */
    @Command(name = "note", description = "Write a line in the book.")
    static final class AddNote implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "AT")
        private String at;

        @Parameters(index = "2", paramLabel = "TEXT")
        private String text;

        @Option(names = "--by", required = true, description = "Who wrote it.")
        private String by;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            try (UnitOfWork work = console.work()) {
                DayLog log = logOn(work, code, when);
                work.dayLogs().replace(log.note(TimeOfDay.parse(at), text, by));
                work.commit();
            }
            console.say(at + "  " + text);
            return Main.OK;
        }
    }

    /** Stop the machine and say why. */
    @Command(name = "stop", description = "Stop the machine and say why.")
    static final class CallStop implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "AT")
        private String at;

        @Option(
                names = "--kind",
                defaultValue = "operator",
                showDefaultValue = Visibility.ALWAYS,
                converter = StopKinds.class,
                completionCandidates = StopKinds.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private StopKind kind;

        @Option(names = "--by", required = true, description = "Who called it.")
        private String by;

        @Option(names = "--note", defaultValue = "", description = "Needed on anything reportable.")
        private String note;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            StopEvent event = new StopEvent(LiftCode.parse(code), kind, moment(when, at), by, note);
            try (UnitOfWork work = console.work()) {
                work.dayLogs().stop(event);
                work.commit();
            }
            console.say(event.toString());
            if (event.kind().alwaysNeedsALineCheck()) {
                console.say("the line is walked before this one runs again");
            }
            return Main.OK;
        }
    }

    /** Run it again, naming the stop by the time it was called. */
    @Command(name = "restart", description = "Run it again, naming the stop by the time it was called.")
    static final class RestartLift implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "STOPPED")
        private String stopped;

        @Parameters(index = "2", paramLabel = "AT")
        private String at;

        @Option(names = "--by", required = true, description = "Who restarted it.")
        private String by;

        @Option(names = "--line-checked", description = "The line has been walked.")
        private boolean lineChecked;

        @Mixin
        private Common.DayOption on;

        @Option(names = "--next-day", description = "The restart is the following morning.")
        private boolean nextDay;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            LocalDate back = when.plusDays(nextDay ? 1 : 0);
            StopEvent event;
            try (UnitOfWork work = console.work()) {
                event = work.dayLogs()
                        .restart(LiftCode.parse(code), moment(when, stopped), moment(back, at), by, lineChecked);
                work.commit();
            }
            console.say(event.lift() + " running again, down " + event.length());
            return Main.OK;
        }
    }

    /** Write down the hour meter and the cycle counter. */
    @Command(name = "meter", description = "Write down the hour meter and the cycle counter.")
    static final class ReadMeter implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "HOURS")
        private String hours;

        @Parameters(index = "2", paramLabel = "CYCLES")
        private long cycles;

        @Option(names = "--by", required = true, description = "Who read it.")
        private String by;

        @Option(names = "--replaced", description = "The meter itself was changed.")
        private boolean replaced;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            MeterReading reading =
                    new MeterReading(LiftCode.parse(code), on.dayOf(console), Decimals.of(hours), cycles, by, replaced);
            BigDecimal added;
            try (UnitOfWork work = console.work()) {
                added = work.dayLogs().readMeter(reading);
                work.commit();
            }
            console.say(reading + ", " + added + " hours since the last reading");
            return Main.OK;
        }
    }

    /** Print the book for one machine on one day. */
    @Command(name = "show", description = "Print the book for one machine on one day.")
    static final class ShowDay implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            DayLog log;
            StopLog stops;
            try (UnitOfWork work = console.work()) {
                log = logOn(work, code, when);
                stops = work.dayLogs().stopsOn(log.lift(), when);
            }
            console.say(log.toString());
            console.say("scheduled " + log.scheduledTime() + ", down " + stops.downtime());
            log.sheet().ifPresent(sheet -> console.say("sheet     " + sheet));
            for (String blocker : log.blockers()) {
                console.say("blocker   " + blocker);
            }
            if (!log.entries().isEmpty()) {
                List<List<Object>> rows = new ArrayList<>();
                for (LogEntry one : log.lines()) {
                    rows.add(List.of(CLOCK.format(one.at()), one.kind().value(), one.text(), one.by()));
                }
                console.say();
                console.page(Table.of(
                                List.of(
                                        new Column("time", 6),
                                        new Column("kind", 10),
                                        new Column("entry", 44),
                                        new Column("by", 14)),
                                rows)
                        .lines());
            }
            if (stops.size() > 0) {
                List<List<Object>> rows = new ArrayList<>();
                for (StopEvent one : stops.stops()) {
                    rows.add(List.of(
                            CLOCK.format(one.stopped()),
                            one.kind().value(),
                            one.isOpen() ? "open" : one.length().toString(),
                            one.stoppedBy()));
                }
                console.say();
                console.page(Table.of(
                                List.of(
                                        new Column("stopped", 8),
                                        new Column("kind", 16),
                                        new Column("down", 10, Align.RIGHT),
                                        new Column("by", 14)),
                                rows)
                        .lines());
            }
            return Main.OK;
        }
    }

    /** Anything on the hill that is stopped and has not run again. */
    @Command(name = "open-stops", description = "Anything on the hill that is stopped and has not run again.")
    static final class OpenStops implements Action {

        @Option(names = "--lift", paramLabel = "CODE", description = "Just this machine.")
        private String code;

        @Override
        public int run(Console console) {
            LocalDateTime now = console.now();
            List<StopEvent> stops;
            try (UnitOfWork work = console.work()) {
                stops = code == null
                        ? work.dayLogs().openStops()
                        : work.dayLogs().openStops(LiftCode.parse(code));
            }
            if (stops.isEmpty()) {
                console.say("nothing is stopped");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (StopEvent one : stops) {
                rows.add(List.of(
                        one.lift().toString(),
                        Common.printed(one.stopped()),
                        one.kind().value(),
                        one.length(now).toString(),
                        one.stoppedBy()));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("lift", 5),
                                    new Column("stopped", 17),
                                    new Column("kind", 16),
                                    new Column("down", 10, Align.RIGHT),
                                    new Column("by", 14)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }
}
