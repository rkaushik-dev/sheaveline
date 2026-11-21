package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One reading off one station.
 *
 * <p>An observation goes stale. A wind reading from forty minutes ago is not evidence about the
 * wind now, and a rule that reads it as though it were is worse than a rule with no reading at all,
 * because it looks like it worked.
 *
 * @param station the station that took it
 * @param taken when it was taken
 * @param wind the wind
 * @param temperature the air temperature
 * @param visibility how far can be seen
 * @param precipitation what is falling
 * @param rime whether rime is building on the towers
 * @param lightningWithin how far away the nearest strike was, when there was one
 */
public record Observation(
        Station station,
        LocalDateTime taken,
        Wind wind,
        Temperature temperature,
        Length visibility,
        Precipitation precipitation,
        boolean rime,
        Optional<Length> lightningWithin) {

    /** A reading older than this is no longer evidence about now. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(20);

    /** The visibility a station reports when nothing limits it. */
    public static final Length UNLIMITED_VISIBILITY = Length.ofFeet(30000);

    /**
     * A reading.
     *
     * @throws WeatherError when the visibility or the lightning distance is negative
     */
    public Observation {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(wind, "wind");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(precipitation, "precipitation");
        Objects.requireNonNull(lightningWithin, "lightningWithin");
        if (visibility.mils() < 0) {
            throw new WeatherError("visibility is not a negative distance");
        }
        if (lightningWithin.isPresent() && lightningWithin.get().mils() < 0) {
            throw new WeatherError("lightning distance is not negative");
        }
    }

    /**
     * A reading on a clear, dry day with no lightning about.
     *
     * @param station the station that took it
     * @param taken when it was taken
     * @param wind the wind
     * @param temperature the air temperature
     */
    public Observation(Station station, LocalDateTime taken, Wind wind, Temperature temperature) {
        this(station, taken, wind, temperature, UNLIMITED_VISIBILITY, Precipitation.NONE, false, Optional.empty());
    }

    /**
     * What the wind makes the temperature feel like.
     *
     * @return the wind chill
     */
    public Temperature chill() {
        return wind.chill(temperature);
    }

    /**
     * How old the reading is at a moment.
     *
     * @param now the moment
     * @return the whole seconds since it was taken
     * @throws WeatherError when the moment is before the reading
     */
    public Duration ageAt(LocalDateTime now) {
        if (now.isBefore(taken)) {
            throw new WeatherError("that moment is before the observation was taken");
        }
        return Duration.ofSeconds(ChronoUnit.SECONDS.between(taken, now));
    }

    /**
     * Whether the reading is too old to say anything about now.
     *
     * @param now the moment
     * @return true once it is older than {@link #STALE_AFTER}
     */
    public boolean isStale(LocalDateTime now) {
        return isStale(now, STALE_AFTER);
    }

    /**
     * Whether the reading is too old to say anything about now, against a window of the caller's.
     *
     * @param now the moment
     * @param after how old a reading may be and still count
     * @return true once it is older than that
     */
    public boolean isStale(LocalDateTime now, Duration after) {
        return ageAt(now).compareTo(after) > 0;
    }

    /**
     * Whether a strike was recorded at any distance.
     *
     * @return true when there is a lightning distance
     */
    public boolean hasLightning() {
        return lightningWithin.isPresent();
    }

    /**
     * Rime on the towers, or freezing rain that will put it there.
     *
     * @return true for either
     */
    public boolean icing() {
        return rime || precipitation.buildsIce();
    }

    /** Such as {@code summit W 18, 8 F}. */
    @Override
    public String toString() {
        return station + " " + wind + ", " + temperature.formatF(0);
    }
}
