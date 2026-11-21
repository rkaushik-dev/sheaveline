package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.RopeSpec;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One splice, where it is and who made it.
 *
 * @param station where along the rope, counted from the mill splice
 * @param made the day it was made
 * @param madeBy who made it
 * @param tucks tucks a strand
 * @param length the length of rope the splice takes up
 * @param mill whether this is the splice made when the rope went on
 */
public record Splice(Length station, LocalDate made, String madeBy, int tucks, Length length, boolean mill) {

    /** A long splice is at least this many rope diameters long. */
    public static final long LONG_SPLICE_DIAMETERS = 1200;

    /**
     * A splice that could have been made.
     *
     * @throws RopeworkError when the station is behind the mill splice, it is unsigned, it has fewer
     *     than four tucks, or it has no length
     */
    public Splice {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(made, "made");
        Objects.requireNonNull(madeBy, "madeBy");
        Objects.requireNonNull(length, "length");
        if (station.mils() < 0) {
            throw new RopeworkError("a splice station is measured from the mill splice, forwards");
        }
        if (madeBy.strip().isEmpty()) {
            throw new RopeworkError("a splice is signed by whoever made it");
        }
        if (tucks < 4) {
            throw new RopeworkError("a long splice carries at least four tucks a strand");
        }
        if (length.mils() <= 0) {
            throw new RopeworkError("a splice has a length");
        }
    }

    /**
     * A field splice, made after the rope went on.
     *
     * @param station where along the rope
     * @param made the day it was made
     * @param madeBy who made it
     * @param tucks tucks a strand
     * @param length the length it takes up
     */
    public Splice(Length station, LocalDate made, String madeBy, int tucks, Length length) {
        this(station, made, madeBy, tucks, length, false);
    }

    /**
     * The shortest a splice in this rope may be.
     *
     * @param spec the rope specification
     * @return twelve hundred nominal diameters
     */
    public Length minimumLength(RopeSpec spec) {
        return spec.nominalDiameter().scaled(LONG_SPLICE_DIAMETERS);
    }

    /**
     * Whether the splice is long enough for the rope.
     *
     * @param spec the rope specification
     * @return true at the minimum length or longer
     */
    public boolean isLongEnough(RopeSpec spec) {
        return length.compareTo(minimumLength(spec)) >= 0;
    }

    /**
     * Days since the splice was made.
     *
     * @param on the day asked about
     * @return whole days
     * @throws RopeworkError when the day is before the splice was made
     */
    public long ageInDays(LocalDate on) {
        if (on.isBefore(made)) {
            throw new RopeworkError(on + " is before the splice was made");
        }
        return ChronoUnit.DAYS.between(made, on);
    }

    /** Such as {@code field splice at 3200 ft, 2019-10-04}. */
    @Override
    public String toString() {
        String kind = mill ? "mill splice" : "field splice";
        return kind + " at " + Decimals.fixed(station.feet(), 0) + " ft, " + made;
    }
}
