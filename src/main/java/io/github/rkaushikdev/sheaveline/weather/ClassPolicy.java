package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the weather rules need to know about one class of machine.
 *
 * <p>The chill floor is per class because it is about the people rather than the machine. A cabin
 * with a door on it will run in weather that would give somebody frostbite on an open chair, and a
 * surface lift is held to the same figure as a chair because the rider is standing in it.
 *
 * @param gust the strongest gust the class runs in
 * @param crosswind the strongest wind across the line it runs in, when the class is asked about one
 * @param chillFloor the coldest wind chill the riders are put out in
 * @param holdsOnIce whether ice on the grips holds the machine
 */
public record ClassPolicy(Speed gust, Optional<Speed> crosswind, Temperature chillFloor, boolean holdsOnIce) {

    /** A row of the table. */
    public ClassPolicy {
        Objects.requireNonNull(gust, "gust");
        Objects.requireNonNull(crosswind, "crosswind");
        Objects.requireNonNull(chillFloor, "chillFloor");
    }

    /**
     * A row written the way the board and the manufacturers write the limits.
     *
     * @param gustMph the gust limit in miles an hour
     * @param crosswindMph the crosswind limit in miles an hour
     * @param chillFloorF the chill floor in degrees Fahrenheit
     * @param holdsOnIce whether ice holds the machine
     * @return the row
     */
    public static ClassPolicy of(long gustMph, long crosswindMph, long chillFloorF, boolean holdsOnIce) {
        return new ClassPolicy(
                Speed.ofMilesPerHour(gustMph),
                Optional.of(Speed.ofMilesPerHour(crosswindMph)),
                Temperature.ofDegreesF(chillFloorF),
                holdsOnIce);
    }

    /**
     * A row for a class that is never asked about the wind across its line.
     *
     * @param gustMph the gust limit in miles an hour
     * @param chillFloorF the chill floor in degrees Fahrenheit
     * @param holdsOnIce whether ice holds the machine
     * @return the row, with no crosswind figure
     */
    public static ClassPolicy withoutCrosswind(long gustMph, long chillFloorF, boolean holdsOnIce) {
        return new ClassPolicy(
                Speed.ofMilesPerHour(gustMph), Optional.empty(), Temperature.ofDegreesF(chillFloorF), holdsOnIce);
    }

    /**
     * Whether the class is held by wind across its line at all.
     *
     * @return true when the row has a crosswind figure
     */
    public boolean caresAboutCrosswind() {
        return crosswind.isPresent();
    }
}
