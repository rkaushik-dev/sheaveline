package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.weather.Station;

/**
 * A weather station.
 *
 * @param name what it is called
 * @param elevation how high it stands
 */
public record StationOut(String name, @LengthFeet Length elevation) {

    /**
     * A station as the tablet shows it.
     *
     * @param station the station
     * @return its shape
     */
    public static StationOut of(Station station) {
        return new StationOut(station.name(), station.elevation());
    }
}
