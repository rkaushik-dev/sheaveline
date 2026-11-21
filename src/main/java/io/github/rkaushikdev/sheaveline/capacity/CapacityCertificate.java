package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Locale;
import java.util.Objects;

/**
 * The capacity figure filed with the board when the machine was accepted.
 *
 * <p>Running above it is not a capacity question, it is a paperwork question with an inspector on the
 * end of it.
 *
 * @param filing the board's filing number
 * @param issued the day the certificate was issued
 * @param ratedPersonsPerHour the capacity filed
 * @param ratedCarriers the carriers on the line when it was rated
 */
public record CapacityCertificate(FilingNumber filing, LocalDate issued, int ratedPersonsPerHour, int ratedCarriers) {

    /** How far either side of the filed figure a machine may run and still agree with it. */
    public static final BigDecimal CERTIFICATE_TOLERANCE = new BigDecimal("0.02");

    /**
     * A certificate rating a real capacity with real carriers.
     *
     * @throws CapacityError when the rated capacity or carriers are under one
     */
    public CapacityCertificate {
        Objects.requireNonNull(filing, "filing");
        Objects.requireNonNull(issued, "issued");
        if (ratedPersonsPerHour < 1) {
            throw new CapacityError("a certificate rates a positive capacity");
        }
        if (ratedCarriers < 1) {
            throw new CapacityError("a certificate names the carriers it was rated with");
        }
    }

    /**
     * The persons an hour a machine may run at and still agree with the certificate.
     *
     * @return two percent either side of the filed figure, each end a whole count with any fraction
     *     dropped
     */
    public Band band() {
        BigDecimal rated = Decimals.of(ratedPersonsPerHour);
        BigDecimal margin = Decimals.multiply(rated, CERTIFICATE_TOLERANCE);
        return new Band(
                rated.subtract(margin, Decimals.CONTEXT).toBigInteger().intValueExact(),
                rated.add(margin, Decimals.CONTEXT).toBigInteger().intValueExact());
    }

    /**
     * Whether a machine runs inside the band.
     *
     * @param capacity what it moves
     * @return true from the low end to the high end
     */
    public boolean agreesWith(UphillCapacity capacity) {
        Band band = band();
        int persons = capacity.personsPerHour();
        return band.low() <= persons && persons <= band.high();
    }

    /**
     * Running above the filed figure, which is a matter for the board.
     *
     * @param capacity what the machine moves
     * @return true above the high end of the band
     */
    public boolean exceededBy(UphillCapacity capacity) {
        return capacity.personsPerHour() > band().high();
    }

    /**
     * Carriers added since the rating, or taken off.
     *
     * @param capacity what the machine runs now
     * @return the change, negative when carriers came off
     */
    public int carriersChanged(UphillCapacity capacity) {
        return capacity.spacing().carriers() - ratedCarriers;
    }

    /**
     * Whole years since the certificate was issued.
     *
     * @param on the day asked about
     * @return the completed years
     * @throws CapacityError when the day is before the certificate was issued
     */
    public int ageInYears(LocalDate on) {
        if (on.isBefore(issued)) {
            throw new CapacityError(on + " is before the certificate was issued");
        }
        int years = on.getYear() - issued.getYear();
        if (MonthDay.from(on).isBefore(MonthDay.from(issued))) {
            years -= 1;
        }
        return years;
    }

    /** The filing and its figure, such as {@code UT-2004-0311, 2,040 an hour}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s, %,d an hour", filing, ratedPersonsPerHour);
    }

    /**
     * The persons an hour a certificate allows, both ends included.
     *
     * @param low the least
     * @param high the most
     */
    public record Band(int low, int high) {}
}
