package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.util.Objects;

/**
 * When a lift may load, and when it has to be swept.
 *
 * <p>Sweep is when the last carrier has to be off the line, and it is the time the department is
 * actually judged on, because a lift still turning after sweep is a lift the mountain manager has to
 * explain.
 *
 * @param firstLoad when the public may get on
 * @param lastLoad when the attendant stops loading
 * @param sweepBy when the line has to be clear
 */
public record OperatingWindow(TimeOfDay firstLoad, TimeOfDay lastLoad, TimeOfDay sweepBy) {

    /**
     * A window whose times run in order.
     *
     * @throws RangeError when last load is before first load, or sweep before last load
     */
    public OperatingWindow {
        Objects.requireNonNull(firstLoad, "firstLoad");
        Objects.requireNonNull(lastLoad, "lastLoad");
        Objects.requireNonNull(sweepBy, "sweepBy");
        if (lastLoad.compareTo(firstLoad) < 0) {
            throw new RangeError("last load", lastLoad, firstLoad, "later");
        }
        if (sweepBy.compareTo(lastLoad) < 0) {
            throw new RangeError("sweep", sweepBy, lastLoad, "later");
        }
    }

    /**
     * Read the three times as they are written on the hours sheet.
     *
     * @param first first load
     * @param last last load
     * @param sweep sweep
     * @return the window
     */
    public static OperatingWindow parse(String first, String last, String sweep) {
        return new OperatingWindow(TimeOfDay.parse(first), TimeOfDay.parse(last), TimeOfDay.parse(sweep));
    }

    /**
     * Whether the attendant is loading at that moment.
     *
     * @param moment the time
     * @return true from first load to last load, both included
     */
    public boolean isLoading(TimeOfDay moment) {
        return firstLoad.compareTo(moment) <= 0 && moment.compareTo(lastLoad) <= 0;
    }

    /**
     * Whether the lift is turning at that moment.
     *
     * @param moment the time
     * @return true from first load to sweep, both included
     */
    public boolean isRunning(TimeOfDay moment) {
        return firstLoad.compareTo(moment) <= 0 && moment.compareTo(sweepBy) <= 0;
    }

    /**
     * How long the lift loads.
     *
     * @return first load to last load
     */
    public Duration loadingTime() {
        return firstLoad.until(lastLoad);
    }

    /**
     * How long the line has to clear after the last chair is loaded.
     *
     * @return last load to sweep
     */
    public Duration sweepAllowance() {
        return lastLoad.until(sweepBy);
    }

    /** Such as {@code 9:00 to 16:00, swept by 16:30}. */
    @Override
    public String toString() {
        return firstLoad + " to " + lastLoad + ", swept by " + sweepBy;
    }
}
