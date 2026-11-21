package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import io.github.rkaushikdev.sheaveline.weather.HoldRegister;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The day book: one machine, one day, and everything that happened on it. It is the one document an
 * inspector always asks for.
 *
 * <p>Everything else is a record of one thing. This is the only place the pieces meet: the morning
 * walk, the time the first chair went out, every stop, every hold, the meter reading at the end and
 * whatever the operators thought was worth writing down.
 *
 * <p>Opening is the interesting part. A lift opens when four separate things are all true at once,
 * and the reason to put that in one place is that three of them are usually fine and the fourth is
 * the one that changes.
 *
 * <p>The book never changes in place: opening, closing, a note, a stop log or a meter reading each
 * give a new book with that one thing added.
 *
 * @param lift the machine
 * @param day the day
 * @param window when it was meant to run
 * @param sheet the morning walk, once it is done
 * @param holds the holds the book is read against
 * @param stops the day's stops, once a log is attached
 * @param entries the lines in the book, in the order they were added
 * @param opened when the first chair went out
 * @param closed when the line was swept
 * @param meter the meter reading at the end of the day
 */
public record DayLog(
        LiftCode lift,
        LocalDate day,
        OperatingWindow window,
        Optional<PreOpSheet> sheet,
        HoldRegister holds,
        Optional<StopLog> stops,
        List<LogEntry> entries,
        Optional<TimeOfDay> opened,
        Optional<TimeOfDay> closed,
        Optional<MeterReading> meter) {

    /**
     * A book whose parts are all for this machine and whose day runs forwards.
     *
     * @throws OperationsError when the sheet or the stop log is for another machine, or the machine
     *     closes without opening or before it opened
     */
    public DayLog {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(holds, "holds");
        Objects.requireNonNull(stops, "stops");
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(closed, "closed");
        Objects.requireNonNull(meter, "meter");
        entries = List.copyOf(entries);
        if (sheet.isPresent() && !sheet.get().lift().code().equals(lift)) {
            throw new OperationsError("that sheet is for another machine");
        }
        if (stops.isPresent() && !stops.get().lift().equals(lift)) {
            throw new OperationsError("that stop log is for another machine");
        }
        if (closed.isPresent() && opened.isEmpty()) {
            throw new OperationsError("a machine that never opened cannot close");
        }
        if (opened.isPresent() && closed.isPresent() && closed.get().compareTo(opened.get()) < 0) {
            throw new OperationsError("a machine cannot close before it opens");
        }
    }

    /**
     * A fresh book with nothing in it yet.
     *
     * @param lift the machine
     * @param day the day
     * @param window when it is meant to run
     * @return the book
     */
    public static DayLog start(LiftCode lift, LocalDate day, OperatingWindow window) {
        return start(lift, day, window, Optional.empty(), HoldRegister.of(), List.of());
    }

    /**
     * A fresh book with the morning walk in it.
     *
     * @param lift the machine
     * @param day the day
     * @param window when it is meant to run
     * @param sheet the morning walk
     * @return the book
     */
    public static DayLog start(LiftCode lift, LocalDate day, OperatingWindow window, PreOpSheet sheet) {
        return start(lift, day, window, Optional.of(sheet), HoldRegister.of(), List.of());
    }

    /**
     * A fresh book.
     *
     * @param lift the machine
     * @param day the day
     * @param window when it is meant to run
     * @param sheet the morning walk, if it is done
     * @param holds the holds to read the book against
     * @param entries any lines already written
     * @return the book, not yet opened and with no stop log attached
     */
    public static DayLog start(
            LiftCode lift,
            LocalDate day,
            OperatingWindow window,
            Optional<PreOpSheet> sheet,
            HoldRegister holds,
            List<LogEntry> entries) {
        return new DayLog(
                lift,
                day,
                window,
                sheet,
                holds,
                Optional.empty(),
                entries,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * The day's stops.
     *
     * @return the attached log, or an empty one for this machine
     */
    public StopLog stopLog() {
        return stops.orElseGet(() -> StopLog.of(lift));
    }

    /**
     * Everything standing between this machine and the first chair.
     *
     * @return {@code no pre opening sheet} or the sheet's own blockers, then {@code gust hold open} for
     *     each open hold on this machine, then {@code safety circuit not cleared} for each open stop
     */
    public List<String> blockers() {
        List<String> reasons = new ArrayList<>();
        if (sheet.isEmpty()) {
            reasons.add("no pre opening sheet");
        } else {
            reasons.addAll(sheet.get().blockers());
        }
        for (HoldRecord hold : holds.openHolds(lift)) {
            reasons.add(hold.reason().value() + " hold open");
        }
        for (StopEvent stop : stopLog().openStops()) {
            reasons.add(stop.kind().value() + " not cleared");
        }
        return List.copyOf(reasons);
    }

    /**
     * Whether the machine may open.
     *
     * @return true when nothing stands in the way
     */
    public boolean mayOpen() {
        return blockers().isEmpty();
    }

    /**
     * Whether the machine is running.
     *
     * @return true once it has opened and until it closes
     */
    public boolean isOpen() {
        return opened.isPresent() && closed.isEmpty();
    }

    /**
     * Open the machine, which writes the opening line.
     *
     * @param at when the first chair went out
     * @param by who opened it
     * @return the opened book; this one is unchanged
     * @throws OperationsError when it already opened, or something stands in the way
     */
    public DayLog openAt(TimeOfDay at, String by) {
        if (opened.isPresent()) {
            throw new OperationsError(lift + " already opened at " + opened.get());
        }
        if (!mayOpen()) {
            throw new OperationsError(
                    lift + " is not clear to open: " + blockers().get(0));
        }
        return withEntry(line(at, EntryKind.OPENED, "first load at " + at, by)).withOpened(at);
    }

    /**
     * Close the machine, which writes the closing line.
     *
     * @param at when the line was swept
     * @param by who closed it
     * @return the closed book; this one is unchanged
     * @throws OperationsError when it never opened, or already closed
     */
    public DayLog closeAt(TimeOfDay at, String by) {
        if (opened.isEmpty()) {
            throw new OperationsError(lift + " never opened");
        }
        if (closed.isPresent()) {
            throw new OperationsError(lift + " already closed at " + closed.get());
        }
        return withEntry(line(at, EntryKind.CLOSED, "swept at " + at, by)).withClosed(at);
    }

    /**
     * Write a note in the book.
     *
     * @param at when
     * @param text what it says
     * @param by who signed it
     * @return the book with the note in it; this one is unchanged
     * @throws OperationsError when the text or the signature is blank
     */
    public DayLog note(TimeOfDay at, String text, String by) {
        return withEntry(line(at, EntryKind.NOTE, text, by));
    }

    /**
     * The same book with one more line in it.
     *
     * @param entry the line
     * @return a new book; this one is unchanged
     */
    public DayLog withEntry(LogEntry entry) {
        List<LogEntry> all = new ArrayList<>(entries);
        all.add(entry);
        return new DayLog(lift, day, window, sheet, holds, stops, all, opened, closed, meter);
    }

    /**
     * The same book with the day's stops attached.
     *
     * @param changed the stop log
     * @return a new book; this one is unchanged
     * @throws OperationsError when the log is for another machine
     */
    public DayLog withStops(StopLog changed) {
        return new DayLog(lift, day, window, sheet, holds, Optional.of(changed), entries, opened, closed, meter);
    }

    /**
     * The same book with the end of day meter reading.
     *
     * @param reading the reading
     * @return a new book; this one is unchanged
     * @throws OperationsError when the reading is for another machine or another day
     */
    public DayLog withMeter(MeterReading reading) {
        if (!reading.lift().equals(lift) || !reading.day().equals(day)) {
            throw new OperationsError("that meter reading is for another machine or another day");
        }
        return new DayLog(lift, day, window, sheet, holds, stops, entries, opened, closed, Optional.of(reading));
    }

    /**
     * Whether the first chair went out after first load.
     *
     * @return true when it opened late
     */
    public boolean openedLate() {
        return opened.isPresent() && opened.get().compareTo(window.firstLoad()) > 0;
    }

    /**
     * Whether the line was swept after the sweep time.
     *
     * @return true when it closed late
     */
    public boolean sweptLate() {
        return closed.isPresent() && closed.get().compareTo(window.sweepBy()) > 0;
    }

    /**
     * How long the machine was meant to run.
     *
     * @return first load to sweep
     */
    public Duration scheduledTime() {
        return window.firstLoad().until(window.sweepBy());
    }

    /**
     * How long the machine was open.
     *
     * @return opening to closing
     * @throws OperationsError until it has both opened and closed
     */
    public Duration openTime() {
        if (opened.isEmpty() || closed.isEmpty()) {
            throw new OperationsError("the machine has not run a full day yet");
        }
        return opened.get().until(closed.get());
    }

    /**
     * How long the machine actually ran.
     *
     * @return the open time with every closed stop taken off
     * @throws OperationsError until it has both opened and closed
     */
    public Duration runningTime() {
        return openTime().minus(stopLog().downtime());
    }

    /**
     * The book as it reads.
     *
     * @return every line in time order, lines written at the same minute in the order they were added
     */
    public List<LogEntry> lines() {
        return entries.stream().sorted(Comparator.comparing(LogEntry::at)).toList();
    }

    /**
     * The lines somebody typed, apart from the ones the book wrote itself.
     *
     * @return those lines in time order
     */
    public List<LogEntry> notes() {
        return lines().stream().filter(entry -> !entry.kind().isStructural()).toList();
    }

    /**
     * Such as {@code JP 2025-01-15, never opened}, {@code JP 2025-01-15, 9:00, still running} or
     * {@code JP 2025-01-15, 9:00 to 16:00}.
     */
    @Override
    public String toString() {
        if (opened.isEmpty()) {
            return lift + " " + day + ", never opened";
        }
        String tail = closed.map(at -> " to " + at).orElse(", still running");
        return lift + " " + day + ", " + opened.get() + tail;
    }

    private LogEntry line(TimeOfDay at, EntryKind kind, String text, String by) {
        return new LogEntry(LocalDateTime.of(day, at.toLocalTime()), kind, text, by);
    }

    private DayLog withOpened(TimeOfDay at) {
        return new DayLog(lift, day, window, sheet, holds, stops, entries, Optional.of(at), closed, meter);
    }

    private DayLog withClosed(TimeOfDay at) {
        return new DayLog(lift, day, window, sheet, holds, stops, entries, opened, Optional.of(at), meter);
    }
}
