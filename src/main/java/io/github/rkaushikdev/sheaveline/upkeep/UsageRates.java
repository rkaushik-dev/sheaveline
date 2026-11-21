package io.github.rkaushikdev.sheaveline.upkeep;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * How fast a machine turns its meters over in a day, which is what it takes to turn a life left in
 * cycles or hours into days.
 *
 * <p>The rate is a thing the meter history knows and the shelf does not, so it is asked for rather
 * than assumed. A rate given as nought projects nothing, the same as a rate not given, but anything
 * worked out while a rate was given is labelled an estimate.
 *
 * @param cyclesADay cycles run in a day, when known
 * @param hoursADay running hours in a day, when known
 */
public record UsageRates(Optional<Long> cyclesADay, Optional<BigDecimal> hoursADay) {

    /** No rate known, so only the calendar can be projected. */
    public static final UsageRates NONE = new UsageRates(Optional.empty(), Optional.empty());

    /** Both rates, either of which may be missing. */
    public UsageRates {
        Objects.requireNonNull(cyclesADay, "cyclesADay");
        Objects.requireNonNull(hoursADay, "hoursADay");
    }

    /**
     * Only a cycle rate.
     *
     * @param cyclesADay cycles run in a day
     * @return the rates
     */
    public static UsageRates ofCyclesADay(long cyclesADay) {
        return new UsageRates(Optional.of(cyclesADay), Optional.empty());
    }

    /**
     * Only an hour rate.
     *
     * @param hoursADay running hours in a day
     * @return the rates
     */
    public static UsageRates ofHoursADay(BigDecimal hoursADay) {
        return new UsageRates(Optional.empty(), Optional.of(hoursADay));
    }

    /**
     * Whether either rate was given, which makes a projection from them an estimate.
     *
     * @return true when either is present, even as nought
     */
    public boolean anyGiven() {
        return cyclesADay.isPresent() || hoursADay.isPresent();
    }
}
