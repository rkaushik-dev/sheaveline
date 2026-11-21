package io.github.rkaushikdev.sheaveline.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ObservationTest {

    private static final Station SUMMIT = new Station("summit", Length.ofFeet(7965));
    private static final Station MID = new Station("mid mountain", Length.ofFeet(7100));
    private static final Station BASE = new Station("base", Length.ofFeet(6400));

    private static final LocalDateTime NOON = LocalDateTime.of(2025, 1, 15, 12, 0);

    /** A reading at the summit at noon, west eighteen and eight degrees, unless told otherwise. */
    private static final class Reading {
        private Station station = SUMMIT;
        private int minutes;
        private String wind = "W 18";
        private long degrees = 8;
        private long visibilityFeet = 30000;
        private Precipitation precipitation = Precipitation.NONE;
        private boolean rime;
        private Optional<Length> lightning = Optional.empty();

        Reading at(Station where, int minutesPastNoon) {
            station = where;
            minutes = minutesPastNoon;
            return this;
        }

        Reading wind(String text) {
            wind = text;
            return this;
        }

        Reading degrees(long fahrenheit) {
            degrees = fahrenheit;
            return this;
        }

        Reading visibility(long feet) {
            visibilityFeet = feet;
            return this;
        }

        Reading falling(Precipitation what) {
            precipitation = what;
            return this;
        }

        Reading rime() {
            rime = true;
            return this;
        }

        Reading lightning(long feet) {
            lightning = Optional.of(Length.ofFeet(feet));
            return this;
        }

        Observation taken() {
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
    }

    private static Reading reading() {
        return new Reading();
    }

    private static Observation observed() {
        return reading().taken();
    }

    private static Observation observed(Station station, int minutes) {
        return reading().at(station, minutes).taken();
    }

    @Test
    void aStationHasANameAndAnElevation() {
        assertThat(SUMMIT.elevation()).isEqualTo(Length.ofFeet(7965));
        assertThat(SUMMIT).hasToString("summit");
        assertThatThrownBy(() -> new Station("  ", Length.ofFeet(7000)))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a station has a name");
    }

    @Test
    void precipitationSaysWhatItDoesToTheMachine() {
        assertThat(Precipitation.RAIN.wetsTheRope()).isTrue();
        assertThat(Precipitation.FREEZING_RAIN.wetsTheRope()).isTrue();
        assertThat(Precipitation.SNOW.wetsTheRope()).isFalse();
        assertThat(Precipitation.FREEZING_RAIN.buildsIce()).isTrue();
        assertThat(Precipitation.RAIN.buildsIce()).isFalse();
    }

    @Test
    void anObservationCarriesTheChillItImplies() {
        Observation cold = reading().wind("W 20").degrees(5).taken();
        assertThat(cold.chill().degreesF()).isLessThan(cold.temperature().degreesF());
    }

    @Test
    void anObservationChecksItsOwnNumbers() {
        assertThatThrownBy(() -> reading().visibility(-1).taken())
                .isInstanceOf(WeatherError.class)
                .hasMessage("visibility is not a negative distance");
        assertThatThrownBy(() -> reading().lightning(-100).taken())
                .isInstanceOf(WeatherError.class)
                .hasMessage("lightning distance is not negative");
    }

    @Test
    void icingIsRimeOnTheTowersOrFreezingRainOnTheWay() {
        assertThat(reading().rime().taken().icing()).isTrue();
        assertThat(reading().falling(Precipitation.FREEZING_RAIN).taken().icing())
                .isTrue();
        assertThat(reading().falling(Precipitation.SNOW).taken().icing()).isFalse();
    }

    @Test
    void lightningIsRecordedByDistanceOrNotAtAll() {
        assertThat(reading().lightning(26400).taken().hasLightning()).isTrue();
        assertThat(observed().hasLightning()).isFalse();
    }

    @Test
    void anObservationAges() {
        Observation seen = observed(SUMMIT, 0);
        assertThat(seen.ageAt(LocalDateTime.of(2025, 1, 15, 12, 10))).isEqualTo(Duration.ofMinutes(10));
        assertThat(seen.isStale(LocalDateTime.of(2025, 1, 15, 12, 10))).isFalse();
        assertThat(seen.isStale(LocalDateTime.of(2025, 1, 15, 12, 40))).isTrue();
    }

    @Test
    void aMomentBeforeTheReadingIsRefused() {
        Observation later = observed(SUMMIT, 30);
        assertThatThrownBy(() -> later.ageAt(LocalDateTime.of(2025, 1, 15, 12, 0)))
                .isInstanceOf(WeatherError.class)
                .hasMessage("that moment is before the observation was taken");
    }

    @Test
    void theStalenessWindowCanBeTightened() {
        Observation seen = observed(SUMMIT, 0);
        LocalDateTime now = LocalDateTime.of(2025, 1, 15, 12, 10);
        assertThat(seen.isStale(now)).isFalse();
        assertThat(seen.isStale(now, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void anObservationPrintsShort() {
        assertThat(reading().wind("W 18").degrees(8).taken()).hasToString("summit W 18, 8 F");
    }

    @Test
    void aLogNeedsReadings() {
        assertThatThrownBy(() -> ObservationLog.of(List.of()))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a log has readings in it");
    }

    @Test
    void aLogSortsByTimeAndListsItsStations() {
        ObservationLog log = ObservationLog.of(List.of(observed(BASE, 10), observed(SUMMIT, 5), observed(MID, 1)));
        assertThat(log.size()).isEqualTo(3);
        assertThat(log.observations().stream().map(seen -> seen.taken().getMinute()))
                .containsExactly(1, 5, 10);
        assertThat(log.stations()).containsExactlyInAnyOrder(SUMMIT, MID, BASE);
    }

    @Test
    void readingsCanBePulledOutByStation() {
        ObservationLog log = ObservationLog.of(List.of(observed(SUMMIT, 1), observed(BASE, 2)));
        assertThat(log.fromStation(SUMMIT)).hasSize(1);
        assertThat(log.latest(SUMMIT).station()).isSameAs(SUMMIT);
        assertThat(log.latest().station()).isSameAs(BASE);
    }

    @Test
    void askingAStationThatHasReportedNothing() {
        ObservationLog log = ObservationLog.of(List.of(observed(SUMMIT, 1)));
        assertThatThrownBy(() -> log.latest(BASE))
                .isInstanceOf(WeatherError.class)
                .hasMessage("nothing has been read at base");
    }

    @Test
    void theLatestFreshReadingSkipsAStaleOne() {
        ObservationLog log = ObservationLog.of(List.of(
                reading().at(SUMMIT, 5).wind("W 40").taken(),
                reading().at(SUMMIT, 50).wind("W 10").taken()));
        LocalDateTime now = LocalDateTime.of(2025, 1, 15, 12, 55);
        assertThat(log.latestFresh(now, SUMMIT).wind().speed().milesPerHour()).isEqualByComparingTo("10");
    }

    @Test
    void aLogOfNothingFreshIsAnErrorRatherThanAGuess() {
        ObservationLog log = ObservationLog.of(List.of(observed(SUMMIT, 5)));
        assertThatThrownBy(() -> log.latestFresh(LocalDateTime.of(2025, 1, 15, 14, 0), SUMMIT))
                .isInstanceOf(WeatherError.class)
                .hasMessage("no fresh reading at summit");
    }

    @Test
    void theHighestStationIsFound() {
        ObservationLog log = ObservationLog.of(List.of(observed(BASE, 1), observed(SUMMIT, 2)));
        assertThat(log.highestStation()).isSameAs(SUMMIT);
    }

    @Test
    void theStationNearestAnElevationIsTheOneTheRulesRead() {
        ObservationLog log = ObservationLog.of(List.of(observed(BASE, 1), observed(MID, 2), observed(SUMMIT, 3)));
        assertThat(log.nearestTo(Length.ofFeet(7900))).isSameAs(SUMMIT);
        assertThat(log.nearestTo(Length.ofFeet(7000))).isSameAs(MID);
        assertThat(log.nearestTo(Length.ofFeet(6420))).isSameAs(BASE);
    }

    @Test
    void theWorstGustAndTheColdestReadingAreFound() {
        ObservationLog log = ObservationLog.of(List.of(
                reading().at(SUMMIT, 1).wind("W 20G30").degrees(10).taken(),
                reading().at(SUMMIT, 2).wind("W 25").degrees(-4).taken()));
        assertThat(log.worstGust().wind().peak().milesPerHour()).isEqualByComparingTo("30");
        assertThat(log.coldest().temperature().degreesF()).isEqualByComparingTo("-4");
    }

    @Test
    void addingAReadingMakesANewLog() {
        ObservationLog log = ObservationLog.of(List.of(observed(SUMMIT, 1)));
        ObservationLog longer = log.withObservation(observed(SUMMIT, 2));
        assertThat(log.size()).isEqualTo(1);
        assertThat(longer.size()).isEqualTo(2);
    }
}
