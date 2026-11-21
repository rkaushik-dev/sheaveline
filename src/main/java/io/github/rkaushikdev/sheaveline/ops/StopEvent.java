package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One stop on one machine, and what it takes to run again after it.
 *
 * <p>A lift stops a dozen times a day and almost all of it is nothing: somebody drops a pole at the
 * unload and the operator hits the button. What matters is telling those apart from the handful that
 * are not nothing, because the answer to what happens next is completely different.
 *
 * @param lift the machine
 * @param kind why it stopped
 * @param stopped when the rope stopped
 * @param stoppedBy who called it
 * @param note what happened, which a reportable stop must say
 * @param restarted when it ran again, once it has
 * @param restartedBy who ran it again, once somebody has
 * @param lineChecked whether the line has been ridden since the stop
 */
public record StopEvent(
        LiftCode lift,
        StopKind kind,
        LocalDateTime stopped,
        String stoppedBy,
        String note,
        Optional<LocalDateTime> restarted,
        Optional<String> restartedBy,
        boolean lineChecked) {

    /** A stop at least this long has the line ridden before it runs again, whatever its kind. */
    public static final Duration LONG_STOP = Duration.ofMinutes(20);

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * A stop.
     *
     * @throws OperationsError when it is unsigned, ends before it started, has only one of the
     *     restart time and the person who restarted it, or is a reportable stop with no note
     */
    public StopEvent {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stopped, "stopped");
        Objects.requireNonNull(stoppedBy, "stoppedBy");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(restarted, "restarted");
        Objects.requireNonNull(restartedBy, "restartedBy");
        if (stoppedBy.strip().isEmpty()) {
            throw new OperationsError("a stop is signed by whoever called it");
        }
        if (restarted.isPresent() && restarted.get().isBefore(stopped)) {
            throw new OperationsError("a stop cannot end before it started");
        }
        if (restarted.isPresent() != restartedBy.isPresent()) {
            throw new OperationsError("a restart has a time and a name, or neither");
        }
        if (kind.isReportable() && note.strip().isEmpty()) {
            throw new OperationsError("a " + kind.value() + " is written up, not just logged");
        }
    }

    /**
     * A stop still on, with nothing written about it.
     *
     * @param lift the machine
     * @param kind why it stopped
     * @param stopped when the rope stopped
     * @param stoppedBy who called it
     */
    public StopEvent(LiftCode lift, StopKind kind, LocalDateTime stopped, String stoppedBy) {
        this(lift, kind, stopped, stoppedBy, "");
    }

    /**
     * A stop still on.
     *
     * @param lift the machine
     * @param kind why it stopped
     * @param stopped when the rope stopped
     * @param stoppedBy who called it
     * @param note what happened
     */
    public StopEvent(LiftCode lift, StopKind kind, LocalDateTime stopped, String stoppedBy, String note) {
        this(lift, kind, stopped, stoppedBy, note, Optional.empty(), Optional.empty(), false);
    }

    /**
     * Whether the rope is still stopped.
     *
     * @return true until somebody runs it again
     */
    public boolean isOpen() {
        return restarted.isEmpty();
    }

    /**
     * How long a closed stop lasted.
     *
     * @return the whole seconds from stop to restart
     * @throws OperationsError when the stop is still open
     */
    public Duration length() {
        return measure(Optional.empty());
    }

    /**
     * How long the stop lasted, or has lasted so far.
     *
     * @param now the moment an open stop is measured to; a closed stop is measured to its restart
     * @return the whole seconds
     * @throws OperationsError when the moment is before the stop
     */
    public Duration length(LocalDateTime now) {
        return measure(Optional.of(now));
    }

    /**
     * Whether a closed stop needs the line ridden before the machine runs again.
     *
     * @return true for the kinds that always do, and for any stop long enough to ice a grip
     * @throws OperationsError when the stop is open and its kind does not settle it
     */
    public boolean needsALineCheck() {
        return needsALineCheck(Optional.empty());
    }

    /**
     * Whether the stop needs the line ridden before the machine runs again.
     *
     * @param now the moment an open stop is measured to
     * @return true for the kinds that always do, and for any stop long enough to ice a grip
     */
    public boolean needsALineCheck(LocalDateTime now) {
        return needsALineCheck(Optional.of(now));
    }

    /**
     * Whether the machine may run again now.
     *
     * @param now the moment asked about
     * @return false for a stop already closed, and for one whose line check is owed and not done
     */
    public boolean mayRestart(LocalDateTime now) {
        if (!isOpen()) {
            return false;
        }
        if (needsALineCheck(now)) {
            return lineChecked;
        }
        return true;
    }

    /**
     * Run the machine again, with no line check done at the restart.
     *
     * @param when when it ran again
     * @param by who ran it
     * @return the closed stop; this one is unchanged
     * @throws OperationsError when the stop is already closed, or needs a line check nobody did
     */
    public StopEvent restart(LocalDateTime when, String by) {
        return restart(when, by, false);
    }

    /**
     * Run the machine again.
     *
     * @param when when it ran again
     * @param by who ran it
     * @param checkedNow whether the line was ridden before the restart
     * @return the closed stop; this one is unchanged
     * @throws OperationsError when the stop is already closed, or needs a line check nobody did
     */
    public StopEvent restart(LocalDateTime when, String by, boolean checkedNow) {
        if (!isOpen()) {
            throw new OperationsError("that stop has already been closed");
        }
        boolean checked = lineChecked || checkedNow;
        StopEvent ran =
                new StopEvent(lift, kind, stopped, stoppedBy, note, Optional.of(when), Optional.of(by), checked);
        if (ran.needsALineCheck() && !checked) {
            throw new OperationsError("a " + kind.value() + " runs again only after a line check");
        }
        return ran;
    }

    /**
     * The same stop with the line ridden.
     *
     * @return a new record; this one is unchanged
     */
    public StopEvent withLineCheck() {
        return new StopEvent(lift, kind, stopped, stoppedBy, note, restarted, restartedBy, true);
    }

    /** Such as {@code JP operator stop at 10:00, still stopped}. */
    @Override
    public String toString() {
        String state = isOpen() ? "still stopped" : "running";
        return lift + " " + kind.value() + " at " + stopped.format(CLOCK) + ", " + state;
    }

    private boolean needsALineCheck(Optional<LocalDateTime> now) {
        if (kind.alwaysNeedsALineCheck()) {
            return true;
        }
        return measure(now).compareTo(LONG_STOP) >= 0;
    }

    private Duration measure(Optional<LocalDateTime> now) {
        LocalDateTime finish = restarted
                .or(() -> now)
                .orElseThrow(() -> new OperationsError("an open stop needs a moment to be measured against"));
        if (finish.isBefore(stopped)) {
            throw new OperationsError("that moment is before the stop");
        }
        return Duration.ofSeconds(ChronoUnit.SECONDS.between(stopped, finish));
    }
}
