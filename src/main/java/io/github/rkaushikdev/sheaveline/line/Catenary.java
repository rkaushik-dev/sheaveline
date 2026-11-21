package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;

/** The weight a sag model hangs on the rope. */
public final class Catenary {

    private Catenary() {}

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
