package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakObservation;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.Indication;
import io.github.rkaushikdev.sheaveline.ropework.LayReading;
import io.github.rkaushikdev.sheaveline.ropework.MagneticTest;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.time.LocalDate;
import java.util.List;

/**
 * John Paul's haul rope, and the readings the rope file tests take off it, written the short way.
 *
 * <p>Every walk, count and trace here is of the same rope on the same January morning, signed by the
 * same people, so a test says only the readings it is about.
 */
public final class Ropes {

    /** John Paul's haul rope. */
    public static final HaulRope ROPE = Hill.johnPaul().rope();

    /** The morning everything here was taken. */
    public static final LocalDate TAKEN = LocalDate.of(2025, 1, 15);

    /** Who walks the rope. */
    public static final String INSPECTOR = "R Halvorsen";

    private Ropes() {}

    /**
     * A caliper reading, the same both ways round.
     *
     * @param stationFeet feet from the splice
     * @param inches the diameter, such as {@code "1.376"}
     * @return the reading
     */
    public static DiameterReading reading(long stationFeet, String inches) {
        return reading(stationFeet, inches, inches);
    }

    /**
     * A caliper reading across the crowns and then the valleys.
     *
     * @param stationFeet feet from the splice
     * @param inches across the crowns
     * @param second across the valleys
     * @return the reading
     */
    public static DiameterReading reading(long stationFeet, String inches, String second) {
        return new DiameterReading(Length.ofFeet(stationFeet), Length.ofInches(inches), Length.ofInches(second));
    }

    /**
     * A walk of the rope with diameter and lay readings.
     *
     * @param lays the lay readings
     * @param readings the caliper readings, at least one
     * @return the walk
     */
    public static WearSurvey walk(List<LayReading> lays, DiameterReading... readings) {
        return WearSurvey.of(ROPE, TAKEN, INSPECTOR, List.of(readings), lays);
    }

    /**
     * A walk of the rope with caliper readings only.
     *
     * @param readings the readings, at least one
     * @return the walk
     */
    public static WearSurvey walk(DiameterReading... readings) {
        return walk(List.of(), readings);
    }

    /**
     * Crown breaks counted at a station away from the splice.
     *
     * @param stationFeet feet from the splice
     * @param one breaks in one lay
     * @param six breaks in six lays
     * @return the count
     */
    public static BreakObservation seen(long stationFeet, int one, int six) {
        return seen(stationFeet, one, six, 0, false);
    }

    /**
     * Breaks counted at a station, with the valleys and whether it is at the splice.
     *
     * @param stationFeet feet from the splice
     * @param one breaks in one lay
     * @param six breaks in six lays
     * @param valley valley breaks among the one lay count
     * @param splice whether the station is at the splice
     * @return the count
     */
    public static BreakObservation seen(long stationFeet, int one, int six, int valley, boolean splice) {
        return new BreakObservation(Length.ofFeet(stationFeet), one, six, valley, splice);
    }

    /**
     * A broken wire count of the rope.
     *
     * @param observations the stations where something was counted
     * @return the count
     */
    public static BreakSurvey breaks(BreakObservation... observations) {
        return BreakSurvey.of(ROPE, TAKEN, INSPECTOR, List.of(observations));
    }

    /**
     * A magnetic test of the rope.
     *
     * @param indications the marks on the trace
     * @return the test
     */
    public static MagneticTest magnetic(Indication... indications) {
        return MagneticTest.of(ROPE, TAKEN, "Wire Rope Diagnostics", "LMA-250", List.of(indications));
    }

    /**
     * A loss of metallic area on the trace.
     *
     * @param stationFeet feet from the splice
     * @param percent the loss, such as {@code "7.4"}
     * @return the mark
     */
    public static Indication loss(long stationFeet, String percent) {
        return Indication.loss(Length.ofFeet(stationFeet), percent);
    }

    /**
     * A local fault on the trace.
     *
     * @param stationFeet feet from the splice
     * @param wires how many wires it stands for
     * @return the mark
     */
    public static Indication fault(long stationFeet, int wires) {
        return Indication.fault(Length.ofFeet(stationFeet), wires);
    }

    /**
     * Corrosion on the trace.
     *
     * @param stationFeet feet from the splice
     * @param percent how much of the section has gone to rust
     * @return the mark
     */
    public static Indication corrosion(long stationFeet, String percent) {
        return Indication.corrosion(Length.ofFeet(stationFeet), percent);
    }
}
