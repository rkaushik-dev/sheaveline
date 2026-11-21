package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.persistence.models.ObservationRow;
import io.github.rkaushikdev.sheaveline.persistence.models.StationRow;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.ObservationLog;
import io.github.rkaushikdev.sheaveline.weather.Station;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Weather stations and everything they have reported. */
public final class WeatherRepository extends Repository {

    /**
     * The weather log in a session.
     *
     * @param session the session
     */
    public WeatherRepository(Session session) {
        super(session);
    }

    /**
     * Set a station up.
     *
     * @param station the station
     * @throws StorageError when there is already a station with its name
     */
    public void addStation(Station station) {
        if (find(StationRow.class, station.name()).isPresent()) {
            throw new StorageError("there is already a station called " + station.name());
        }
        session().persist(new StationRow(station.name(), station.elevation()));
    }

    /**
     * A station by its name.
     *
     * @param name the name
     * @return the station
     * @throws StorageError when there is no such station
     */
    public Station station(String name) {
        StationRow row = stationRow(name);
        return new Station(row.getName(), row.getElevation());
    }

    /**
     * Every station, in name order.
     *
     * @return the stations
     */
    public List<Station> stations() {
        return many(query("select s from StationRow s order by s.name", StationRow.class)).stream()
                .map(row -> new Station(row.getName(), row.getElevation()))
                .toList();
    }

    /**
     * The station whose elevation is closest to the point being asked about. Of two as close, the
     * first by name.
     *
     * @param elevation the elevation
     * @return the station
     * @throws StorageError when no station has been set up
     */
    public Station nearestTo(Length elevation) {
        List<Station> found = stations();
        if (found.isEmpty()) {
            throw new StorageError("no station has been set up yet");
        }
        return found.stream()
                .min(Comparator.comparingLong(
                        one -> Math.abs(one.elevation().minus(elevation).mils())))
                .orElseThrow();
    }

    /**
     * File a reading against the station that sent it.
     *
     * @param observation the reading
     * @throws StorageError when its station has not been set up
     */
    public void record(Observation observation) {
        StationRow station = stationRow(observation.station().name());
        Wind wind = observation.wind();
        ObservationRow row = new ObservationRow(
                station.getName(),
                observation.taken(),
                wind.speed(),
                wind.bearing(),
                observation.temperature(),
                observation.visibility(),
                observation.precipitation());
        row.setWindGust(wind.gust().orElse(null));
        row.setRime(observation.rime());
        row.setLightningWithin(observation.lightningWithin().orElse(null));
        station.addObservation(row);
        session().persist(row);
    }

    /**
     * Every reading at or after a moment, across the hill, oldest first.
     *
     * @param moment the moment
     * @return the readings
     */
    public List<Observation> since(LocalDateTime moment) {
        return since(moment, Optional.empty());
    }

    /**
     * Every reading at one station at or after a moment, oldest first.
     *
     * @param moment the moment
     * @param station the station
     * @return the readings
     */
    public List<Observation> since(LocalDateTime moment, Station station) {
        return since(moment, Optional.of(station));
    }

    private List<Observation> since(LocalDateTime moment, Optional<Station> station) {
        String statement = "select o from ObservationRow o where o.taken >= :moment"
                + station.map(any -> " and o.stationName = :name").orElse("")
                + " order by o.taken";
        TypedQuery<ObservationRow> query =
                query(statement, ObservationRow.class).setParameter("moment", moment);
        station.ifPresent(wanted -> query.setParameter("name", wanted.name()));
        return many(query).stream().map(WeatherRepository::toDomain).toList();
    }

    /**
     * The most recent reading at a station, however old.
     *
     * @param station the station
     * @return the reading
     * @throws StorageError when the station has reported nothing
     */
    public Observation latest(Station station) {
        return toDomain(one(
                query(
                                "select o from ObservationRow o where o.stationName = :name order by o.taken desc",
                                ObservationRow.class)
                        .setParameter("name", station.name()),
                "nothing has been read at " + station.name()));
    }

    /**
     * The most recent reading that is still evidence about now, by the usual staleness window.
     *
     * @param station the station
     * @param now the moment it is
     * @return the reading
     * @throws StorageError when the station has reported nothing, or its last reading is stale
     */
    public Observation latestFresh(Station station, LocalDateTime now) {
        return latestFresh(station, now, Observation.STALE_AFTER);
    }

    /**
     * The most recent reading that is still evidence about now.
     *
     * @param station the station
     * @param now the moment it is
     * @param after how old a reading can be before it is stale
     * @return the reading
     * @throws StorageError when the station has reported nothing, or its last reading is stale
     */
    public Observation latestFresh(Station station, LocalDateTime now, Duration after) {
        Observation seen = latest(station);
        if (seen.isStale(now, after)) {
            throw new StorageError("the last reading at " + station.name() + " is stale");
        }
        return seen;
    }

    /**
     * Every reading across the hill, as a log.
     *
     * @return the log, oldest first
     * @throws StorageError when there are no readings, since a log is never empty
     */
    public ObservationLog log() {
        return log(Optional.empty());
    }

    /**
     * Every reading at one station, as a log.
     *
     * @param station the station
     * @return the log, oldest first
     * @throws StorageError when the station has no readings, since a log is never empty
     */
    public ObservationLog log(Station station) {
        return log(Optional.of(station));
    }

    private ObservationLog log(Optional<Station> station) {
        String statement = "select o from ObservationRow o"
                + station.map(any -> " where o.stationName = :name").orElse("")
                + " order by o.taken";
        TypedQuery<ObservationRow> query = query(statement, ObservationRow.class);
        station.ifPresent(wanted -> query.setParameter("name", wanted.name()));
        List<ObservationRow> rows = many(query);
        if (rows.isEmpty()) {
            throw new StorageError("there are no readings to build a log from");
        }
        return ObservationLog.of(rows.stream().map(WeatherRepository::toDomain).toList());
    }

    private StationRow stationRow(String name) {
        return fetch(StationRow.class, name, "there is no station called " + name);
    }

    private static Observation toDomain(ObservationRow row) {
        StationRow station = row.getStation();
        return new Observation(
                new Station(station.getName(), station.getElevation()),
                row.getTaken(),
                new Wind(row.getWindSpeed(), row.getWindBearing(), row.getWindGust()),
                row.getTemperature(),
                row.getVisibility(),
                row.getPrecipitation(),
                row.isRime(),
                row.getLightningWithin());
    }
}
