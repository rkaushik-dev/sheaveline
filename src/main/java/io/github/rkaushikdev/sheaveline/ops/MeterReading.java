package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * What the two counters on the drive said on one morning.
 *
 * <p>The hour meter has five digits and a tenth, so it wraps at ninety nine thousand nine hundred and
 * ninety nine point nine and starts again at nothing. The cycle counter gets replaced when the drive
 * controller is replaced and comes back reading zero.
 *
 * @param lift the machine
 * @param day the morning it was read
 * @param hours what the hour meter said
 * @param cycles what the cycle counter said
 * @param readBy who read it
 * @param meterReplaced whether the meter was new that morning, so it counts again from zero
 */
public record MeterReading(
        LiftCode lift, LocalDate day, BigDecimal hours, long cycles, String readBy, boolean meterReplaced) {

    /** The reading the hour meter wraps at, which it never shows. */
    public static final BigDecimal METER_ROLLOVER = new BigDecimal("100000.0");

    /**
     * A signed reading inside the dial.
     *
     * @throws OperationsError when it is unsigned, the hours are outside the dial, or the cycles are
     *     negative
     */
    public MeterReading {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(readBy, "readBy");
        if (readBy.strip().isEmpty()) {
            throw new OperationsError("a meter reading is signed by whoever took it");
        }
        if (hours.signum() < 0 || hours.compareTo(METER_ROLLOVER) >= 0) {
            throw new OperationsError("an hour meter reads under " + METER_ROLLOVER);
        }
        if (cycles < 0) {
            throw new OperationsError("a cycle counter does not read negative");
        }
    }

    /**
     * A reading off the meter that was already on the machine.
     *
     * @param lift the machine
     * @param day the morning it was read
     * @param hours what the hour meter said
     * @param cycles what the cycle counter said
     * @param readBy who read it
     */
    public MeterReading(LiftCode lift, LocalDate day, BigDecimal hours, long cycles, String readBy) {
        this(lift, day, hours, cycles, readBy, false);
    }

    /**
     * The hour meter as elapsed time.
     *
     * @return the hours in whole seconds, any part of a second dropped
     */
    public Duration runningTime() {
        BigDecimal seconds = Decimals.multiply(hours, Duration.SECONDS_PER_HOUR);
        return Duration.ofSeconds(seconds.setScale(0, RoundingMode.DOWN).longValueExact());
    }

    /** Such as {@code JP 2025-01-01, 42000.0 hours, 1,000,000 cycles}. */
    @Override
    public String toString() {
        return lift + " " + day + ", " + hours + " hours, " + String.format(Locale.ROOT, "%,d", cycles) + " cycles";
    }
}
