package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;

/**
 * One tower, shot once with the total station, against the drawing it was built to.
 *
 * <p>Lean is the top going sideways. It shows up as an offset measured from the base, which turns
 * into an angle only when the tower's own height is known: a short tower and a tall one with the same
 * offset are not in the same trouble, so the height is asked for rather than assumed.
 *
 * @param ref the tower
 * @param station where it was shot along the line
 * @param crossarmElevation the crossarm elevation shot
 * @param offsetAtTop how far the top sits out from over the base
 */
public record TowerShot(TowerRef ref, Length station, Length crossarmElevation, Length offsetAtTop) {

    /** How far out of plumb a tower may lean before it is a finding. */
    public static final Angle PLUMB_TOLERANCE = Angle.ofDegrees("0.50");

    /**
     * A shot on the line.
     *
     * @throws PlantError when the station is behind the bottom terminal
     */
    public TowerShot {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(crossarmElevation, "crossarmElevation");
        Objects.requireNonNull(offsetAtTop, "offsetAtTop");
        if (station.mils() < 0) {
            throw new PlantError(ref + " cannot be behind the bottom terminal");
        }
    }

    /**
     * The offset turned into an angle over the tower's height.
     *
     * @param height the tower's height
     * @return the lean
     * @throws PlantError when the height is not positive
     */
    public Angle lean(Length height) {
        if (height.mils() <= 0) {
            throw new PlantError(ref + " has no height to lean over");
        }
        return Angle.ofRiseRun(offsetAtTop, height);
    }

    /**
     * Whether the tower stands within the plumb tolerance.
     *
     * @param height the tower's height
     * @return true within half a degree
     */
    public boolean isPlumb(Length height) {
        return isPlumb(height, PLUMB_TOLERANCE);
    }

    /**
     * Whether the tower stands within a plumb tolerance.
     *
     * @param height the tower's height
     * @param tolerance the largest lean allowed either way
     * @return true within the tolerance
     */
    public boolean isPlumb(Length height, Angle tolerance) {
        return Math.abs(lean(height).millidegrees()) <= tolerance.millidegrees();
    }

    /** Such as {@code JP-T4 at 2400 ft, top out 0' 6"}. */
    @Override
    public String toString() {
        return ref + " at " + Decimals.fixed(station.feet(), 0) + " ft, top out " + offsetAtTop;
    }
}
