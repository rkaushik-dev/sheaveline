package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * How long the department has to finish a work order, and what it may run in the meantime.
 *
 * <p>Priority is not typed in. It falls out of what raised the order, because a serious defect that
 * somebody decided was routine is exactly the thing an accident report goes looking for afterwards.
 */
public enum Priority implements Valued {
    /** Done before the machine runs again. */
    IMMEDIATE("immediate", 0),
    /** Done within three days. */
    URGENT("urgent", 3),
    /** Done within thirty days. */
    SCHEDULED("scheduled", 30),
    /** Done within the season. */
    ROUTINE("routine", 180);

    private final String value;
    private final int targetDays;

    Priority(String value, int targetDays) {
        this.value = value;
        this.targetDays = targetDays;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * How many days after it was raised an order at this priority should be done by.
     *
     * @return the days, nought for an immediate order
     */
    public int targetDays() {
        return targetDays;
    }

    /**
     * Whether an open order at this priority keeps the machine from carrying the public.
     *
     * @return true only for an immediate order
     */
    public boolean stopsTheMachine() {
        return this == IMMEDIATE;
    }

    /**
     * The one place a defect level turns into a deadline.
     *
     * @param level how bad the defect is
     * @return immediate for a serious defect, scheduled for a defect, routine for an observation
     */
    public static Priority forLevel(DefectLevel level) {
        return switch (level) {
            case SERIOUS -> IMMEDIATE;
            case DEFECT -> SCHEDULED;
            case OBSERVATION -> ROUTINE;
        };
    }
}
