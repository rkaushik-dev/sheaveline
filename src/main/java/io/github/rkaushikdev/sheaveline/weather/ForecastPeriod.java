package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What the weather is expected to do over one stretch of the day.
 *
 * @param starts when the stretch begins
 * @param ends when it is over, not part of it
 * @param wind the expected wind
 * @param temperature the expected air temperature
 * @param confidence how far the forecaster trusts it
 * @param visibility the expected visibility
 * @param precipitation what is expected to fall
 * @param rime whether rime is expected on the towers
 */
public record ForecastPeriod(
        LocalDateTime starts,
        LocalDateTime ends,
        Wind wind,
        Temperature temperature,
        Confidence confidence,
        Length visibility,
        Precipitation precipitation,
        boolean rime) {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * A period.
     *
     * @throws WeatherError when it does not run forwards, or the visibility is negative
     */
    public ForecastPeriod {
        Objects.requireNonNull(starts, "starts");
        Objects.requireNonNull(ends, "ends");
        Objects.requireNonNull(wind, "wind");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(precipitation, "precipitation");
        if (!ends.isAfter(starts)) {
            throw new WeatherError("a forecast period runs forwards");
        }
        if (visibility.mils() < 0) {
            throw new WeatherError("visibility is not a negative distance");
        }
    }

    /**
     * A period of moderate confidence on a clear, dry stretch.
     *
     * @param starts when the stretch begins
     * @param ends when it is over
     * @param wind the expected wind
     * @param temperature the expected air temperature
     */
    public ForecastPeriod(LocalDateTime starts, LocalDateTime ends, Wind wind, Temperature temperature) {
        this(
                starts,
                ends,
                wind,
                temperature,
                Confidence.MODERATE,
                Observation.UNLIMITED_VISIBILITY,
                Precipitation.NONE,
                false);
    }

    /**
     * Whether a moment falls inside the stretch.
     *
     * @param moment the moment
     * @return true from the start up to, but not at, the end
     */
    public boolean covers(LocalDateTime moment) {
        return !moment.isBefore(starts) && moment.isBefore(ends);
    }

    /**
     * The same shape the anemometer produces, so the same rules can read it.
     *
     * @param station the station the forecast is for
     * @return a reading taken at the start of the period, with no lightning in it
     */
    public Observation asObservation(Station station) {
        return new Observation(station, starts, wind, temperature, visibility, precipitation, rime, Optional.empty());
    }

    /** Such as {@code 09:00 to 12:00, W 18}. */
    @Override
    public String toString() {
        return starts.format(CLOCK) + " to " + ends.format(CLOCK) + ", " + wind;
    }
}
