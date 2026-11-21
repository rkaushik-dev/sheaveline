package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * One part on one machine, with the meters as they read when it went on.
 *
 * <p>The number that matters is how much of the life is used rather than how much is left, because
 * the crew orders parts on a lead time and a part at ninety percent has to be on the shelf already.
 *
 * @param kind what the part is
 * @param lift the machine it is on
 * @param where where on the machine, such as {@code JP-T4}
 * @param fitted the day it went on
 * @param hoursAtFitting the hour meter when it went on
 * @param cyclesAtFitting the cycle counter when it went on
 * @param serial the maker's serial number, when the part carries one
 */
public record FittedPart(
        PartKind kind,
        LiftCode lift,
        String where,
        LocalDate fitted,
        BigDecimal hoursAtFitting,
        long cyclesAtFitting,
        Optional<String> serial) {

    /** The share of a life used at which a replacement wants ordering. */
    public static final BigDecimal ORDER_AT = new BigDecimal("0.90");

    /**
     * A part on a machine.
     *
     * @throws UpkeepError when it was fitted nowhere in particular, or a meter reads negative
     */
    public FittedPart {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(fitted, "fitted");
        Objects.requireNonNull(hoursAtFitting, "hoursAtFitting");
        Objects.requireNonNull(serial, "serial");
        if (where.strip().isEmpty()) {
            throw new UpkeepError(kind.code() + " was fitted nowhere in particular");
        }
        if (hoursAtFitting.signum() < 0 || cyclesAtFitting < 0) {
            throw new UpkeepError("a meter does not read negative");
        }
    }

    /**
     * A part with no serial number.
     *
     * @param kind what the part is
     * @param lift the machine it is on
     * @param where where on the machine
     * @param fitted the day it went on
     * @param hoursAtFitting the hour meter when it went on
     * @param cyclesAtFitting the cycle counter when it went on
     */
    public FittedPart(
            PartKind kind,
            LiftCode lift,
            String where,
            LocalDate fitted,
            BigDecimal hoursAtFitting,
            long cyclesAtFitting) {
        this(kind, lift, where, fitted, hoursAtFitting, cyclesAtFitting, Optional.empty());
    }

    /**
     * A part fitted to a machine whose meters read nought, with no serial number.
     *
     * @param kind what the part is
     * @param lift the machine it is on
     * @param where where on the machine
     * @param fitted the day it went on
     */
    public FittedPart(PartKind kind, LiftCode lift, String where, LocalDate fitted) {
        this(kind, lift, where, fitted, BigDecimal.ZERO, 0);
    }

    /**
     * The hour meter and the cycle counter with nothing missing from either.
     *
     * @param hours the hour meter
     * @param cycles the cycle counter
     */
    public record Readings(BigDecimal hours, long cycles) {

        /** Both readings. */
        public Readings {
            Objects.requireNonNull(hours, "hours");
        }
    }

    /**
     * Running hours since the part went on.
     *
     * @param hoursNow the hour meter now
     * @return the difference
     * @throws UpkeepError when the meter reads less than it did at fitting
     */
    public BigDecimal hoursUsed(BigDecimal hoursNow) {
        if (hoursNow.compareTo(hoursAtFitting) < 0) {
            throw new UpkeepError("the hour meter has gone backwards");
        }
        return hoursNow.subtract(hoursAtFitting);
    }

    /**
     * Cycles since the part went on.
     *
     * @param cyclesNow the cycle counter now
     * @return the difference
     * @throws UpkeepError when the counter reads less than it did at fitting
     */
    public long cyclesUsed(long cyclesNow) {
        if (cyclesNow < cyclesAtFitting) {
            throw new UpkeepError("the cycle counter has gone backwards");
        }
        return cyclesNow - cyclesAtFitting;
    }

    /**
     * Days since the part went on.
     *
     * @param on the day asked about
     * @return the whole days
     * @throws UpkeepError when the day is before the part went on
     */
    public long daysUsed(LocalDate on) {
        if (on.isBefore(fitted)) {
            throw new UpkeepError("that day is before the part went on");
        }
        return ChronoUnit.DAYS.between(fitted, on);
    }

    /**
     * The meters with nothing missing. A clock not given has not moved, which is not the same as
     * reading nought, so a missing reading is taken as the one at fitting.
     *
     * @param meters what the meters read now
     * @return the readings, a missing one filled in from fitting
     */
    public Readings readings(Meters meters) {
        return new Readings(
                meters.hours().orElse(hoursAtFitting), meters.cycles().orElse(cyclesAtFitting));
    }

    /**
     * How much of one clock's life has gone, with neither meter read.
     *
     * @param basis the clock
     * @param on the day asked about
     * @return the share used, nought for a clock the part does not wear out on
     */
    public BigDecimal usedOn(Basis basis, LocalDate on) {
        return usedOn(basis, on, Meters.NOT_READ);
    }

    /**
     * How much of one clock's life has gone, as a fraction.
     *
     * @param basis the clock
     * @param on the day asked about
     * @param meters what the meters read now
     * @return the share used, nought for a clock the part does not wear out on
     * @throws UpkeepError when a meter or the day is behind the fitting
     */
    public BigDecimal usedOn(Basis basis, LocalDate on, Meters meters) {
        Readings now = readings(meters);
        return switch (basis) {
            case HOURS ->
                kind.hours()
                        .map(life -> Decimals.divide(hoursUsed(now.hours()), life))
                        .orElse(BigDecimal.ZERO);
            case CYCLES ->
                kind.cycles()
                        .map(life -> Decimals.divide(cyclesUsed(now.cycles()), life))
                        .orElse(BigDecimal.ZERO);
            case CALENDAR ->
                kind.days().map(life -> Decimals.divide(daysUsed(on), life)).orElse(BigDecimal.ZERO);
        };
    }

    /**
     * The worst of the clocks, with neither meter read.
     *
     * @param on the day asked about
     * @return the largest share used
     */
    public BigDecimal used(LocalDate on) {
        return used(on, Meters.NOT_READ);
    }

    /**
     * The worst of the clocks, which is the one that ends the part.
     *
     * @param on the day asked about
     * @param meters what the meters read now
     * @return the largest share used over the clocks the part wears out on
     */
    public BigDecimal used(LocalDate on, Meters meters) {
        return usedOn(spentClock(on, meters), on, meters);
    }

    /**
     * The clock nearest to ending the part, with neither meter read.
     *
     * @param on the day asked about
     * @return the clock
     */
    public Basis spentClock(LocalDate on) {
        return spentClock(on, Meters.NOT_READ);
    }

    /**
     * The clock nearest to ending the part. Between two clocks equally far gone, the one that comes
     * first in the order calendar, hours, cycles.
     *
     * @param on the day asked about
     * @param meters what the meters read now
     * @return the clock with the largest share used
     */
    public Basis spentClock(LocalDate on, Meters meters) {
        return kind.bases().stream()
                .max(Comparator.comparing((Basis basis) -> usedOn(basis, on, meters)))
                .orElseThrow();
    }

    /**
     * Whether the part is at the end of its life, with neither meter read.
     *
     * @param on the day asked about
     * @return true once any clock is used up
     */
    public boolean isDue(LocalDate on) {
        return isDue(on, Meters.NOT_READ);
    }

    /**
     * Whether the part is at the end of its life.
     *
     * @param on the day asked about
     * @param meters what the meters read now
     * @return true once any clock is used up
     */
    public boolean isDue(LocalDate on, Meters meters) {
        return used(on, meters).compareTo(BigDecimal.ONE) >= 0;
    }

    /**
     * Whether the replacement wants ordering, with neither meter read.
     *
     * @param on the day asked about
     * @return true from {@link #ORDER_AT} of a life on
     */
    public boolean shouldBeOrdered(LocalDate on) {
        return shouldBeOrdered(on, Meters.NOT_READ);
    }

    /**
     * Whether the replacement wants ordering.
     *
     * @param on the day asked about
     * @param meters what the meters read now
     * @return true from {@link #ORDER_AT} of a life on
     */
    public boolean shouldBeOrdered(LocalDate on, Meters meters) {
        return used(on, meters).compareTo(ORDER_AT) >= 0;
    }

    /**
     * The day the calendar life ends.
     *
     * @return the day, or empty when the part has no calendar life
     */
    public Optional<LocalDate> calendarDue() {
        return kind.days().map(fitted::plusDays);
    }

    /** Such as {@code LIN at JP-T4, fitted 2024-11-01}. */
    @Override
    public String toString() {
        return kind.code() + " at " + where + ", fitted " + fitted;
    }
}
