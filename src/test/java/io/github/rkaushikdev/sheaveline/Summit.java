package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.Precipitation;
import io.github.rkaushikdev.sheaveline.weather.Station;
import io.github.rkaushikdev.sheaveline.weather.WeatherHold;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The hill's weather stations, and a reading off them built up a field at a time.
 *
 * <p>Every reading starts as the summit at noon on a clear dry day, west eighteen and eight degrees,
 * so a test changes only the weather it is about.
 */
public final class Summit {

    /** The station at the top of the hill. */
    public static final Station SUMMIT = new Station("summit", Length.ofFeet(7965));

    /** The station halfway up. */
    public static final Station MID = new Station("mid mountain", Length.ofFeet(7100));

    /** The station by the base lodge. */
    public static final Station BASE = new Station("base", Length.ofFeet(6400));

    /** Noon on the day every reading here is taken. */
    public static final LocalDateTime NOON = LocalDateTime.of(2025, 1, 15, 12, 0);

    /** John Paul runs at seventy two degrees, so a wind from the east north east is along it. */
    public static final String ALONG = "ENE";

    /** And a wind from the south south east is square across it. */
    public static final String ACROSS = "SSE";

    private Summit() {}

    /**
     * The quiet reading, ready to be changed.
     *
     * @return the reading
     */
    public static Reading reading() {
        return new Reading();
    }

    /** A reading built up a field at a time. */
    public static final class Reading {
        private Station station = SUMMIT;
        private int minutes;
        private String wind = "W 18";
        private long degrees = 8;
        private long visibilityFeet = 30000;
        private Precipitation precipitation = Precipitation.NONE;
        private boolean rime;
        private Optional<Length> lightning = Optional.empty();

        private Reading() {}

        /**
         * Taken at another station, some minutes past noon.
         *
         * @param where the station
         * @param minutesPastNoon under an hour, or noon itself
         * @return this reading
         */
        public Reading at(Station where, int minutesPastNoon) {
            station = where;
            minutes = minutesPastNoon;
            return this;
        }

        /**
         * The wind, in the station's shorthand.
         *
         * @param text such as {@code SSE 30G60}
         * @return this reading
         */
        public Reading wind(String text) {
            wind = text;
            return this;
        }

        /**
         * The air temperature.
         *
         * @param fahrenheit whole degrees
         * @return this reading
         */
        public Reading degrees(long fahrenheit) {
            degrees = fahrenheit;
            return this;
        }

        /**
         * How far can be seen.
         *
         * @param feet the distance
         * @return this reading
         */
        public Reading visibility(long feet) {
            visibilityFeet = feet;
            return this;
        }

        /**
         * What is falling.
         *
         * @param what the precipitation
         * @return this reading
         */
        public Reading falling(Precipitation what) {
            precipitation = what;
            return this;
        }

        /**
         * Rime building on the towers.
         *
         * @return this reading
         */
        public Reading rime() {
            rime = true;
            return this;
        }

        /**
         * A strike at some distance.
         *
         * @param feet how far off
         * @return this reading
         */
        public Reading lightning(long feet) {
            lightning = Optional.of(Length.ofFeet(feet));
            return this;
        }

        /**
         * The reading itself.
         *
         * @return the observation
         */
        public Observation taken() {
            return new Observation(
                    station,
                    minutes < 60 ? NOON.plusMinutes(minutes) : NOON,
                    Wind.parse(wind),
                    Temperature.ofDegreesF(degrees),
                    Length.ofFeet(visibilityFeet),
                    precipitation,
                    rime,
                    lightning);
        }

        /**
         * A machine read against the reading.
         *
         * @param lift the machine
         * @return the decision
         */
        public WeatherHold on(Lift lift) {
            return new WeatherHold(lift, taken());
        }

        /**
         * John Paul read against the reading.
         *
         * @return the decision
         */
        public WeatherHold held() {
            return on(Hill.johnPaul());
        }
    }
}
