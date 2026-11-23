package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What was counted at one station along the rope.
 *
 * @param station where along the rope, counted from the splice
 * @param inOneLay breaks inside one lay length
 * @param inSixLays breaks inside the six lay lengths around it
 * @param valleyBreaks how many of the one lay breaks are down in a valley
 * @param atSplice whether the station is at the splice
 */
public record BreakObservation(Length station, int inOneLay, int inSixLays, int valleyBreaks, boolean atSplice) {

    /**
     * Counts that could have been made.
     *
     * @throws RopeworkError when the station is behind the splice, a count is negative, six lays hold
     *     fewer than one, or there are more valley breaks than breaks
     */
    public BreakObservation {
        Objects.requireNonNull(station, "station");
        if (station.mils() < 0) {
            throw new RopeworkError("a break station is measured from the splice, forwards");
        }
        requireCounted("one lay", inOneLay);
        requireCounted("six lays", inSixLays);
        requireCounted("valley", valleyBreaks);
        if (inSixLays < inOneLay) {
            throw new RopeworkError("six lays cannot hold fewer breaks than one of them");
        }
        if (valleyBreaks > inOneLay) {
            throw new RopeworkError("valley breaks are counted inside the one lay total");
        }
    }

    /**
     * Crown breaks only, away from the splice.
     *
     * @param station where along the rope
     * @param inOneLay breaks inside one lay
     * @param inSixLays breaks inside six lays
     */
    public BreakObservation(Length station, int inOneLay, int inSixLays) {
        this(station, inOneLay, inSixLays, 0, false);
    }

    /**
     * Whether the counts are over a set of limits. A valley break usually means the wire has been
     * working against the one beside it for a long time, so one valley break past the limit fails the
     * station on its own.
     *
     * @param limits the allowance
     * @return true when any count is over
     */
    public boolean exceeds(BreakLimits limits) {
        return !breaches(limits).isEmpty();
    }

    /**
     * Which of a set of limits the counts are over, said the way the inspector reads them back.
     *
     * @param limits the allowance
     * @return one line a limit passed, such as {@code 7 in one lay against 6}, in the order the
     *     standard lists them, empty when the station is inside every one
     */
    public List<String> breaches(BreakLimits limits) {
        Objects.requireNonNull(limits, "limits");
        List<String> over = new ArrayList<>();
        over(over, inOneLay, limits.oneLay(), "in one lay");
        over(over, inSixLays, limits.sixLays(), "in six lays");
        over(over, valleyBreaks, limits.valley(), "in the valleys");
        if (atSplice) {
            over(over, inOneLay, limits.atSplice(), "in one lay at the splice");
        }
        return List.copyOf(over);
    }

    private static void over(List<String> over, int counted, int allowed, String where) {
        if (counted > allowed) {
            over.add(counted + " " + where + " against " + allowed);
        }
    }

    /** Such as {@code 3 in one lay at 600 ft at the splice}. */
    @Override
    public String toString() {
        String where = atSplice ? " at the splice" : "";
        return inOneLay + " in one lay at " + Decimals.fixed(station.feet(), 0) + " ft" + where;
    }

    private static void requireCounted(String name, int count) {
        if (count < 0) {
            throw new RopeworkError("a " + name + " break count cannot be negative");
        }
    }
}
