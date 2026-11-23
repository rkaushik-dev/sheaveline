package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;

/**
 * The weight a sag model hangs on the rope.
 *
 * <p>On a rime night the rope comes out of the bullwheel in the morning carrying a sleeve of ice, and
 * the sleeve is heavier than it looks. Half an inch round a rope of an inch and three eighths is more
 * than a pound a foot, a third again on the rope's own weight, and the sag that follows is the sag
 * the clearance walk and the evacuation profile have to be read at.
 */
public final class Catenary {

    /** What glaze ice weighs, fifty seven pounds a cubic foot, the figure line engineers design to. */
    public static final BigDecimal ICE_POUNDS_PER_CUBIC_FOOT = BigDecimal.valueOf(57);

    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846");
    private static final BigDecimal SQUARE_INCHES_PER_SQUARE_FOOT = BigDecimal.valueOf(144);

    private Catenary() {}

    /**
     * What a sleeve of ice weighs on a foot of rope.
     *
     * @param diameter the rope's diameter
     * @param radialIce how thick the ice is, measured out from the rope's surface
     * @return the weight per foot of the sleeve, nothing for no ice
     * @throws LineError when the ice is thinner than nothing
     */
    public static LoadPerFoot iceWeight(Length diameter, Length radialIce) {
        if (radialIce.mils() < 0) {
            throw new LineError("ice on a rope is not thinner than nothing");
        }
        BigDecimal thickness = radialIce.inches();
        BigDecimal squareInches = Decimals.multiply(
                Decimals.multiply(PI, thickness), diameter.inches().add(thickness));
        BigDecimal cubicFeetPerFoot = Decimals.divide(squareInches, SQUARE_INCHES_PER_SQUARE_FOOT);
        return LoadPerFoot.ofPoundsPerFoot(Decimals.multiply(cubicFeetPerFoot, ICE_POUNDS_PER_CUBIC_FOOT));
    }

    /**
     * One machine's loaded line: its rope, a loaded carrier at every spacing, and the ice on the rope.
     *
     * @param lift the machine
     * @param spacing how far apart its carriers hang
     * @param radialIce how thick the ice on the rope is
     * @return the weight per foot the sag model hangs
     * @throws LineError when the spacing is not positive or the ice is negative
     */
    public static LoadPerFoot loadedLine(Lift lift, Length spacing, Length radialIce) {
        LoadPerFoot bare = lineWeight(
                lift.rope().spec().weight(), lift.carriers().onLine().getFirst().gross(), spacing);
        LoadPerFoot ice = iceWeight(lift.rope().diameter(), radialIce);
        return new LoadPerFoot(bare.millipoundsPerFoot() + ice.millipoundsPerFoot());
    }

    /**
     * Rope plus carriers, smeared along the line the way the sag formula wants.
     *
     * @param rope what the rope weighs per foot
     * @param carrierWeight what one carrier weighs
     * @param spacing how far apart the carriers hang
     * @return the weight per foot of the loaded line
     * @throws LineError when the spacing is not positive
     */
    public static LoadPerFoot lineWeight(LoadPerFoot rope, Load carrierWeight, Length spacing) {
        if (spacing.mils() <= 0) {
            throw new LineError("carriers on a line have a spacing");
        }
        LoadPerFoot perFoot = carrierWeight.over(spacing);
        return new LoadPerFoot(rope.millipoundsPerFoot() + perFoot.millipoundsPerFoot());
    }
}
