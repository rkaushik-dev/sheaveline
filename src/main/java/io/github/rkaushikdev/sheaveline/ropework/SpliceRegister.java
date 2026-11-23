package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Every splice in one rope, and what they add up to.
 *
 * <p>A haul rope is a loop, so it has at least one splice in it and every splice is a place where the
 * rope is not quite the rope. The mill splice is made when the rope goes on. Every one after that is
 * the record of something going wrong: a section came out, the ends were tucked back together, and
 * the rope got shorter.
 *
 * <p>Shorter matters. The tension carriage has a fixed travel and every shortening moves it toward
 * one end of it, so a rope that has been cut three times can be perfectly sound and still be
 * finished, because the carriage has nowhere left to go.
 *
 * @param rope the rope
 * @param splices its splices, in station order
 */
public record SpliceRegister(HaulRope rope, List<Splice> splices) {

    /** Splices must be at least this many rope diameters apart. */
    public static final long MINIMUM_SEPARATION_DIAMETERS = 3000;

    /** A rope with more splices than this is finished, however good they are. */
    public static final int MAXIMUM_SPLICES = 4;

    /**
     * A register that describes a real rope.
     *
     * @throws RopeworkError when it is empty, has other than one mill splice, puts two splices at one
     *     station, or has a splice past the end of the rope
     */
    public SpliceRegister {
        Objects.requireNonNull(rope, "rope");
        splices = List.copyOf(splices);
        if (splices.isEmpty()) {
            throw new RopeworkError("a loop of rope has at least the mill splice in it");
        }
        if (splices.stream().filter(Splice::mill).count() != 1) {
            throw new RopeworkError("a rope has exactly one mill splice");
        }
        if (splices.stream().map(splice -> splice.station().mils()).distinct().count() != splices.size()) {
            throw new RopeworkError("two splices cannot sit at the same station");
        }
        for (Splice splice : splices) {
            if (splice.station().compareTo(rope.length()) > 0) {
                throw new RopeworkError(
                        "station " + Decimals.fixed(splice.station().feet(), 0) + " is past the end");
            }
        }
    }

    /**
     * A register with its splices put in station order.
     *
     * @param rope the rope
     * @param splices the splices, in any order
     * @return the register
     */
    public static SpliceRegister of(HaulRope rope, List<Splice> splices) {
        return new SpliceRegister(
                rope,
                splices.stream()
                        .sorted(Comparator.comparingLong(
                                splice -> splice.station().mils()))
                        .toList());
    }

    /**
     * How many splices the rope carries.
     *
     * @return the count, mill splice included
     */
    public int count() {
        return splices.size();
    }

    /**
     * The splices made after the rope went on.
     *
     * @return those splices, in station order
     */
    public List<Splice> fieldSplices() {
        return splices.stream().filter(splice -> !splice.mill()).toList();
    }

    /**
     * How far apart two splices must be.
     *
     * @return three thousand rope diameters
     */
    public Length minimumSeparation() {
        return rope.diameter().scaled(MINIMUM_SEPARATION_DIAMETERS);
    }

    /**
     * Gaps between splices round the loop, including the wrap past the end.
     *
     * @return one gap after each splice, the whole rope when there is only one
     */
    public List<Length> separations() {
        if (splices.size() == 1) {
            return List.of(rope.length());
        }
        List<Length> gaps = new ArrayList<>();
        for (int index = 1; index < splices.size(); index++) {
            gaps.add(splices.get(index).station().minus(splices.get(index - 1).station()));
        }
        Length wrap = rope.length()
                .minus(splices.get(splices.size() - 1).station())
                .plus(splices.get(0).station());
        gaps.add(wrap);
        return List.copyOf(gaps);
    }

    /**
     * The clear rope after each splice before the next one starts, round the loop.
     *
     * <p>The separation the standard asks for is between splices, not between the places they were
     * marked at. A splice takes up twelve hundred diameters of rope, and the strands are disturbed
     * over all of it, so the rope that counts as clear starts where the last tuck ends.
     *
     * @return one length after each splice, from its end to the start of the next, in loop order
     */
    public List<Length> clearRope() {
        List<Length> gaps = separations();
        List<Length> clear = new ArrayList<>();
        for (int index = 0; index < splices.size(); index++) {
            clear.add(gaps.get(index).minus(splices.get(index).length()));
        }
        return List.copyOf(clear);
    }

    /**
     * The clear rope shorter than the minimum separation.
     *
     * @return those lengths, in loop order
     */
    public List<Length> tooClose() {
        Length limit = minimumSeparation();
        return clearRope().stream().filter(gap -> gap.compareTo(limit) < 0).toList();
    }

    /**
     * Every way the register fails, each said the way the rope file prints it.
     *
     * @return one line for too many splices, one for each splice with too little clear rope after it,
     *     and one for each short splice, empty when the register passes
     */
    public List<String> problems() {
        List<String> found = new ArrayList<>();
        if (count() > MAXIMUM_SPLICES) {
            found.add(count() + " splices against a limit of " + MAXIMUM_SPLICES);
        }
        Length limit = minimumSeparation();
        List<Length> clear = clearRope();
        for (int index = 0; index < splices.size(); index++) {
            if (clear.get(index).compareTo(limit) < 0) {
                found.add(feet(clear.get(index)) + " ft clear after the splice at "
                        + feet(splices.get(index).station()) + " ft, wants " + feet(limit) + " ft");
            }
        }
        for (Splice splice : shortSplices()) {
            found.add("splice at " + feet(splice.station()) + " ft is " + feet(splice.length()) + " ft long, wants "
                    + feet(splice.minimumLength(rope.spec())) + " ft");
        }
        return List.copyOf(found);
    }

    /**
     * The splices shorter than a long splice.
     *
     * @return those splices, in station order
     */
    public List<Splice> shortSplices() {
        return splices.stream()
                .filter(splice -> !splice.isLongEnough(rope.spec()))
                .toList();
    }

    /**
     * The splice nearest a station, measured along the rope without wrapping.
     *
     * @param station the station
     * @return the nearest splice, the earlier on a tie
     */
    public Splice nearestTo(Length station) {
        return splices.stream()
                .min(Comparator.comparingLong(
                        splice -> Math.abs(splice.station().minus(station).mils())))
                .orElseThrow();
    }

    /**
     * Whether the register passes: few enough splices, kept far enough apart, and each long enough.
     *
     * @return true when the rope's splices are acceptable
     */
    public boolean isAcceptable() {
        return problems().isEmpty();
    }

    /**
     * Cut a piece out and tuck the ends together. The rope gets shorter and carries one more splice.
     *
     * @param removed the length cut out
     * @param splice the field splice that joins the ends
     * @return the register of the shortened rope
     * @throws RopeworkError when nothing or the whole rope is removed, or the splice is a mill splice
     */
    public SpliceRegister shortenedBy(Length removed, Splice splice) {
        if (removed.mils() <= 0) {
            throw new RopeworkError("a shortening removes a positive length");
        }
        if (removed.compareTo(rope.length()) >= 0) {
            throw new RopeworkError("that would remove the whole rope");
        }
        if (splice.mill()) {
            throw new RopeworkError("a field splice is not a mill splice");
        }
        HaulRope shorter = new HaulRope(
                rope.serial(), rope.spec(), rope.installed(), rope.length().minus(removed), rope.splices() + 1);
        List<Splice> joined = new ArrayList<>(splices);
        joined.add(splice);
        return of(shorter, joined);
    }

    /**
     * How much has been cut out of the rope since it was a given length.
     *
     * @param original the length it went on at
     * @return the length removed
     */
    public Length totalRemoved(Length original) {
        return original.minus(rope.length());
    }

    private static String feet(Length length) {
        return Decimals.fixed(length.feet(), 0);
    }
}
