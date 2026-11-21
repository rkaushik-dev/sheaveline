package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.plant.RopeSpec;
import java.util.Objects;

/**
 * How many broken wires a rope of one construction is allowed to carry.
 *
 * @param oneLay breaks allowed inside one lay length
 * @param sixLays breaks allowed inside six lay lengths
 * @param valley valley breaks allowed inside one lay
 * @param atSplice breaks allowed in one lay at the splice
 */
public record BreakLimits(int oneLay, int sixLays, int valley, int atSplice) {

    /** Breaks allowed in one lay of a regular lay rope. */
    public static final int REGULAR_ONE_LAY = 6;

    /** Breaks allowed in six lays of a regular lay rope. */
    public static final int REGULAR_SIX_LAYS = 12;

    /** Breaks allowed in one lay of a lang lay rope, half the regular allowance. */
    public static final int LANG_ONE_LAY = 3;

    /** Breaks allowed in six lays of a lang lay rope. */
    public static final int LANG_SIX_LAYS = 6;

    /** Valley breaks allowed in one lay, whatever the lay. */
    public static final int VALLEY_LIMIT = 1;

    /** Breaks allowed in one lay at the splice. */
    public static final int SPLICE_LIMIT = 2;

    /**
     * The allowance for a specification. Lang lay ropes get half, because the outer wires run more
     * nearly along the rope and a break in one carries further.
     *
     * @param spec the rope specification
     * @return the limits
     */
    public static BreakLimits forSpec(RopeSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.lay().isLang()) {
            return new BreakLimits(LANG_ONE_LAY, LANG_SIX_LAYS, VALLEY_LIMIT, SPLICE_LIMIT);
        }
        return new BreakLimits(REGULAR_ONE_LAY, REGULAR_SIX_LAYS, VALLEY_LIMIT, SPLICE_LIMIT);
    }

    /**
     * Half the allowance, which is where the rope goes on the watch list. No valley break and no
     * break at the splice is let past at this level.
     *
     * @return the watch limits
     */
    public BreakLimits watchLevel() {
        return new BreakLimits(Math.floorDiv(oneLay, 2), Math.floorDiv(sixLays, 2), 0, 0);
    }
}
