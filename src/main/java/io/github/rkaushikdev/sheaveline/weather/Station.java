package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Objects;

/**
 * One weather station, and how high up the hill it sits.
 *
 * <p>There are three stations on this mountain and they disagree, which is the point of having
 * three. The base reads eighteen degrees and calm while the summit reads minus four and gusting
 * forty, and a lift that runs between them is held or not held by whichever station is nearer the
 * exposed end of it.
 *
 * @param name what the station is called
 * @param elevation how high it sits
 */
public record Station(String name, Length elevation) {

    /**
     * A station.
     *
     * @throws WeatherError when the name is blank
     */
    public Station {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(elevation, "elevation");
        if (name.strip().isEmpty()) {
            throw new WeatherError("a station has a name");
        }
    }

    /** The station's name. */
    @Override
    public String toString() {
        return name;
    }
}
