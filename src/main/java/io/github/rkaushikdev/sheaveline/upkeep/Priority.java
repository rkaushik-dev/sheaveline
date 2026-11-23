package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.util.Valued;
import java.util.Locale;

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

    /** How a place in the drive terminal ends, such as {@code JP-DRV}. */
    public static final String DRIVE_TERMINAL = "-DRV";

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
     * Whether an open order at this priority stops the machine once it is past its deadline.
     *
     * <p>An urgent order is three days because the fault will not wait much longer than that. One
     * that has been let run past them is a fault nobody has dealt with on a machine still carrying
     * the public, so the machine comes off until it is done.
     *
     * @return true only for an urgent order
     */
    public boolean stopsTheMachineWhenOverdue() {
        return this == URGENT;
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

    /**
     * The deadline for one defect, which is its level's unless it is in the drive terminal.
     *
     * <p>The drive house holds the motor, the gearbox and both brakes, and a defect there is a defect
     * in the thing that stops the rope. Thirty days is too long to leave one, so a defect there is
     * urgent rather than scheduled. A serious defect is immediate wherever it is, and an observation
     * is routine wherever it is.
     *
     * @param defect the defect
     * @return urgent for a defect at a place ending {@link #DRIVE_TERMINAL}, otherwise the level's
     */
    public static Priority forDefect(Defect defect) {
        Priority byLevel = forLevel(defect.level());
        boolean inTheDrive = defect.where().strip().toUpperCase(Locale.ROOT).endsWith(DRIVE_TERMINAL);
        return byLevel == SCHEDULED && inTheDrive ? URGENT : byLevel;
    }
}
