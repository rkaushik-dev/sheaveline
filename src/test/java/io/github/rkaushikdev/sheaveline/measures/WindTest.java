package io.github.rkaushikdev.sheaveline.measures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class WindTest {

    /** The quad runs almost due east up the fall line. */
    private static final Bearing LINE = Bearing.ofDegrees(90);

    private static BigDecimal tenths(Speed speed) {
        return Decimals.quantize(speed.milesPerHour(), 1);
    }

    @Test
    void milesAnHourIsNowAFaceOnSpeed() {
        assertThat(Speed.ofMilesPerHour(60).milesPerHour()).isEqualByComparingTo("60");
        assertThat(Speed.parse("35 mph")).isEqualTo(Speed.ofMilesPerHour(35));
        assertThat(Speed.parse("35 miles per hour").formatMph()).isEqualTo("35 mph");
        assertThat(Speed.ofMilesPerHour(60).feetPerMinute()).isEqualByComparingTo("5280");
    }

    @Test
    void aWindCarriesADirectionAndAGust() {
        Wind wind = Wind.ofMph(24, "SW", 38);
        assertThat(wind.speed()).isEqualTo(Speed.ofMilesPerHour(24));
        assertThat(wind.bearing()).isEqualTo(Bearing.ofPoint("SW"));
        assertThat(wind.peak()).isEqualTo(Speed.ofMilesPerHour(38));
    }

    @Test
    void withoutAGustThePeakIsTheSustainedSpeed() {
        Wind wind = Wind.ofMph(18, "W");
        assertThat(wind.gust()).isEmpty();
        assertThat(wind.peak()).isEqualTo(wind.speed());
    }

    @Test
    void aGustBelowTheSustainedSpeedIsRefused() {
        assertThatThrownBy(() -> Wind.ofMph(30, "W", 20)).isInstanceOf(ParseError.class);
    }

    static Stream<Arguments> shorthand() {
        return Stream.of(
                Arguments.of("SW 24G38", 24L, "SW", 38L),
                Arguments.of("sw 24 g 38", 24L, "SW", 38L),
                Arguments.of("W 18", 18L, "W", null),
                Arguments.of("225 24", 24L, "SW", null),
                Arguments.of("270 41G55", 41L, "W", 55L));
    }

    @ParameterizedTest
    @MethodSource("shorthand")
    void parseReadsTheStationShorthand(String text, long mph, String point, Long gust) {
        Wind wind = Wind.parse(text);
        assertThat(wind.speed()).isEqualTo(Speed.ofMilesPerHour(mph));
        assertThat(wind.bearing()).isEqualTo(Bearing.ofPoint(point));
        assertThat(wind.gust()).isEqualTo(Optional.ofNullable(gust).map(Speed::ofMilesPerHour));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "windy", "SW", "24", "NNNE 20"})
    void parseRefusesWhatIsNotAnObservation(String text) {
        assertThatThrownBy(() -> Wind.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void theWindBlowsTheOtherWayFromWhereItComesFrom() {
        assertThat(Wind.ofMph(20, "W").blowingToward()).isEqualTo(Bearing.ofPoint("E"));
    }

    @Test
    void aWindStraightUpTheLineHasNoCrossComponent() {
        Wind wind = Wind.ofMph(30, "W");
        assertThat(tenths(wind.across(LINE))).isEqualByComparingTo("0");
        assertThat(tenths(wind.along(LINE))).isEqualByComparingTo("30");
        assertThat(wind.isHeadwind(LINE)).isFalse();
    }

    @Test
    void aWindOffTheTopIsAHeadwind() {
        Wind wind = Wind.ofMph(30, "E");
        assertThat(wind.isHeadwind(LINE)).isTrue();
        assertThat(tenths(wind.along(LINE))).isEqualByComparingTo("30");
    }

    @Test
    void aWindSquareAcrossTheLineIsAllCrossComponent() {
        Wind wind = Wind.ofMph(30, "S");
        assertThat(tenths(wind.across(LINE))).isEqualByComparingTo("30");
        assertThat(tenths(wind.along(LINE))).isEqualByComparingTo("0");
    }

    @Test
    void aWindOnTheQuarterSplitsBetweenTheTwo() {
        Wind wind = Wind.ofMph(40, "SW");
        assertThat(tenths(wind.across(LINE))).isEqualByComparingTo("28.3");
        assertThat(tenths(wind.along(LINE))).isEqualByComparingTo("28.3");
    }

    @Test
    void theCrossComponentIsReadOffTheGust() {
        Wind wind = Wind.ofMph(20, "S", 45);
        assertThat(tenths(wind.across(LINE))).isEqualByComparingTo("20");
        assertThat(tenths(wind.peakAcross(LINE))).isEqualByComparingTo("45");
    }

    @Test
    void windChillFollowsTheWeatherServiceFormula() {
        Wind wind = Wind.ofMph(20, "W");
        assertThat(Decimals.quantize(wind.chill(Temperature.ofDegreesF(5)).degreesF(), 0))
                .isEqualByComparingTo("-15");
        assertThat(Decimals.quantize(wind.chill(Temperature.ofDegreesF(35)).degreesF(), 0))
                .isEqualByComparingTo("24");
    }

    @Test
    void windChillStandsOffOutsideTheRangeItIsDefinedFor() {
        Temperature warm = Temperature.ofDegreesF(58);
        assertThat(Wind.ofMph(20, "W").chill(warm)).isEqualTo(warm);
        Temperature cold = Temperature.ofDegreesF(5);
        assertThat(Wind.ofMph(2, "W").chill(cold)).isEqualTo(cold);
        assertThat(Wind.CALM.chill(cold)).isEqualTo(cold);
    }

    @Test
    void calmHasNothingInAnyDirection() {
        assertThat(Wind.CALM.across(LINE)).isEqualTo(new Speed(0));
        assertThat(Wind.CALM.along(LINE)).isEqualTo(new Speed(0));
        assertThat(Wind.CALM.peak()).isEqualTo(new Speed(0));
    }

    @Test
    void anObservationPrintsTheWayItArrived() {
        assertThat(Wind.ofMph(24, "SW", 38)).hasToString("SW 24G38");
        assertThat(Wind.ofMph(18, "W")).hasToString("W 18");
        assertThat(Wind.parse("SW 24G38").formatShorthand()).isEqualTo("SW 24G38");
    }
}
