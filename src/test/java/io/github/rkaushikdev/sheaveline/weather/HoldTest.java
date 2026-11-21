package io.github.rkaushikdev.sheaveline.weather;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HoldTest {

    private static final Station SUMMIT = new Station("summit", Length.ofFeet(7965));
    private static final LocalDateTime NOON = LocalDateTime.of(2025, 1, 15, 12, 0);

    // John Paul runs at seventy two degrees, so a wind from the south is square across it.
    private static final String ALONG = "ENE";
    private static final String ACROSS = "SSE";

    /** The summit at noon, west eighteen and eight degrees on a clear dry day, unless told otherwise. */
    private static final class Conditions {
        private String wind = "W 18";
        private long degrees = 8;
        private long visibilityFeet = 30000;
        private Precipitation precipitation = Precipitation.NONE;
        private boolean rime;
        private Optional<Length> lightning = Optional.empty();

        Conditions wind(String text) {
            wind = text;
            return this;
        }

        Conditions degrees(long fahrenheit) {
            degrees = fahrenheit;
            return this;
        }

        Conditions visibility(long feet) {
            visibilityFeet = feet;
            return this;
        }

        Conditions falling(Precipitation what) {
            precipitation = what;
            return this;
        }

        Conditions rime() {
            rime = true;
            return this;
        }

        Conditions lightning(long feet) {
            lightning = Optional.of(Length.ofFeet(feet));
            return this;
        }

        WeatherHold on(Lift lift) {
            Observation seen = new Observation(
                    SUMMIT,
                    NOON,
                    Wind.parse(wind),
                    Temperature.ofDegreesF(degrees),
                    Length.ofFeet(visibilityFeet),
                    precipitation,
                    rime,
                    lightning);
            return new WeatherHold(lift, seen);
        }

        WeatherHold held() {
            return on(johnPaul());
        }
    }

    private static Conditions weather() {
        return new Conditions();
    }

    private static WeatherHold held() {
        return weather().held();
    }

    @Test
    void aQuietDayHoldsNothing() {
        WeatherHold clear = held();
        assertThat(clear.mayRun()).isTrue();
        assertThat(clear.isHeld()).isFalse();
        assertThat(clear.holds()).isEmpty();
        assertThat(clear).hasToString("JP clear");
    }

    @Test
    void aGustOverTheLimitHoldsTheMachine() {
        WeatherHold windy = weather().wind(ALONG + " 30G50").held();
        assertThat(windy.isHeld()).isTrue();
        assertThat(windy.reasons()).contains(HoldReason.GUST);
        assertThat(windy.holds().get(0).detail()).contains("45 mph");
    }

    @Test
    void eachClassCarriesItsOwnGustLimit() {
        assertThat(weather().on(strawberry()).gustLimit()).isEqualTo(Speed.ofMilesPerHour(40));
        assertThat(weather().on(johnPaul()).gustLimit()).isEqualTo(Speed.ofMilesPerHour(45));
        assertThat(weather().on(wildcat()).gustLimit()).isEqualTo(Speed.ofMilesPerHour(50));
    }

    @Test
    void aWindThatStopsATripleWillNotStopATow() {
        String wind = ALONG + " 42";
        assertThat(weather().wind(wind).on(strawberry()).isHeld()).isTrue();
        assertThat(weather().wind(wind).on(wildcat()).mayRun()).isTrue();
    }

    @Test
    void aWindAlongTheLineHasNoCrosswindInIt() {
        WeatherHold along = weather().wind(ALONG + " 34").held();
        assertThat(along.crosswind()).isLessThan(Speed.ofMilesPerHour(5));
        assertThat(along.reasons()).doesNotContain(HoldReason.CROSSWIND);
    }

    @Test
    void theSameWindSquareAcrossTheLineHoldsIt() {
        WeatherHold across = weather().wind(ACROSS + " 34").held();
        assertThat(across.crosswind()).isGreaterThan(Speed.ofMilesPerHour(30));
        assertThat(across.reasons()).contains(HoldReason.CROSSWIND);
    }

    @Test
    void aSurfaceTowIsNotAskedAboutCrosswindAtAll() {
        WeatherHold tow = weather().wind(ACROSS + " 40").on(wildcat());
        assertThat(tow.crosswindLimit()).isEmpty();
        assertThat(tow.reasons()).doesNotContain(HoldReason.CROSSWIND);
    }

    @Test
    void theCrosswindIsReadOffTheGustNotTheSustainedWind() {
        WeatherHold gusting = weather().wind(ACROSS + " 20G40").held();
        assertThat(gusting.reasons()).contains(HoldReason.CROSSWIND);
        WeatherHold steady = weather().wind(ACROSS + " 20").held();
        assertThat(steady.reasons()).doesNotContain(HoldReason.CROSSWIND);
    }

    @Test
    void coldAloneDoesNotHoldALiftButColdAndWindDo() {
        WeatherHold still = weather().wind("W 2").degrees(-20).held();
        assertThat(still.reasons()).doesNotContain(HoldReason.CHILL);
        WeatherHold blowing = weather().wind(ALONG + " 25").degrees(-10).held();
        assertThat(blowing.reasons()).contains(HoldReason.CHILL);
    }

    @Test
    void iceStopsADetachableAndNotAFixedGrip() {
        assertThat(weather().rime().held().reasons()).contains(HoldReason.ICING);
        assertThat(weather().rime().on(strawberry()).reasons()).doesNotContain(HoldReason.ICING);
    }

    @Test
    void freezingRainCountsAsIcingWithoutAnyRimeYet() {
        assertThat(weather().falling(Precipitation.FREEZING_RAIN).held().reasons())
                .contains(HoldReason.ICING);
        assertThat(weather().falling(Precipitation.SNOW).held().reasons()).doesNotContain(HoldReason.ICING);
    }

    @Test
    void lightningInsideFiveMilesHoldsEverything() {
        for (Lift lift : List.of(johnPaul(), strawberry(), wildcat())) {
            WeatherHold strike = weather().lightning(20000).on(lift);
            assertThat(strike.reasons()).contains(HoldReason.LIGHTNING);
        }
    }

    @Test
    void lightningOutsideFiveMilesDoesNot() {
        assertThat(weather().lightning(40000).held().reasons()).doesNotContain(HoldReason.LIGHTNING);
    }

    @Test
    void aLightningHoldRunsOnAClockAndAWindHoldDoesNot() {
        assertThat(weather().lightning(20000).held().needsATimer()).isTrue();
        assertThat(weather().wind(ALONG + " 30G50").held().needsATimer()).isFalse();
        assertThat(HoldReason.GUST.clearsOnReading()).isTrue();
        assertThat(HoldReason.LIGHTNING.clearsOnReading()).isFalse();
    }

    @Test
    void visibilityAtTheTerminalHoldsTheMachine() {
        assertThat(weather().visibility(100).held().reasons()).contains(HoldReason.VISIBILITY);
        assertThat(weather().visibility(400).held().reasons()).doesNotContain(HoldReason.VISIBILITY);
    }

    @Test
    void severalReasonsCanHoldAtOnce() {
        WeatherHold bad = weather()
                .wind(ACROSS + " 30G60")
                .degrees(-15)
                .lightning(10000)
                .visibility(80)
                .held();
        assertThat(bad.reasons()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(bad.isHeld()).isTrue();
        assertThat(bad.toString()).contains("held");
        assertThat(bad).hasToString("JP held: gust, crosswind, wind chill, lightning, visibility");
    }

    @Test
    void aHoldPrintsItsReasonAndItsDetail() {
        WeatherHold windy = weather().wind(ALONG + " 30G50").held();
        assertThat(windy.holds().get(0).toString()).startsWith("gust: ");
        assertThat(windy.holds().get(0)).hasToString("gust: 50 mph against a limit of 45 mph");
    }

    @Test
    void theLimitsComeOffThePolicyTable() {
        assertThat(held().policy()).isSameAs(Policy.forClass(johnPaul().liftClass()));
        assertThat(weather().on(wildcat()).policy().crosswind()).isEmpty();
        assertThat(Policy.TABLE.get(johnPaul().liftClass()).gust()).isEqualTo(Speed.ofMilesPerHour(45));
    }

    @Test
    void everyClassHasARowInTheTable() {
        for (LiftClass liftClass : LiftClass.values()) {
            ClassPolicy row = Policy.forClass(liftClass);
            assertThat(row.gust().milesPerHour()).isPositive();
            assertThat(row.chillFloor().degreesF()).isNegative();
        }
    }

    @Test
    void onlyTheDetachablesHoldOnIceInTheTable() {
        for (LiftClass liftClass : LiftClass.values()) {
            assertThat(Policy.forClass(liftClass).holdsOnIce()).isEqualTo(liftClass.isDetachable());
        }
    }

    @Test
    void aCabinToleratesAColderChillThanAnOpenChair() {
        ClassPolicy cabin = Policy.forClass(LiftClass.DETACHABLE_GONDOLA);
        ClassPolicy chair = Policy.forClass(LiftClass.DETACHABLE_CHAIR);
        assertThat(cabin.chillFloor()).isLessThan(chair.chillFloor());
    }

    @Test
    void theSurfaceTowIsTheOnlyRowWithNoCrosswindFigure() {
        List<LiftClass> without = Arrays.stream(LiftClass.values())
                .filter(one -> !Policy.forClass(one).caresAboutCrosswind())
                .toList();
        assertThat(without).containsExactly(LiftClass.SURFACE_TOW);
    }
}
