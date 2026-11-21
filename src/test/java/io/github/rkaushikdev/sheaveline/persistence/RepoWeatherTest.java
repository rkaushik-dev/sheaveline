package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.persistence.models.ObservationRow;
import io.github.rkaushikdev.sheaveline.persistence.models.StationRow;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.Precipitation;
import io.github.rkaushikdev.sheaveline.weather.Station;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepoWeatherTest {

    private static final Station SUMMIT = new Station("summit", Length.ofFeet(7981));
    private static final Station MID = new Station("mid mountain", Length.ofFeet(7100));
    private static final Station BASE = new Station("base", Length.ofFeet(6400));
    private static final LocalDateTime NOON = LocalDateTime.of(2025, 1, 15, 12, 0);

    private Database database;
    private Session session;

    @BeforeEach
    void openASession() {
        database = TestDatabase.fresh();
        session = database.session();
    }

    @AfterEach
    void closeIt() {
        session.close();
    }

    private static Observation observed(
            Station station, int minute, String wind, long degrees, Optional<Long> lightning, boolean rime) {
        return new Observation(
                station,
                LocalDateTime.of(2025, 1, 15, 12, minute),
                Wind.parse(wind),
                Temperature.ofDegreesF(degrees),
                Length.ofFeet(30000),
                Precipitation.SNOW,
                rime,
                lightning.map(Length::ofFeet));
    }

    private static Observation observed(Station station, int minute, String wind) {
        return observed(station, minute, wind, 8, Optional.empty(), false);
    }

    private static Observation observed(Station station, int minute) {
        return observed(station, minute, "W 18");
    }

    private static Observation observed() {
        return observed(SUMMIT, 0);
    }

    private WeatherRepository stocked() {
        WeatherRepository weather = new WeatherRepository(session);
        for (Station station : List.of(SUMMIT, MID, BASE)) {
            weather.addStation(station);
        }
        session.commit();
        return weather;
    }

    @Test
    void theSchemaCarriesTheWeatherTables() {
        assertThat(database.existingTables()).contains("weather_stations", "observations");
    }

    @Test
    void aStationGoesInOnce() {
        WeatherRepository weather = stocked();
        assertThatThrownBy(() -> weather.addStation(SUMMIT))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is already a station called summit");
    }

    @Test
    void theStationsComeBackInNameOrder() {
        WeatherRepository weather = stocked();
        assertThat(weather.stations().stream().map(Station::name)).containsExactly("base", "mid mountain", "summit");
        assertThat(weather.station("summit").elevation()).isEqualTo(Length.ofFeet(7981));
    }

    @Test
    void aStationThatIsNotThere() {
        assertThatThrownBy(() -> new WeatherRepository(session).station("cirque"))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no station called cirque");
    }

    @Test
    void theNearestStationToAnElevationIsTheOneTheRulesRead() {
        WeatherRepository weather = stocked();
        assertThat(weather.nearestTo(Length.ofFeet(7900)).name()).isEqualTo("summit");
        assertThat(weather.nearestTo(Length.ofFeet(7050)).name()).isEqualTo("mid mountain");
        assertThat(weather.nearestTo(Length.ofFeet(6440)).name()).isEqualTo("base");
    }

    @Test
    void askingTheNearestStationBeforeAnyExist() {
        assertThatThrownBy(() -> new WeatherRepository(session).nearestTo(Length.ofFeet(7000)))
                .isInstanceOf(StorageError.class)
                .hasMessage("no station has been set up yet");
    }

    @Test
    void aReadingAgainstAStationThatIsNotThereIsRefused() {
        WeatherRepository weather = new WeatherRepository(session);
        assertThatThrownBy(() -> weather.record(observed()))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no station called summit");
    }

    @Test
    void aReadingRoundTripsWithEveryField() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 0, "SW 24G38", -12, Optional.of(20000L), true));
        session.commit();
        Observation found = weather.latest(SUMMIT);
        assertThat(found.wind().speed()).isEqualTo(Speed.ofMilesPerHour(24));
        assertThat(found.wind().gust()).contains(Speed.ofMilesPerHour(38));
        assertThat(found.temperature()).isEqualTo(Temperature.ofDegreesF(-12));
        assertThat(found.lightningWithin()).contains(Length.ofFeet(20000));
        assertThat(found.rime()).isTrue();
        assertThat(found.precipitation()).isSameAs(Precipitation.SNOW);
        assertThat(found.station().elevation()).isEqualTo(SUMMIT.elevation());
    }

    @Test
    void aReadingWithNoGustComesBackWithNone() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 0, "W 18"));
        session.commit();
        assertThat(weather.latest(SUMMIT).wind().gust()).isEmpty();
    }

    @Test
    void twoReadingsCannotShareAMomentAtOneStation() {
        WeatherRepository weather = stocked();
        assertRefused(() -> {
            weather.record(observed());
            weather.record(observed());
            session.commit();
        });
    }

    @Test
    void twoStationsMayReportTheSameMoment() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 0));
        weather.record(observed(BASE, 0));
        session.commit();
        assertThat(weather.since(NOON)).hasSize(2);
    }

    @Test
    void aGustBelowTheSustainedWindIsRefusedByTheDatabase() {
        stocked();
        ObservationRow row = new ObservationRow(
                "summit",
                NOON,
                Speed.ofMilesPerHour(30),
                Wind.parse("W 30").bearing(),
                Temperature.ofDegreesF(8),
                Length.ofFeet(30000),
                Precipitation.NONE);
        row.setWindGust(Speed.ofMilesPerHour(20));
        assertRefused(() -> {
            session.persist(row);
            session.commit();
        });
    }

    @Test
    void theLatestReadingIsTheMostRecentOne() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 5, "W 40"));
        weather.record(observed(SUMMIT, 25, "W 10"));
        session.commit();
        assertThat(weather.latest(SUMMIT).wind().speed()).isEqualTo(Speed.ofMilesPerHour(10));
    }

    @Test
    void aStationThatHasReportedNothingHasNoLatest() {
        WeatherRepository weather = stocked();
        assertThatThrownBy(() -> weather.latest(SUMMIT))
                .isInstanceOf(StorageError.class)
                .hasMessage("nothing has been read at summit");
    }

    @Test
    void aStaleReadingIsRefusedRatherThanBelieved() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 5));
        session.commit();
        Observation fresh = weather.latestFresh(SUMMIT, LocalDateTime.of(2025, 1, 15, 12, 20));
        assertThat(fresh.wind().speed()).isEqualTo(Speed.ofMilesPerHour(18));
        assertThatThrownBy(() -> weather.latestFresh(SUMMIT, LocalDateTime.of(2025, 1, 15, 13, 0)))
                .isInstanceOf(StorageError.class)
                .hasMessage("the last reading at summit is stale");
    }

    @Test
    void theStalenessWindowCanBeTightened() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 0));
        session.commit();
        LocalDateTime now = LocalDateTime.of(2025, 1, 15, 12, 10);
        assertThat(weather.latestFresh(SUMMIT, now)).isNotNull();
        assertThatThrownBy(() -> weather.latestFresh(SUMMIT, now, Duration.ofMinutes(5)))
                .isInstanceOf(StorageError.class);
    }

    @Test
    void theReadingsSinceAMomentComeBackInOrder() {
        WeatherRepository weather = stocked();
        for (int minute : new int[] {30, 5, 20}) {
            weather.record(observed(SUMMIT, minute));
        }
        session.commit();
        List<Observation> found = weather.since(LocalDateTime.of(2025, 1, 15, 12, 10));
        assertThat(found.stream().map(one -> one.taken().getMinute())).containsExactly(20, 30);
    }

    @Test
    void aLogCanBeBuiltForOneStationOrForAllOfThem() {
        WeatherRepository weather = stocked();
        weather.record(observed(SUMMIT, 5));
        weather.record(observed(BASE, 10));
        session.commit();
        assertThat(weather.log().size()).isEqualTo(2);
        assertThat(weather.log(SUMMIT).size()).isEqualTo(1);
        assertThat(weather.log().highestStation().name()).isEqualTo("summit");
    }

    @Test
    void aLogWithNothingInItIsAnErrorRatherThanAnEmptyOne() {
        stocked();
        assertThatThrownBy(() -> new WeatherRepository(session).log())
                .isInstanceOf(StorageError.class)
                .hasMessage("there are no readings to build a log from");
    }

    @Test
    void deletingAStationTakesItsReadingsWithIt() {
        WeatherRepository weather = stocked();
        weather.record(observed());
        session.commit();
        StationRow row = session.find(StationRow.class, "summit").orElseThrow();
        session.remove(row);
        session.commit();
        assertThat(weather.since(NOON)).isEmpty();
    }
}
