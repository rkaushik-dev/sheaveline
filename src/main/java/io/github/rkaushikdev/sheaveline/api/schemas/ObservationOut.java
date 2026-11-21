package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A reading, with what follows from it worked out.
 *
 * @param station the station's name
 * @param taken when it was read
 * @param wind the sustained wind
 * @param bearing where the wind comes from
 * @param gust the gust, when there was one
 * @param peak the gust, or the sustained wind when there was none
 * @param temperature the air temperature
 * @param chill what the wind makes it feel like
 * @param visibility how far can be seen
 * @param precipitation what is falling
 * @param icing whether ice is building on the line
 * @param lightningWithin how far away the nearest strike was, when there was one
 */
public record ObservationOut(
        String station,
        LocalDateTime taken,
        @WindText Speed wind,
        @BearingText Bearing bearing,
        @WindText Optional<Speed> gust,
        @WindText Speed peak,
        @TemperatureText Temperature temperature,
        @TemperatureText Temperature chill,
        @LengthFeet Length visibility,
        String precipitation,
        boolean icing,
        @LengthFeet Optional<Length> lightningWithin) {

    /**
     * A reading as the tablet shows it.
     *
     * @param seen the reading
     * @return its shape
     */
    public static ObservationOut of(Observation seen) {
        return new ObservationOut(
                seen.station().name(),
                seen.taken(),
                seen.wind().speed(),
                seen.wind().bearing(),
                seen.wind().gust(),
                seen.wind().peak(),
                seen.temperature(),
                seen.chill(),
                seen.visibility(),
                seen.precipitation().value(),
                seen.icing(),
                seen.lightningWithin());
    }
}
