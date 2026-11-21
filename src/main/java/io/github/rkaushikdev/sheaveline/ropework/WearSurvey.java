package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One walk of the rope with a caliper and a rule.
 *
 * <p>Two numbers say most of what is worth knowing about a used rope. Diameter falls as the wires
 * wear and as the core crushes, and it falls fastest where the rope sits on a sheave. Lay length
 * stretches as the core gives up, and a lay that has opened out by more than a few percent means
 * the core is going even where the diameter still reads well.
 *
 * <p>Both are measured at stations along the rope, counted from the splice, because that is the only
 * mark on it that does not move.
 *
 * <p>What the readings mean is not decided here. The survey carries numbers and the rule table in
 * {@link Verdict} decides what they cost, so there is one place to look when the board asks where a
 * threshold came from.
 *
 * @param rope the rope walked
 * @param taken the day it was walked
 * @param inspector who signed it
 * @param diameters the caliper readings, in the order recorded
 * @param lays the lay readings, in the order recorded
 */
public record WearSurvey(
        HaulRope rope, LocalDate taken, String inspector, List<DiameterReading> diameters, List<LayReading> lays) {

    /**
     * A survey that could have been taken.
     *
     * @throws RopeworkError when it is unsigned, has no diameter reading, predates the rope, or reads
     *     a station past the end of it
     */
    public WearSurvey {
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(inspector, "inspector");
        diameters = List.copyOf(diameters);
        lays = List.copyOf(lays);
        if (inspector.strip().isEmpty()) {
            throw new RopeworkError("a survey is signed by whoever took it");
        }
        if (diameters.isEmpty()) {
            throw new RopeworkError("a survey has at least one diameter reading");
        }
        if (taken.isBefore(rope.installed())) {
            throw new RopeworkError("a survey cannot predate the rope it is of");
        }
        for (DiameterReading reading : diameters) {
            if (reading.station().compareTo(rope.length()) > 0) {
                throw new RopeworkError(
                        "station " + Decimals.fixed(reading.station().feet(), 0) + " is past the end");
            }
        }
    }

    /**
     * A walk with diameter readings and no lay readings.
     *
     * @param rope the rope walked
     * @param taken the day it was walked
     * @param inspector who signed it
     * @param diameters the caliper readings
     * @return the survey
     */
    public static WearSurvey of(HaulRope rope, LocalDate taken, String inspector, List<DiameterReading> diameters) {
        return of(rope, taken, inspector, diameters, List.of());
    }

    /**
     * A walk with both kinds of reading.
     *
     * @param rope the rope walked
     * @param taken the day it was walked
     * @param inspector who signed it
     * @param diameters the caliper readings
     * @param lays the lay readings
     * @return the survey
     */
    public static WearSurvey of(
            HaulRope rope, LocalDate taken, String inspector, List<DiameterReading> diameters, List<LayReading> lays) {
        return new WearSurvey(rope, taken, inspector, diameters, lays);
    }

    /**
     * The diameter the rope was made to.
     *
     * @return the rope's nominal diameter
     */
    public Length nominal() {
        return rope.diameter();
    }

    /**
     * The smallest reading, which is the one that matters.
     *
     * @return the reading with the least mean diameter, the first recorded on a tie
     */
    public DiameterReading smallest() {
        return diameters.stream()
                .min(Comparator.comparingLong(reading -> reading.mean().mils()))
                .orElseThrow();
    }

    /**
     * The reading furthest from round.
     *
     * @return the reading with the most ovality, the first recorded on a tie
     */
    public DiameterReading worstOvality() {
        return diameters.stream()
                .max(Comparator.comparingLong(reading -> reading.ovality().mils()))
                .orElseThrow();
    }

    /**
     * A reading as a percentage of nominal.
     *
     * @param reading one of the readings
     * @return the mean diameter over nominal, times a hundred
     */
    public BigDecimal percentOfNominal(DiameterReading reading) {
        return Decimals.multiply(reading.mean().ratio(nominal()), 100);
    }

    /**
     * Every reading under the condemning diameter.
     *
     * @return those readings, in the order recorded
     */
    public List<DiameterReading> belowCondemning() {
        Length limit = rope.spec().condemningDiameter();
        return diameters.stream()
                .filter(reading -> reading.mean().compareTo(limit) < 0)
                .toList();
    }

    /**
     * How far the worst lay has opened out.
     *
     * @return the largest stretch as a fraction, zero when no lay was read
     */
    public BigDecimal worstLayStretch() {
        if (lays.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Length nominal = rope.spec().layLength();
        return lays.stream()
                .map(reading -> reading.stretchAgainst(nominal))
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    /**
     * The stations walked.
     *
     * @return the diameter stations, in the order recorded
     */
    public List<Length> stations() {
        return diameters.stream().map(DiameterReading::station).toList();
    }

    /** Such as {@code FAT-2019-04412 walked on 2025-01-15}. */
    @Override
    public String toString() {
        return rope.serial() + " walked on " + taken;
    }
}
