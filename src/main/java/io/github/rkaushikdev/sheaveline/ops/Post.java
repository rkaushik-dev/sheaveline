package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A place somebody has to be standing while the machine turns.
 *
 * <p>Minimum staffing is not a headcount. It is a set of posts that have to be covered for every
 * minute the machine is turning, and the set depends on what the machine is. A surface tow runs with
 * one person at the bottom. A chair or a gondola needs somebody at each end, because the top operator
 * is the only person who can see a chair that has not unloaded. A tram runs with a mechanic on site,
 * because a tram that stops does not clear itself.
 */
public enum Post implements Valued {
    /** The operator at the bottom terminal. */
    BOTTOM("bottom operator"),
    /** The operator at the top terminal. */
    TOP("top operator"),
    /** The attendant at a mid station. */
    MID("mid station attendant"),
    /** A mechanic on site. */
    MECHANIC("mechanic on site");

    private final String value;

    Post(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether whoever holds this post runs the machine.
     *
     * @return true for the bottom, top and mid station posts
     */
    public boolean isAnOperator() {
        return this == BOTTOM || this == TOP || this == MID;
    }

    /**
     * The posts a class of machine needs covered for every minute it turns.
     *
     * @param liftClass the class of machine
     * @return the required posts, in declaration order
     */
    public static Set<Post> requiredOn(LiftClass liftClass) {
        Set<Post> posts =
                switch (liftClass) {
                    case FIXED_CHAIR, DETACHABLE_CHAIR, PULSE_GONDOLA, DETACHABLE_GONDOLA -> EnumSet.of(BOTTOM, TOP);
                    case AERIAL_TRAM -> EnumSet.of(BOTTOM, TOP, MECHANIC);
                    case SURFACE_TOW -> EnumSet.of(BOTTOM);
                };
        return Collections.unmodifiableSet(posts);
    }
}
