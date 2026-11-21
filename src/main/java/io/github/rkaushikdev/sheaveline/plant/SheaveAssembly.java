package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;

/**
 * One side of a tower: its sheaves and the bend it puts in the rope.
 *
 * @param kind which way it bends the rope
 * @param sheaves how many sheaves, an even count of at least two
 * @param deviation the bend, positive downward
 */
public record SheaveAssembly(AssemblyKind kind, int sheaves, Angle deviation) {

    /**
     * An assembly whose bend matches its kind.
     *
     * @throws PlantError when the sheave count is odd or under two, or the bend goes the wrong way
     */
    public SheaveAssembly {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(deviation, "deviation");
        if (sheaves < 2 || sheaves % 2 != 0) {
            throw new PlantError("a sheave assembly has an even count of at least two, not " + sheaves);
        }
        if (kind == AssemblyKind.SUPPORT && !deviation.isUphill()) {
            throw new PlantError("a support assembly bends the rope downward");
        }
        if (kind == AssemblyKind.HOLD_DOWN && deviation.millidegrees() >= 0) {
            throw new PlantError("a hold down assembly bends the rope upward");
        }
        if (kind == AssemblyKind.COMBINATION && deviation.isFlat()) {
            throw new PlantError("a combination assembly bends the rope one way or the other");
        }
    }

    /**
     * How much of the rope load each sheave takes, evenly split.
     *
     * @param lineLoad the load on the assembly
     * @return the load on one sheave, the share taken to ten places
     */
    public Load perSheave(Load lineLoad) {
        return lineLoad.scaled(Decimals.fixed(1.0 / sheaves, 10));
    }

    /**
     * Whether this assembly bends the rope harder than the other, whichever way each one bends.
     *
     * @param other the other assembly
     * @return true when this bend is the larger
     */
    public boolean isHeavierThan(SheaveAssembly other) {
        return deviation.abs().compareTo(other.deviation.abs()) > 0;
    }

    /** As the tower schedule prints it, such as {@code 6s support at 1.50 deg}. */
    @Override
    public String toString() {
        return sheaves + "s " + kind.value() + " at " + deviation.formatDegrees();
    }
}
