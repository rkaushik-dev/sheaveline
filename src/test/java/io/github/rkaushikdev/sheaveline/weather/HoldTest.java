package io.github.rkaushikdev.sheaveline.weather;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static io.github.rkaushikdev.sheaveline.Summit.ACROSS;
import static io.github.rkaushikdev.sheaveline.Summit.ALONG;
import static io.github.rkaushikdev.sheaveline.Summit.reading;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class HoldTest {

    private static WeatherHold held() {
        return reading().held();
    }

    @Test
    void aGustUnderTheLimitButInsideTheMarginIsNotYetClear() {
        WeatherHold near = reading().wind("W 20G43").held();
        assertThat(near.mayRun()).isTrue();
        assertThat(near.clears(HoldReason.GUST)).isFalse();
        assertThat(WeatherHold.GUST.clears(near)).isFalse();
        assertThat(reading().wind("W 20G40").held().clears(HoldReason.GUST)).isTrue();
        assertThat(reading().wind("W 20G50").held().clears(HoldReason.GUST)).isFalse();
    }

    @Test
    void theChillAndTheVisibilityReopenAtTheirOwnLimits() {
        assertThat(reading().wind("W 2").degrees(-25).held().clears(HoldReason.CHILL))
                .isTrue();
        assertThat(reading().visibility(200).held().clears(HoldReason.VISIBILITY))
                .isTrue();
        assertThat(reading().visibility(199).held().clears(HoldReason.VISIBILITY))
                .isFalse();
    }

    @Test
    void aClassNotAskedAboutCrosswindIsAlwaysClearOfIt() {
        WeatherHold tow = reading().wind(ACROSS + " 45").on(wildcat());
        assertThat(WeatherHold.CROSSWIND.check(tow)).isEmpty();
        assertThat(tow.clears(HoldReason.CROSSWIND)).isTrue();
    }

    @Test
    void aGustHoldSaysTheFigureAndTheLimit() {
        assertThat(WeatherHold.GUST.check(reading().wind("W 20G50").held()))
                .contains(new Hold(HoldReason.GUST, "50 mph against a limit of 45 mph"));
        assertThat(WeatherHold.GUST.reopensAt().apply(Speed.ofMilesPerHour(45))).isEqualTo(Speed.ofMilesPerHour(40));
        assertThat(WeatherHold.GUST.reopensAt().apply(Speed.ofMilesPerHour(3))).isEqualTo(Speed.STOPPED);
    }

    @Test
    void aGustInsideTheMarginRunsTheMachineSlow() {
        WeatherHold near = reading().wind("W 20G43").held();
        assertThat(near.mayRun()).isTrue();
        assertThat(near.runsSlow()).isTrue();
        assertThat(near.slowings()).containsExactly(new Hold(HoldReason.GUST, "43 mph against a limit of 45 mph"));
        assertThat(near.slowSpeed()).contains(johnPaul().designSpeed().scaled("0.7"));
        assertThat(near).hasToString("JP slow: gust");
    }

    @Test
    void theSlowedSpeedIsSevenTenthsOfTheDesignSpeedForEveryMachine() {
        for (Lift lift : List.of(johnPaul(), strawberry(), wildcat())) {
            assertThat(Policy.slowedSpeed(lift))
                    .as(lift.code().toString())
                    .isEqualTo(lift.designSpeed().scaled("0.7"));
        }
        assertThat(reading().wind("W 20G43").held().slowSpeed()).contains(Policy.slowedSpeed(johnPaul()));
    }

    @Test
    void aMachineWellInsideItsLimitsRunsAtSpeed() {
        WeatherHold calm = reading().wind("W 20G40").held();
        assertThat(calm.runsSlow()).isFalse();
        assertThat(calm.slowings()).isEmpty();
        assertThat(calm.slowSpeed()).isEmpty();
        assertThat(calm).hasToString("JP clear");
    }

    @Test
    void aHeldMachineIsNotAlsoSlowed() {
        WeatherHold gale = reading().wind(ACROSS + " 30G43").held();
        assertThat(gale.isHeld()).isTrue();
        assertThat(gale.reasons()).containsExactly(HoldReason.CROSSWIND);
        assertThat(gale.slowings()).isEmpty();
        assertThat(gale.runsSlow()).isFalse();
    }

    @Test
    void aCrosswindInsideTheMarginSlowsTheMachineOnItsOwn() {
        WeatherHold across = reading().wind(ACROSS + " 29").held();
        assertThat(across.isHeld()).isFalse();
        assertThat(across.slowings()).extracting(Hold::reason).containsExactly(HoldReason.CROSSWIND);
        assertThat(WeatherHold.GUST.near(across)).isEmpty();
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
        WeatherHold windy = reading().wind(ALONG + " 30G50").held();
        assertThat(windy.isHeld()).isTrue();
        assertThat(windy.reasons()).contains(HoldReason.GUST);
        assertThat(windy.holds().get(0).detail()).contains("45 mph");
    }

    @Test
    void eachClassCarriesItsOwnGustLimit() {
        assertThat(reading().on(strawberry()).gustLimit()).isEqualTo(Speed.ofMilesPerHour(40));
        assertThat(reading().on(johnPaul()).gustLimit()).isEqualTo(Speed.ofMilesPerHour(45));
        assertThat(reading().on(wildcat()).gustLimit()).isEqualTo(Speed.ofMilesPerHour(50));
    }

    @Test
    void aWindThatStopsATripleWillNotStopATow() {
        String wind = ALONG + " 42";
        assertThat(reading().wind(wind).on(strawberry()).isHeld()).isTrue();
        assertThat(reading().wind(wind).on(wildcat()).mayRun()).isTrue();
    }

    @Test
    void aWindAlongTheLineHasNoCrosswindInIt() {
        WeatherHold along = reading().wind(ALONG + " 34").held();
        assertThat(along.crosswind()).isLessThan(Speed.ofMilesPerHour(5));
        assertThat(along.reasons()).doesNotContain(HoldReason.CROSSWIND);
    }

    @Test
    void theSameWindSquareAcrossTheLineHoldsIt() {
        WeatherHold across = reading().wind(ACROSS + " 34").held();
        assertThat(across.crosswind()).isGreaterThan(Speed.ofMilesPerHour(30));
        assertThat(across.reasons()).contains(HoldReason.CROSSWIND);
    }

    @Test
    void aSurfaceTowIsNotAskedAboutCrosswindAtAll() {
        WeatherHold tow = reading().wind(ACROSS + " 40").on(wildcat());
        assertThat(tow.crosswindLimit()).isEmpty();
        assertThat(tow.reasons()).doesNotContain(HoldReason.CROSSWIND);
    }

    @Test
    void theCrosswindIsReadOffTheGustNotTheSustainedWind() {
        WeatherHold gusting = reading().wind(ACROSS + " 20G40").held();
        assertThat(gusting.reasons()).contains(HoldReason.CROSSWIND);
        WeatherHold steady = reading().wind(ACROSS + " 20").held();
        assertThat(steady.reasons()).doesNotContain(HoldReason.CROSSWIND);
    }

    @Test
    void coldAloneDoesNotHoldALiftButColdAndWindDo() {
        WeatherHold still = reading().wind("W 2").degrees(-20).held();
        assertThat(still.reasons()).doesNotContain(HoldReason.CHILL);
        WeatherHold blowing = reading().wind(ALONG + " 25").degrees(-10).held();
        assertThat(blowing.reasons()).contains(HoldReason.CHILL);
    }

    @Test
    void iceStopsADetachableAndNotAFixedGrip() {
        assertThat(reading().rime().held().reasons()).contains(HoldReason.ICING);
        assertThat(reading().rime().on(strawberry()).reasons()).doesNotContain(HoldReason.ICING);
    }

    @Test
    void freezingRainCountsAsIcingWithoutAnyRimeYet() {
        assertThat(reading().falling(Precipitation.FREEZING_RAIN).held().reasons())
                .contains(HoldReason.ICING);
        assertThat(reading().falling(Precipitation.SNOW).held().reasons()).doesNotContain(HoldReason.ICING);
    }

    @Test
    void lightningInsideFiveMilesHoldsEverything() {
        for (Lift lift : List.of(johnPaul(), strawberry(), wildcat())) {
            WeatherHold strike = reading().lightning(20000).on(lift);
            assertThat(strike.reasons()).contains(HoldReason.LIGHTNING);
        }
    }

    @Test
    void lightningOutsideFiveMilesDoesNot() {
        assertThat(reading().lightning(40000).held().reasons()).doesNotContain(HoldReason.LIGHTNING);
    }

    @Test
    void aLightningHoldRunsOnAClockAndAWindHoldDoesNot() {
        assertThat(reading().lightning(20000).held().needsATimer()).isTrue();
        assertThat(reading().wind(ALONG + " 30G50").held().needsATimer()).isFalse();
        assertThat(HoldReason.GUST.clearsOnReading()).isTrue();
        assertThat(HoldReason.LIGHTNING.clearsOnReading()).isFalse();
    }

    @Test
    void freezingFogHoldsAMachineThatHoldsOnIceAndSaysWhy() {
        WeatherHold fog = reading().visibility(1500).degrees(26).held();
        assertThat(fog.reasons()).containsExactly(HoldReason.ICING);
        assertThat(fog.holds()).containsExactly(new Hold(HoldReason.ICING, "freezing fog riming the grips"));
        assertThat(reading().rime().held().holds()).containsExactly(new Hold(HoldReason.ICING, "ice on the grips"));
        assertThat(reading().visibility(1500).degrees(26).on(strawberry()).isHeld())
                .isFalse();
    }

    @Test
    void visibilityAtTheTerminalHoldsTheMachine() {
        assertThat(reading().visibility(100).held().reasons()).contains(HoldReason.VISIBILITY);
        assertThat(reading().visibility(400).held().reasons()).doesNotContain(HoldReason.VISIBILITY);
    }

    @Test
    void severalReasonsCanHoldAtOnce() {
        WeatherHold bad = reading()
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
        WeatherHold windy = reading().wind(ALONG + " 30G50").held();
        assertThat(windy.holds().get(0).toString()).startsWith("gust: ");
        assertThat(windy.holds().get(0)).hasToString("gust: 50 mph against a limit of 45 mph");
    }

    @Test
    void theLimitsComeOffThePolicyTable() {
        assertThat(held().policy()).isSameAs(Policy.forClass(johnPaul().liftClass()));
        assertThat(reading().on(wildcat()).policy().crosswind()).isEmpty();
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
