package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.weather.Station;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A weather station a manager sets up.
 *
 * @param name what it is called
 * @param elevation how high it stands
 */
public record StationIn(@NotNull @Size(min = 1, max = 32) String name, @NotNull @LengthFeet Length elevation) {

    /**
     * The station in the domain's own terms.
     *
     * @return the station
     */
    public Station toDomain() {
        return new Station(name, elevation);
    }
}
