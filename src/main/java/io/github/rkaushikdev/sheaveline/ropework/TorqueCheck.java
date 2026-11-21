package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Torque;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One fixed grip, what the wrench found on it, and what it was left at.
 *
 * <p>A fixed grip is two jaws and a stack of bolts, and the bolts relax. Not much and not fast, but a
 * grip that reads forty inch pounds light has lost some of the clamp that is the only thing between
 * the chair and the ground. What the wrench finds before it moves anything is the number that
 * matters.
 *
 * @param carrier the carrier number
 * @param checked the day of the check
 * @param checker who ran the wrench
 * @param specified the figure the grip is set to
 * @param found what the wrench read before anything moved
 * @param resetTo what the grip was left at, empty when it was left as found
 */
public record TorqueCheck(
        int carrier, LocalDate checked, String checker, Torque specified, Torque found, Optional<Torque> resetTo) {

    /** The band either side of the specified figure, as a fraction of it. */
    public static final BigDecimal TORQUE_BAND = new BigDecimal("0.10");

    /**
     * A check that could have been made.
     *
     * @throws RopeworkError when the carrier number is under one, the check is unsigned, or there is no
     *     specified figure to read against
     */
    public TorqueCheck {
        Objects.requireNonNull(checked, "checked");
        Objects.requireNonNull(checker, "checker");
        Objects.requireNonNull(specified, "specified");
        Objects.requireNonNull(found, "found");
        Objects.requireNonNull(resetTo, "resetTo");
        if (carrier < 1) {
            throw new RopeworkError("a torque check names the carrier it was on");
        }
        if (checker.strip().isEmpty()) {
            throw new RopeworkError("a torque check is signed by whoever ran the wrench");
        }
        if (specified.isSlack()) {
            throw new RopeworkError("a torque check is read against a specified figure");
        }
    }

    /**
     * A check that left the grip as it was found.
     *
     * @param carrier the carrier number
     * @param checked the day of the check
     * @param checker who ran the wrench
     * @param specified the figure the grip is set to
     * @param found what the wrench read
     */
    public TorqueCheck(int carrier, LocalDate checked, String checker, Torque specified, Torque found) {
        this(carrier, checked, checker, specified, found, Optional.empty());
    }

    /**
     * Whether the grip was found inside a tenth of its figure.
     *
     * @return true in band
     */
    public boolean withinBand() {
        return found.within(specified, TORQUE_BAND);
    }

    /**
     * Whether the grip was found below the band. Over the band is a different fault and not this one.
     *
     * @return true when found slack
     */
    public boolean foundSlack() {
        return found.compareTo(specified) < 0 && !withinBand();
    }

    /**
     * How far the reading was from the figure.
     *
     * @return the gap between the specified and the found torque
     */
    public Torque shortfall() {
        return specified.minus(found);
    }

    /**
     * Whether the wrench moved the grip.
     *
     * @return true when it was reset
     */
    public boolean wasReset() {
        return resetTo.isPresent();
    }

    /**
     * Whether the grip was left inside the band, reset or not.
     *
     * @return true when what it was left at is in band
     */
    public boolean leftCorrect() {
        return resetTo.orElse(found).within(specified, TORQUE_BAND);
    }

    /** Such as {@code carrier 7 found 380 in lb slack}. */
    @Override
    public String toString() {
        String state = foundSlack() ? "slack" : "in band";
        return "carrier " + carrier + " found " + found + " " + state;
    }
}
