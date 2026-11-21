package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * What one machine moves in an hour at a given spacing and loading.
 *
 * <p>Capacity gets quoted three different ways and only one of them is honest. Seats an hour is what
 * the machine could do with every place filled, which never happens. Persons an hour is what it does
 * with the loading efficiency the crew actually achieves, and that is the number the ski school and
 * the patrol plan against. Vertical feet an hour is the two multiplied by the rise, and it is the
 * only figure that lets one lift be compared against another. Every figure is a whole count, any
 * fraction dropped.
 *
 * @param spacing the carriers on the line and the rope speed
 * @param seats the seats on each carrier
 * @param occupancy the loading efficiency, the fraction of seats filled
 */
public record UphillCapacity(LineSpacing spacing, int seats, BigDecimal occupancy) {

    /** The loading efficiency a crew achieves unless told otherwise. */
    public static final BigDecimal DEFAULT_OCCUPANCY = new BigDecimal("0.85");

    /**
     * A capacity with seats to fill and a loading efficiency that is a fraction of them.
     *
     * @throws CapacityError when there are no seats, or the occupancy is not above zero and at most
     *     one
     */
    public UphillCapacity {
        Objects.requireNonNull(spacing, "spacing");
        Objects.requireNonNull(occupancy, "occupancy");
        if (seats < 1) {
            throw new CapacityError("a carrier has seats");
        }
        if (occupancy.signum() <= 0 || occupancy.compareTo(BigDecimal.ONE) > 0) {
            throw new CapacityError("loading efficiency is a fraction of the seats");
        }
    }

    /**
     * A capacity at the usual loading efficiency.
     *
     * @param spacing the carriers on the line and the rope speed
     * @param seats the seats on each carrier
     */
    public UphillCapacity(LineSpacing spacing, int seats) {
        this(spacing, seats, DEFAULT_OCCUPANCY);
    }

    /**
     * A lift's own capacity at the usual loading efficiency.
     *
     * @param lift the machine
     * @return its spacing at design speed and its carriers' seats
     */
    public static UphillCapacity of(Lift lift) {
        return of(lift, DEFAULT_OCCUPANCY);
    }

    /**
     * A lift's own capacity at a loading efficiency.
     *
     * @param lift the machine
     * @param occupancy the fraction of seats filled
     * @return its spacing at design speed and its carriers' seats
     */
    public static UphillCapacity of(Lift lift, BigDecimal occupancy) {
        return new UphillCapacity(LineSpacing.of(lift), lift.carriers().kind().seats(), occupancy);
    }

    /**
     * Carriers through the gate in an hour.
     *
     * @return an hour over the interval
     */
    public int carriersPerHour() {
        return Decimals.divide(Duration.SECONDS_PER_HOUR, spacing.interval().seconds())
                .toBigInteger()
                .intValueExact();
    }

    /**
     * Every place filled, which is the figure a brochure prints.
     *
     * @return carriers an hour times the seats
     */
    public int seatsPerHour() {
        return carriersPerHour() * seats;
    }

    /**
     * What the crew actually loads, which is the figure worth planning against.
     *
     * @return seats an hour times the loading efficiency
     */
    public int personsPerHour() {
        return Decimals.multiply(Decimals.of(seatsPerHour()), occupancy)
                .toBigInteger()
                .intValueExact();
    }

    /**
     * Persons an hour times the rise, which is how two lifts are compared.
     *
     * @param rise the vertical rise of the line
     * @return vertical feet an hour
     * @throws CapacityError when the rise is not positive
     */
    public long verticalFeetPerHour(Length rise) {
        if (rise.mils() <= 0) {
            throw new CapacityError("vertical transport needs a rise");
        }
        return Decimals.multiply(Decimals.of(personsPerHour()), rise.feet())
                .toBigInteger()
                .longValueExact();
    }

    /**
     * The same machine at another loading efficiency.
     *
     * @param changed the fraction of seats filled
     * @return the new capacity
     */
    public UphillCapacity atOccupancy(BigDecimal changed) {
        return new UphillCapacity(spacing, seats, changed);
    }

    /**
     * The same machine at a loading efficiency written as digits.
     *
     * @param changed the fraction, such as {@code "0.60"}
     * @return the new capacity
     */
    public UphillCapacity atOccupancy(String changed) {
        return atOccupancy(Decimals.of(changed));
    }

    /**
     * The same seats and loading on another line.
     *
     * @param changed the spacing
     * @return the new capacity
     */
    public UphillCapacity withSpacing(LineSpacing changed) {
        return new UphillCapacity(changed, seats, occupancy);
    }

    /**
     * How many carriers the line needs to make a target. The share each carrier carries is worked in
     * binary floating point and the count is rounded up.
     *
     * @param wanted persons an hour
     * @return the carriers needed
     * @throws CapacityError when the target is under one person
     * @throws ArithmeticException when the line carries nobody, so no count of carriers makes a target
     */
    public int carriersForPersonsPerHour(int wanted) {
        if (wanted < 1) {
            throw new CapacityError("a capacity target is a positive number of people");
        }
        double perCarrier = (double) personsPerHour() / spacing.carriers();
        if (perCarrier == 0) {
            throw new ArithmeticException("division by zero");
        }
        return Math.toIntExact((long) Math.ceil(wanted / perCarrier));
    }

    /** Both figures, such as {@code 2,040 people an hour, 2,400 seats}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%,d people an hour, %,d seats", personsPerHour(), seatsPerHour());
    }
}
