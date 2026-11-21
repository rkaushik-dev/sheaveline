package io.github.rkaushikdev.sheaveline.upkeep;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What the hour meter and the cycle counter on a drive read, where either may not have been read.
 *
 * <p>What an unread meter means is up to whoever asks. A fitted part takes it as a clock that has
 * not moved since the part went on, a due item as a clock that has not fired, and a projection of
 * when a part runs out as a clock it cannot project.
 *
 * @param hours the hour meter, when it was read
 * @param cycles the cycle counter, when it was read
 */
public record Meters(Optional<BigDecimal> hours, Optional<Long> cycles) {

    /** Neither meter read. */
    public static final Meters NOT_READ = new Meters(Optional.empty(), Optional.empty());

    /** Both readings, either of which may be missing. */
    public Meters {
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(cycles, "cycles");
    }

    /**
     * Both meters read.
     *
     * @param hours the hour meter
     * @param cycles the cycle counter
     * @return the readings
     */
    public static Meters of(BigDecimal hours, long cycles) {
        return new Meters(Optional.of(hours), Optional.of(cycles));
    }

    /**
     * Only the hour meter read.
     *
     * @param hours the hour meter
     * @return the readings
     */
    public static Meters ofHours(BigDecimal hours) {
        return new Meters(Optional.of(hours), Optional.empty());
    }

    /**
     * Only the cycle counter read.
     *
     * @param cycles the cycle counter
     * @return the readings
     */
    public static Meters ofCycles(long cycles) {
        return new Meters(Optional.empty(), Optional.of(cycles));
    }
}
