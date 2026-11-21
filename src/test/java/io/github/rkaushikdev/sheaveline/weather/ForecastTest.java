package io.github.rkaushikdev.sheaveline.weather;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ForecastTest {

    private static final Station SUMMIT = new Station("summit", Length.ofFeet(7981));
    private static final LocalDateTime ISSUED = LocalDateTime.of(2025, 1, 15, 5, 0);

    // John Paul runs at seventy two degrees, so a wind from the south is across it.
    private static final String ALONG = "ENE";
    private static final String ACROSS = "SSE";

    private static LocalDateTime hour(int hour) {
        return LocalDateTime.of(2025, 1, 15, hour, 0);
    }

    private static ForecastPeriod period(
            int startHour, int endHour, String wind, long degrees, Confidence confidence, boolean rime) {
        return new ForecastPeriod(
                hour(startHour),
                hour(endHour),
                Wind.parse(wind),
                Temperature.ofDegreesF(degrees),
                confidence,
                Observation.UNLIMITED_VISIBILITY,
                Precipitation.NONE,
                rime);
    }

    private static ForecastPeriod period(int startHour, int endHour, String wind, long degrees) {
        return period(startHour, endHour, wind, degrees, Confidence.MODERATE, false);
    }

    private static ForecastPeriod period(int startHour, int endHour, String wind) {
        return period(startHour, endHour, wind, 8);
    }

    private static ForecastPeriod period(int startHour, int endHour) {
        return period(startHour, endHour, ALONG + " 18");
    }

    private static ForecastPeriod period(int startHour, int endHour, Confidence confidence) {
        return period(startHour, endHour, ALONG + " 18", 8, confidence, false);
    }

    private static ForecastPeriod period() {
        return period(9, 12);
    }

    private static Forecast forecast(ForecastPeriod... periods) {
        return Forecast.of(
                SUMMIT, ISSUED, "mountain forecaster", periods.length == 0 ? List.of(period()) : List.of(periods));
    }

    private static Set<HoldReason> reasonsIn(Forecast held, ForecastPeriod period, Lift lift) {
        return held.holdsIn(period, lift).stream().map(Hold::reason).collect(Collectors.toSet());
    }

    private static List<Integer> startHours(List<ForecastPeriod> periods) {
        return periods.stream().map(one -> one.starts().getHour()).toList();
    }

    @Test
    void aPeriodRunsForwards() {
        assertThatThrownBy(() -> new ForecastPeriod(hour(12), hour(9), Wind.parse("W 18"), Temperature.ofDegreesF(8)))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a forecast period runs forwards");
    }

    @Test
    void aPeriodCoversItsOwnStretch() {
        ForecastPeriod one = period(9, 12);
        assertThat(one.covers(hour(9))).isTrue();
        assertThat(one.covers(LocalDateTime.of(2025, 1, 15, 11, 59))).isTrue();
        assertThat(one.covers(hour(12))).isFalse();
    }

    @Test
    void aPeriodPrintsShort() {
        assertThat(period(9, 12, "W 18")).hasToString("09:00 to 12:00, W 18");
    }

    @Test
    void aForecastIsSignedAndHasPeriods() {
        assertThatThrownBy(() -> Forecast.of(SUMMIT, ISSUED, "  ", List.of(period())))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a forecast names who issued it");
        assertThatThrownBy(() -> Forecast.of(SUMMIT, ISSUED, "forecaster", List.of()))
                .isInstanceOf(WeatherError.class)
                .hasMessage("a forecast has periods in it");
    }

    @Test
    void overlappingPeriodsAreRefused() {
        assertThatThrownBy(() -> forecast(period(9, 13), period(12, 15)))
                .isInstanceOf(WeatherError.class)
                .hasMessage("two forecast periods overlap");
    }

    @Test
    void thePeriodsComeBackInOrderWhicheverWayTheyAreGiven() {
        Forecast held = forecast(period(15, 18), period(9, 12), period(12, 15));
        assertThat(startHours(held.periods())).containsExactly(9, 12, 15);
        assertThat(held.size()).isEqualTo(3);
    }

    @Test
    void thePeriodCoveringAMomentIsFound() {
        Forecast held = forecast(period(9, 12), period(12, 15));
        Optional<ForecastPeriod> found = held.at(hour(13));
        assertThat(found).isPresent();
        assertThat(found.get().starts().getHour()).isEqualTo(12);
        assertThat(held.at(hour(20))).isEmpty();
    }

    @Test
    void theWindiestAndColdestPeriodsAreFound() {
        Forecast held = forecast(period(9, 12, ALONG + " 18", 20), period(12, 15, ALONG + " 30G55", -8));
        assertThat(held.windiest().starts().getHour()).isEqualTo(12);
        assertThat(held.coldest().starts().getHour()).isEqualTo(12);
    }

    @Test
    void aQuietForecastHoldsNothing() {
        Lift lift = johnPaul();
        Forecast held = forecast(period(9, 12));
        assertThat(held.expectedHolds(lift)).isEmpty();
        assertThat(held.firstHold(lift)).isEmpty();
        assertThat(held.clearPeriods(lift)).hasSize(1);
    }

    @Test
    void aWindyPeriodIsExpectedToHoldTheMachine() {
        Lift lift = johnPaul();
        Forecast held = forecast(period(9, 12), period(12, 15, ALONG + " 30G60"));
        assertThat(startHours(held.expectedHolds(lift))).containsExactly(12);
        Optional<ForecastPeriod> first = held.firstHold(lift);
        assertThat(first).isPresent();
        assertThat(first.get().starts().getHour()).isEqualTo(12);
    }

    @Test
    void theForecastIsJudgedByTheSameRulesAsAReading() {
        Lift lift = johnPaul();
        Forecast held = forecast(period(9, 12, ACROSS + " 20G50"));
        Set<HoldReason> reasons = reasonsIn(held, held.periods().get(0), lift);
        assertThat(reasons).contains(HoldReason.GUST);
        assertThat(reasons).contains(HoldReason.CROSSWIND);
    }

    @Test
    void aWindThatHoldsAQuadMayNotHoldATow() {
        ForecastPeriod windy = period(9, 12, ACROSS + " 30G48");
        Forecast held = forecast(windy);
        assertThat(held.expectedHolds(johnPaul())).isNotEmpty();
        assertThat(held.expectedHolds(wildcat())).isEmpty();
    }

    @Test
    void theTimeTheMachineIsNextExpectedToRun() {
        Lift lift = johnPaul();
        Forecast held = forecast(
                period(9, 12, ALONG + " 30G60"), period(12, 15, ALONG + " 30G60"), period(15, 18, ALONG + " 12"));
        Optional<LocalDateTime> clear = held.clearAfter(lift, hour(10));
        assertThat(clear).contains(hour(15));
    }

    @Test
    void aMachineAlreadyClearIsClearFromNow() {
        Lift lift = johnPaul();
        Forecast held = forecast(period(9, 12), period(12, 15, ALONG + " 30G60"));
        assertThat(held.clearAfter(lift, hour(10))).contains(hour(10));
    }

    @Test
    void aForecastThatNeverClearsSaysNothingRatherThanGuessing() {
        Lift lift = johnPaul();
        Forecast held = forecast(period(9, 12, ALONG + " 30G60"));
        assertThat(held.clearAfter(lift, hour(10))).isEmpty();
    }

    @Test
    void aLowConfidencePeriodIsNotWorthPlanningAgainst() {
        Forecast held = forecast(period(9, 12, Confidence.HIGH), period(12, 15, Confidence.LOW));
        assertThat(startHours(held.worthActingOn())).containsExactly(9);
        assertThat(Confidence.MODERATE.worthPlanningAgainst()).isTrue();
        assertThat(Confidence.LOW.worthPlanningAgainst()).isFalse();
    }

    @Test
    void aForecastSaysWhatItCovers() {
        Forecast held = forecast(period(9, 12), period(12, 15));
        Forecast.Coverage covered = held.covers();
        assertThat(covered.starts().getHour()).isEqualTo(9);
        assertThat(covered.ends().getHour()).isEqualTo(15);
    }

    @Test
    void aForecastAges() {
        Forecast held = forecast();
        assertThat(held.ageAt(hour(11))).isEqualTo(6);
        assertThatThrownBy(() -> held.ageAt(hour(4)))
                .isInstanceOf(WeatherError.class)
                .hasMessage("that moment is before the forecast was issued");
    }

    @Test
    void aPeriodTurnsIntoTheShapeTheAnemometerProduces() {
        ForecastPeriod one = period(9, 12, "W 24G38", -4);
        Observation seen = one.asObservation(SUMMIT);
        assertThat(seen.station()).isSameAs(SUMMIT);
        assertThat(seen.taken()).isEqualTo(one.starts());
        assertThat(seen.wind().peak()).isEqualTo(one.wind().peak());
        assertThat(seen.temperature()).isEqualTo(one.temperature());
    }

    @Test
    void icingInAForecastReachesTheHoldEngine() {
        Lift lift = johnPaul();
        Forecast held = forecast(period(9, 12, ALONG + " 18", 8, Confidence.MODERATE, true));
        assertThat(reasonsIn(held, held.periods().get(0), lift)).contains(HoldReason.ICING);
    }
}
