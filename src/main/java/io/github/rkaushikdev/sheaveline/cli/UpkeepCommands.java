package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.Gather;
import io.github.rkaushikdev.sheaveline.reports.Table;
import io.github.rkaushikdev.sheaveline.upkeep.Defect;
import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import io.github.rkaushikdev.sheaveline.upkeep.DueItem;
import io.github.rkaushikdev.sheaveline.upkeep.InspectionRecord;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceAssessment;
import io.github.rkaushikdev.sheaveline.upkeep.UpkeepSchedule;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Commands about the ladder: what is due, what was found, what is being done.
 *
 * <p>The department's word for the schedule of required inspections is the ladder, and the point of
 * these commands is that a mechanic can answer three questions without opening the binder. What is
 * due on this machine. What did the last walk turn up that has not been dealt with. What is on the
 * board for me today.
 *
 * <p>Raising a work order takes a defect and not a priority. The priority falls out of how bad the
 * defect is, and a mechanic who could type the priority in would eventually type the convenient
 * one.
 */
final class UpkeepCommands {

    private UpkeepCommands() {}

    /**
     * Read where:level:what, which is the order it is written on the walk sheet. Everything after the
     * second colon is the description, colons and all.
     *
     * @param text the defect as typed
     * @param raised the day it was found
     * @return the defect
     * @throws ParseError when it has fewer than three parts or the level is not one there is
     */
    static Defect parseDefect(String text, LocalDate raised) {
        String[] parts = text.split(":", 3);
        if (parts.length != 3) {
            throw new ParseError(text, "a defect written where:level:what");
        }
        String level = parts[1];
        DefectLevel found = Valued.lookup(DefectLevel.class, level.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ParseError(
                        level,
                        "a defect level, one of "
                                + Arrays.stream(DefectLevel.values())
                                        .map(DefectLevel::value)
                                        .collect(Collectors.joining(", "))));
        return new Defect(parts[0].strip(), found, parts[2].strip(), raised);
    }

    /** The work order priorities, by name. */
    static final class Priorities extends Common.Choice<Priority> {

        Priorities() {
            super(named(Priority.class));
        }
    }

    /** The inspection ladder, its findings and the work that comes off it. */
    @Command(
            name = "upkeep",
            description = "The inspection ladder, its findings and the work that comes off it.",
            subcommands = {
                Due.class,
                RecordJob.class,
                OpenDefects.class,
                RaiseOrder.class,
                OrderBoard.class,
                CloseOrder.class
            })
    static final class UpkeepGroup {}

    /** What the ladder wants on one machine, soonest first. */
    @Command(name = "due", description = "What the ladder wants on one machine, soonest first.")
    static final class Due implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(
                names = "--within",
                defaultValue = "30",
                showDefaultValue = Visibility.ALWAYS,
                description = "Days ahead to look.")
        private int within;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            Lift lift;
            ServiceAssessment reading;
            try (UnitOfWork work = console.work()) {
                lift = work.lifts().get(LiftCode.parse(code));
                reading = Gather.assess(work, lift, when);
            }
            UpkeepSchedule schedule = reading.schedule();
            List<DueItem> overdue = schedule.overdue(when);
            List<DueItem> soon = schedule.dueWithin(when, within).stream()
                    .filter(one -> !overdue.contains(one))
                    .toList();
            console.say(lift.code() + " " + lift.name() + ", ladder read on " + when);
            console.say("status    " + reading.status().value());
            if (overdue.isEmpty() && soon.isEmpty()) {
                console.say("nothing due");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            List<DueItem> listed = new ArrayList<>(overdue);
            listed.addAll(soon);
            for (DueItem one : listed) {
                rows.add(List.of(
                        one.job().code(),
                        one.job().description(),
                        Common.printed(one.dueOn(), "never"),
                        one.daysUntil(when).map(Object::toString).orElse(""),
                        overdue.contains(one) ? "overdue" : "due"));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("job", 5),
                                    new Column("what", 38),
                                    new Column("when", 12),
                                    new Column("days", 6, Align.RIGHT),
                                    new Column("state", 9)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }

    /** Sign off one job on the ladder and write down what it turned up. */
    @Command(name = "record", description = "Sign off one job on the ladder and write down what it turned up.")
    static final class RecordJob implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "JOB")
        private String job;

        @Option(names = "--by", required = true, description = "Who did it.")
        private String by;

        @Option(names = "--hours", description = "The hour meter at the time.")
        private String hours;

        @Option(names = "--cycles", description = "The cycle counter at the time.")
        private Long cycles;

        @Option(names = "--defect", paramLabel = "WHERE:LEVEL:WHAT")
        private List<String> defect = new ArrayList<>();

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            List<Defect> found =
                    defect.stream().map(one -> parseDefect(one, when)).toList();
            InspectionRecord record = InspectionRecord.of(
                    LiftCode.parse(code),
                    job,
                    when,
                    by,
                    Optional.ofNullable(hours).map(Decimals::of),
                    Optional.ofNullable(cycles),
                    found);
            try (UnitOfWork work = console.work()) {
                work.inspections().add(record);
                work.commit();
            }
            console.say(record.lift() + " " + record.code() + " done " + when + " by " + by);
            for (Defect one : found) {
                console.say(
                        String.format(Locale.ROOT, "  %-12s %s  %s", one.level().value(), one.where(), one.detail()));
                if (one.level().needsAWorkOrder()) {
                    console.say("    this one wants a work order raising");
                }
            }
            return Main.OK;
        }
    }

    /** Everything found and not yet cleared. */
    @Command(name = "defects", description = "Everything found and not yet cleared.")
    static final class OpenDefects implements Action {

        @Option(names = "--lift", paramLabel = "CODE", description = "Just this machine.")
        private String code;

        @Override
        public int run(Console console) {
            List<Defect> found;
            try (UnitOfWork work = console.work()) {
                found = code == null
                        ? work.inspections().openDefects()
                        : work.inspections().openDefects(LiftCode.parse(code));
            }
            if (found.isEmpty()) {
                console.say("nothing open");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (Defect one : found) {
                rows.add(List.of(
                        one.where(),
                        one.level().value(),
                        one.detail(),
                        one.raised().toString()));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("where", 14),
                                    new Column("level", 12),
                                    new Column("what", 34),
                                    new Column("raised", 12)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }

    /** Raise a work order against an open defect. */
    @Command(
            name = "raise",
            description = {
                "Raise a work order against an open defect.",
                "",
                "The order is dated from the defect rather than from today, because the clock the priority is"
                        + " measured against started when somebody found the thing."
            })
    static final class RaiseOrder implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "WHERE")
        private String where;

        @Option(names = "--by", required = true, description = "Who is raising it.")
        private String by;

        @Override
        public int run(Console console) {
            LiftCode lift = LiftCode.parse(code);
            WorkOrder order;
            try (UnitOfWork work = console.work()) {
                Defect defect = work.inspections().openDefects(lift).stream()
                        .filter(one -> one.where().equals(where))
                        .findFirst()
                        .orElseThrow(() -> new StorageError(lift + " has no open defect at " + where));
                order = WorkOrder.fromDefect(work.workOrders().nextNumber(), lift, defect, by);
                work.workOrders().add(order);
                work.commit();
            }
            console.say(order.toString());
            console.say("due by    " + order.dueBy());
            if (order.priority().stopsTheMachine()) {
                console.say(lift + " does not carry the public until this is closed");
            }
            return Main.OK;
        }
    }

    /** The work orders that are still open, worst first. */
    @Command(name = "board", description = "The work orders that are still open, worst first.")
    static final class OrderBoard implements Action {

        @Option(names = "--lift", paramLabel = "CODE", description = "Just this machine.")
        private String code;

        @Option(
                names = "--priority",
                converter = Priorities.class,
                completionCandidates = Priorities.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private Priority priority;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            List<WorkOrder> orders;
            try (UnitOfWork work = console.work()) {
                if (priority != null) {
                    orders = work.workOrders().ofPriority(priority);
                } else if (code != null) {
                    orders = work.workOrders().openOrders(LiftCode.parse(code));
                } else {
                    orders = work.workOrders().openOrders();
                }
            }
            List<WorkOrder> open = orders.stream()
                    .filter(WorkOrder::isOpen)
                    .sorted(Comparator.comparing(WorkOrder::dueBy).thenComparingInt(WorkOrder::number))
                    .toList();
            if (open.isEmpty()) {
                console.say("the board is clear");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (WorkOrder one : open) {
                rows.add(List.of(
                        one.number(),
                        one.lift().toString(),
                        one.detail(),
                        one.priority().value(),
                        one.dueBy().toString(),
                        one.isOverdue(when) ? "overdue" : "open"));
            }
            console.page(Table.of(
                            List.of(
                                    new Column("no", 5, Align.RIGHT),
                                    new Column("lift", 5),
                                    new Column("what", 32),
                                    new Column("priority", 10),
                                    new Column("due", 12),
                                    new Column("state", 8)),
                            rows)
                    .lines());
            return Main.OK;
        }
    }

    /** Close a work order. */
    @Command(name = "close", description = "Close a work order.")
    static final class CloseOrder implements Action {

        @Parameters(paramLabel = "NUMBER")
        private int number;

        @Option(names = "--by", required = true, description = "Who finished it.")
        private String by;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            WorkOrder closed;
            try (UnitOfWork work = console.work()) {
                closed = work.workOrders().close(number, when, by);
                work.commit();
            }
            console.say(closed.toString());
            return Main.OK;
        }
    }
}
