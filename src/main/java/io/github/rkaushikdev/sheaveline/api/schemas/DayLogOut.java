package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.util.List;
import java.util.Optional;

/**
 * One machine on one day, with the answers already worked out.
 *
 * <p>Times go out as clock strings on purpose. The tablet in the terminal shows them straight
 * through without knowing anything about time zones, and this hill has never had a machine that ran
 * across one.
 *
 * @param lift the machine
 * @param day the day, as an ISO date
 * @param firstLoad when loading starts
 * @param lastLoad when the attendant stops loading
 * @param sweepBy when the line has to be clear
 * @param opened when it opened, once it has
 * @param closed when the line was swept, once it has been
 * @param sheetWalked whether the morning sheet is complete
 * @param mayOpen whether it may open
 * @param blockers what stands in the way of opening
 * @param entries the day book's lines, in order
 * @param meter the evening's meter reading, once it has been read
 * @param scheduled how long it is scheduled to run
 */
public record DayLogOut(
        @LiftCodeText LiftCode lift,
        String day,
        @ClockText TimeOfDay firstLoad,
        @ClockText TimeOfDay lastLoad,
        @ClockText TimeOfDay sweepBy,
        @ClockText Optional<TimeOfDay> opened,
        @ClockText Optional<TimeOfDay> closed,
        boolean sheetWalked,
        boolean mayOpen,
        List<String> blockers,
        List<EntryOut> entries,
        Optional<MeterOut> meter,
        @DurationText Duration scheduled) {

    /** A day's shape. */
    public DayLogOut {
        blockers = List.copyOf(blockers);
        entries = List.copyOf(entries);
    }

    /**
     * The day as the tablet shows it.
     *
     * @param log the day book
     * @return its shape
     */
    public static DayLogOut of(DayLog log) {
        return new DayLogOut(
                log.lift(),
                log.day().toString(),
                log.window().firstLoad(),
                log.window().lastLoad(),
                log.window().sweepBy(),
                log.opened(),
                log.closed(),
                log.sheet().map(PreOpSheet::isComplete).orElse(false),
                log.mayOpen(),
                log.blockers(),
                log.lines().stream().map(EntryOut::of).toList(),
                log.meter().map(MeterOut::of),
                log.scheduledTime());
    }
}
