package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * What the mill makes, before any of it is cut: a construction, a diameter and a breaking strength,
 * the same for every reel of that product.
 *
 * <p>The design factor is a property of the specification and of the tension, never of either one
 * alone.
 *
 * @param construction strands by wires
 * @param grade the wire grade
 * @param lay which way the strands wind
 * @param nominalDiameter the diameter the mill quotes
 * @param breakingStrength the minimum breaking strength
 * @param weight the weight per foot
 */
public record RopeSpec(
        RopeConstruction construction,
        RopeGrade grade,
        LayDirection lay,
        Length nominalDiameter,
        Load breakingStrength,
        LoadPerFoot weight) {

    /** Breaking strength over working tension may never drop below four. */
    public static final BigDecimal MINIMUM_DESIGN_FACTOR = new BigDecimal(4);

    /**
     * A specification with real numbers in it.
     *
     * @throws PlantError when the diameter, the strength or the weight is not positive
     */
    public RopeSpec {
        Objects.requireNonNull(construction, "construction");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(lay, "lay");
        Objects.requireNonNull(nominalDiameter, "nominalDiameter");
        Objects.requireNonNull(breakingStrength, "breakingStrength");
        Objects.requireNonNull(weight, "weight");
        if (nominalDiameter.mils() <= 0) {
            throw new PlantError("a rope has a diameter");
        }
        if (breakingStrength.pounds() <= 0) {
            throw new PlantError("a rope has a breaking strength");
        }
        if (weight.millipoundsPerFoot() <= 0) {
            throw new PlantError("a rope has a weight per foot");
        }
    }

    /**
     * The wires a break is counted in.
     *
     * @return the construction's outer wire count
     */
    public int outerWires() {
        return construction.outerWires();
    }

    /**
     * One full turn of a strand, taken at six and a half diameters.
     *
     * @return the lay length
     */
    public Length layLength() {
        return nominalDiameter.scaled("6.5");
    }

    /**
     * Ninety three percent of nominal, below which the rope comes off.
     *
     * @return the condemning diameter
     */
    public Length condemningDiameter() {
        return nominalDiameter.scaled("0.93");
    }

    /**
     * What a run of this rope weighs.
     *
     * @param length the run
     * @return its weight
     */
    public Load weightOf(Length length) {
        return weight.times(length);
    }

    /**
     * Breaking strength over a working tension.
     *
     * @param tension the working tension
     * @return the design factor
     * @throws PlantError when the tension is not positive
     */
    public BigDecimal designFactorAt(Load tension) {
        if (tension.pounds() <= 0) {
            throw new PlantError("a design factor needs a tension");
        }
        return breakingStrength.ratio(tension);
    }

    /**
     * Whether the rope keeps its design factor at that tension.
     *
     * @param tension the working tension
     * @return true at four to one or better
     */
    public boolean safeAt(Load tension) {
        return designFactorAt(tension).compareTo(MINIMUM_DESIGN_FACTOR) >= 0;
    }

    /**
     * The highest tension the specification allows.
     *
     * @return a quarter of the breaking strength
     */
    public Load maximumTension() {
        return breakingStrength.scaled(Decimals.divide(BigDecimal.ONE, MINIMUM_DESIGN_FACTOR));
    }

    /** The way a reel tag reads, such as {@code 1.378" 6x25 filler wire}. */
    @Override
    public String toString() {
        return nominalDiameter.formatInches(3) + " " + construction.value();
    }
}
