package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Every reading from every station, in the order they arrived.
 *
 * <p>The hold rules read the log rather than one reading, because the question is always which
 * station to believe and whether what it said is still true.
 *
 * @param observations the readings, oldest first
 */
public record ObservationLog(List<Observation> observations) {

    private static final Comparator<Observation> BY_GUST =
            Comparator.comparingLong(seen -> seen.wind().peak().milsPerMinute());
    private static final Comparator<Observation> BY_TEMPERATURE =
            Comparator.comparingLong(seen -> seen.temperature().decidegreesF());

    /**
     * A log.
     *
     * @throws WeatherError when there are no readings
     */
    public ObservationLog {
        observations = List.copyOf(observations);
        if (observations.isEmpty()) {
            throw new WeatherError("a log has readings in it");
        }
    }

    /**
     * A log with its readings put in the order they were taken.
     *
     * @param observations the readings, in any order
     * @return the log
     * @throws WeatherError when there are no readings
     */
    public static ObservationLog of(List<Observation> observations) {
        return new ObservationLog(observations.stream()
                .sorted(Comparator.comparing(Observation::taken))
                .toList());
    }

    /**
     * Every station that has reported, in the order each first reported.
     *
     * @return the stations, each once
     */
    public List<Station> stations() {
        return observations.stream().map(Observation::station).distinct().toList();
    }

    /**
     * The readings from one station.
     *
     * @param station the station
     * @return its readings, oldest first, empty when it has reported nothing
     */
    public List<Observation> fromStation(Station station) {
        return observations.stream()
                .filter(seen -> seen.station().equals(station))
                .toList();
    }

    /**
     * The most recent reading from any station.
     *
     * @return the newest reading
     */
    public Observation latest() {
        return observations.getLast();
    }

    /**
     * The most recent reading from one station.
     *
     * @param station the station
     * @return its newest reading
     * @throws WeatherError when the station has reported nothing
     */
    public Observation latest(Station station) {
        List<Observation> pool = fromStation(station);
        if (pool.isEmpty()) {
            throw new WeatherError("nothing has been read at " + station);
        }
        return pool.getLast();
    }

    /**
     * The most recent reading from any station that is still evidence about now.
     *
     * @param now the moment
     * @return the newest reading that is not stale
     * @throws WeatherError when every reading is stale, or one was taken after the moment
     */
    public Observation latestFresh(LocalDateTime now) {
        return freshest(observations, now).orElseThrow(() -> new WeatherError("no fresh reading at any station"));
    }

    /**
     * The most recent reading from one station that is still evidence about now.
     *
     * @param now the moment
     * @param station the station
     * @return its newest reading that is not stale
     * @throws WeatherError when none is fresh, or one was taken after the moment
     */
    public Observation latestFresh(LocalDateTime now, Station station) {
        return freshest(fromStation(station), now)
                .orElseThrow(() -> new WeatherError("no fresh reading at " + station));
    }

    /**
     * The station highest up the hill. The first to report wins a tie.
     *
     * @return the station
     */
    public Station highestStation() {
        return stations().stream()
                .max(Comparator.comparingLong(station -> station.elevation().mils()))
                .orElseThrow();
    }

    /**
     * The station whose elevation is closest to the point being asked about. The first to report
     * wins a tie.
     *
     * @param elevation the elevation asked about
     * @return the station
     */
    public Station nearestTo(Length elevation) {
        return stations().stream()
                .min(Comparator.comparingLong(
                        station -> station.elevation().minus(elevation).abs().mils()))
                .orElseThrow();
    }

    /**
     * The reading with the strongest peak wind on any station.
     *
     * @return the earliest of the windiest readings
     */
    public Observation worstGust() {
        return observations.stream().max(BY_GUST).orElseThrow();
    }

    /**
     * The reading with the strongest peak wind on one station.
     *
     * @param station the station
     * @return the earliest of its windiest readings
     * @throws NoSuchElementException when the station has reported nothing
     */
    public Observation worstGust(Station station) {
        return fromStation(station).stream().max(BY_GUST).orElseThrow();
    }

    /**
     * The coldest reading on any station.
     *
     * @return the earliest of the coldest readings
     */
    public Observation coldest() {
        return observations.stream().min(BY_TEMPERATURE).orElseThrow();
    }

    /**
     * The coldest reading on one station.
     *
     * @param station the station
     * @return the earliest of its coldest readings
     * @throws NoSuchElementException when the station has reported nothing
     */
    public Observation coldest(Station station) {
        return fromStation(station).stream().min(BY_TEMPERATURE).orElseThrow();
    }

    /**
     * The last reading that put a strike inside a distance, which is where a lightning clock runs
     * from.
     *
     * @param radius how close a strike has to be to count
     * @return the latest such reading, or empty when no reading put one that close
     */
    public Optional<Observation> lastStrikeWithin(Length radius) {
        Optional<Observation> found = Optional.empty();
        for (Observation seen : observations) {
            if (seen.lightningWithin().filter(at -> at.compareTo(radius) <= 0).isPresent()) {
                found = Optional.of(seen);
            }
        }
        return found;
    }

    /**
     * The log with one more reading in it, put in its place by time.
     *
     * @param observation the new reading
     * @return a new log; this one is unchanged
     */
    public ObservationLog withObservation(Observation observation) {
        List<Observation> longer = new ArrayList<>(observations);
        longer.add(observation);
        return of(longer);
    }

    /**
     * How many readings are in the log.
     *
     * @return the count
     */
    public int size() {
        return observations.size();
    }

    private static Optional<Observation> freshest(List<Observation> pool, LocalDateTime now) {
        for (Observation seen : pool.reversed()) {
            if (!seen.isStale(now)) {
                return Optional.of(seen);
            }
        }
        return Optional.empty();
    }
}
