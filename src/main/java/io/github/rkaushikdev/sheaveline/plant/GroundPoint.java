package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Objects;

/**
 * One shot from the survey: a station and the ground elevation there.
 *
 * @param station where along the centreline
 * @param elevation the ground elevation
 */
public record GroundPoint(Length station, Length elevation) {

    /** A survey shot, nothing left null. */
    public GroundPoint {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(elevation, "elevation");
    }

    /**
     * A shot written in whole feet.
     *
     * @param station feet along the centreline
     * @param elevation feet above datum
     * @return the shot
     */
    public static GroundPoint atFeet(long station, long elevation) {
        return new GroundPoint(Length.ofFeet(station), Length.ofFeet(elevation));
    }

    /**
     * A shot written in feet as digits.
     *
     * @param station feet along the centreline, such as {@code "600"}
     * @param elevation feet above datum, such as {@code "6480.5"}
     * @return the shot
     */
    public static GroundPoint atFeet(String station, String elevation) {
        return new GroundPoint(Length.ofFeet(station), Length.ofFeet(elevation));
    }
}
