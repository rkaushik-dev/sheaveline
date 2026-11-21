package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.ops.MeterHistory;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a machine is due, read off its record book and its meters: one machine's ladder, anchored on
 * when it was built.
 *
 * <p>The awkward part is the anchor, and there are three cases rather than two. A monthly job done
 * four days late is still a monthly job, and its next one is counted from when it was due rather
 * than from when it was finally signed off, or the whole ladder walks later every month until the
 * annual inspection has drifted into the summer. A job done so late that it fell out of grace
 * restarts the chain, because at that point the old schedule is fiction. And a job done early also
 * restarts it, counted from the day the work was actually done, because nobody gets credit for a
 * cycle they skipped past.
 *
 * @param lift the machine
 * @param liftClass its class, which decides the jobs it carries
 * @param commissioned the day it was built, which anchors a job never done
 * @param book its record book
 * @param meter its meter history, when the meters are read
 */
public record UpkeepSchedule(
        LiftCode lift, LiftClass liftClass, LocalDate commissioned, RecordBook book, Optional<MeterHistory> meter) {

    /**
     * A ladder.
     *
     * @throws UpkeepError when the book or the meter history is another machine's, or a record
     *     predates the machine
     */
    public UpkeepSchedule {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(liftClass, "liftClass");
        Objects.requireNonNull(commissioned, "commissioned");
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(meter, "meter");
        if (!book.lift().equals(lift)) {
            throw new UpkeepError("that record book is for " + book.lift());
        }
        if (meter.isPresent() && !meter.get().lift().equals(lift)) {
            throw new UpkeepError("that meter history is for " + meter.get().lift());
        }
        for (InspectionRecord record : book.records()) {
            if (record.done().isBefore(commissioned)) {
                throw new UpkeepError(record.code() + " was done before " + lift + " was built");
            }
        }
    }

    /**
     * A ladder on a machine whose meters are not read.
     *
     * @param lift the machine
     * @param liftClass its class
     * @param commissioned the day it was built
     * @param book its record book
     */
    public UpkeepSchedule(LiftCode lift, LiftClass liftClass, LocalDate commissioned, RecordBook book) {
        this(lift, liftClass, commissioned, book, Optional.empty());
    }

    /**
     * The jobs the machine's class carries.
     *
     * @return those jobs, in the manual's order
     */
    public List<Requirement> jobs() {
        return Library.libraryFor(liftClass);
    }

    /**
     * The day the next one of a job is counted from, walking the chain of its records forward and
     * keeping the schedule where it was meant to be. A record done inside its window, from the due
     * date to the last day of grace, moves the anchor on to that due date. A record done early, or
     * after grace ran out, restarts the chain from the day it was done.
     *
     * @param code the job
     * @return the anchor, the commissioning day for a job never done
     * @throws UpkeepError when the manual has no such job
     */
    public LocalDate anchorFor(String code) {
        Interval interval = Library.requirement(code).interval();
        LocalDate anchor = commissioned;
        for (InspectionRecord record : book.forCode(code)) {
            Optional<LocalDate> due = interval.dueDate(anchor);
            Optional<LocalDate> graceEnd = interval.lastDayOfGrace(anchor);
            boolean inWindow = due.isPresent()
                    && graceEnd.isPresent()
                    && !record.done().isBefore(due.get())
                    && !record.done().isAfter(graceEnd.get());
            anchor = inWindow ? due.get() : record.done();
        }
        return anchor;
    }

    /**
     * One job and when it falls due on each of its clocks.
     *
     * @param code the job
     * @return the due item
     * @throws UpkeepError when the manual has no such job, or this machine's class does not carry it
     */
    public DueItem dueItem(String code) {
        Requirement job = Library.requirement(code);
        if (!job.appliesTo(liftClass)) {
            throw new UpkeepError(code + " is not a job on a " + liftClass.value());
        }
        Optional<InspectionRecord> last = book.lastDone(code);
        Optional<LocalDate> dueOn = job.interval().dueDate(anchorFor(code));
        Optional<BigDecimal> dueHours = last.flatMap(InspectionRecord::hours).flatMap(job.interval()::dueHours);
        Optional<Long> dueCycles = last.flatMap(InspectionRecord::cycles).flatMap(job.interval()::dueCycles);
        return new DueItem(job, dueOn, dueHours, dueCycles, last.isPresent());
    }

    /**
     * Every job on the ladder.
     *
     * @return a due item per job, in the manual's order
     */
    public List<DueItem> allDue() {
        return jobs().stream().map(job -> dueItem(job.code())).toList();
    }

    /**
     * What the hour meter read last.
     *
     * @return the latest reading's hours, or empty when the meters are not read
     */
    public Optional<BigDecimal> currentHours() {
        return meter.map(MeterHistory::latest).map(MeterReading::hours);
    }

    /**
     * What the cycle counter read last.
     *
     * @return the latest reading's cycles, or empty when the meters are not read
     */
    public Optional<Long> currentCycles() {
        return meter.map(MeterHistory::latest).map(MeterReading::cycles);
    }

    /**
     * The jobs past due on any clock, the meters read as they last were.
     *
     * @param on the day asked about
     * @return those due items, in the manual's order
     */
    public List<DueItem> overdue(LocalDate on) {
        Meters now = new Meters(currentHours(), currentCycles());
        return allDue().stream().filter(item -> item.isOverdue(on, now)).toList();
    }

    /**
     * The critical jobs past due, which are what stop the machine.
     *
     * @param on the day asked about
     * @return those due items, in the manual's order
     */
    public List<DueItem> overdueCritical(LocalDate on) {
        return overdue(on).stream().filter(item -> item.job().critical()).toList();
    }

    /**
     * The jobs the book holds no record of.
     *
     * @return those due items, in the manual's order
     */
    public List<DueItem> neverDone() {
        return allDue().stream().filter(item -> !item.everDone()).toList();
    }

    /**
     * The jobs falling due on the calendar from a given day to some days after it.
     *
     * @param on the first day
     * @param days how many days ahead to look
     * @return those due items, in the manual's order; a job already past due is not among them
     */
    public List<DueItem> dueWithin(LocalDate on, int days) {
        return allDue().stream()
                .filter(item -> item.daysUntil(on)
                        .filter(remaining -> remaining >= 0 && remaining <= days)
                        .isPresent())
                .toList();
    }

    /**
     * Whether the machine is current on its ladder.
     *
     * @param on the day asked about
     * @return true when no critical job is past due
     */
    public boolean isCurrent(LocalDate on) {
        return overdueCritical(on).isEmpty();
    }

    /** Such as {@code JP ladder, 20 jobs}. */
    @Override
    public String toString() {
        return lift + " ladder, " + jobs().size() + " jobs";
    }
}
