package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.Precipitation;
import io.github.rkaushikdev.sheaveline.weather.Station;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A reading an operator files from a station.
 *
 * @param station the station's name
 * @param taken when it was read
 * @param wind the sustained wind
 * @param bearing where the wind comes from
 * @param gust the gust, when there was one
 * @param temperature the air temperature
 * @param visibility how far can be seen, unlimited when not sent
 * @param precipitation what is falling, nothing when not sent
 * @param rime whether rime is building on the towers, no when not sent
 * @param lightningWithin how far away the nearest strike was, when there was one
 */
public record ObservationIn(
        @NotNull @Size(min = 1) String station,
        @NotNull LocalDateTime taken,
        @NotNull @WindText Speed wind,
        @NotNull @BearingText Bearing bearing,
        @WindText Optional<Speed> gust,
        @NotNull @TemperatureText Temperature temperature,
        @LengthFeet @Schema(type = "string", examples = "4800.00 ft", defaultValue = UNLIMITED_VISIBILITY)
                Length visibility,
        Precipitation precipitation,
        boolean rime,
        @LengthFeet Optional<Length> lightningWithin) {

    /**
     * The visibility a reading has when the client does not send one, written the way a client
     * would send it: {@link Observation#UNLIMITED_VISIBILITY} in feet and inches. It is read through
     * the same parser every other value goes through.
     */
    public static final String UNLIMITED_VISIBILITY = "30000' 0\"";

    /** A reading, with the defaults for what was not sent. */
    public ObservationIn {
        gust = gust == null ? Optional.empty() : gust;
        visibility = visibility == null ? Length.parse(UNLIMITED_VISIBILITY) : visibility;
        precipitation = precipitation == null ? Precipitation.NONE : precipitation;
        lightningWithin = lightningWithin == null ? Optional.empty() : lightningWithin;
    }

    /**
     * The reading in the domain's own terms.
     *
     * @param at the station it was read at
     * @return the reading
     * @throws io.github.rkaushikdev.sheaveline.MeasureError when the gust is below the sustained wind
     */
    public Observation toDomain(Station at) {
        return new Observation(
                at,
                taken,
                new Wind(wind, bearing, gust),
                temperature,
                visibility,
                precipitation,
                rime,
                lightningWithin);
    }
}
