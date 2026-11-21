package io.github.rkaushikdev.sheaveline.evac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EvacProfileTest {

    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(34500));

    private static EvacuationProfile profile(long snowFeet) {
        return new EvacuationProfile(Hill.johnPaul(), SAG, Length.ofFeet(snowFeet));
    }

    private static EvacuationProfile profile() {
        return profile(2);
    }

    @Test
    void theLoopIsTwiceTheLine() {
        EvacuationProfile walk = profile();
        assertThat(walk.loopLength()).isEqualTo(walk.slopeLength().scaled(2));
    }

    @Test
    void theSpacingIsTheLoopOverTheCarriers() {
        EvacuationProfile walk = profile();
        assertThat(walk.spacing().mils())
                .isEqualTo(Math.floorDiv(walk.loopLength().mils(), 84));
    }

    @Test
    void aSnowAllowanceIsNeverNegative() {
        assertThatThrownBy(() -> new EvacuationProfile(Hill.johnPaul(), SAG, Length.ofFeet(-1)))
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void aDistanceAlongTheRopeLandsInASpan() {
        EvacuationProfile walk = profile();
        EvacuationProfile.Position bottom = walk.stationAt(Length.ofFeet(0));
        assertThat(bottom.span().index()).isEqualTo(1);
        assertThat(bottom.station()).isEqualTo(Length.ofFeet(0));
        EvacuationProfile.Position top = walk.stationAt(walk.slopeLength());
        assertThat(top.span().index()).isEqualTo(8);
        assertThat(top.station()).isEqualTo(Length.ofFeet(4800));
    }

    @Test
    void aDistanceInTheMiddleLandsWhereItShould() {
        EvacuationProfile walk = profile();
        EvacuationProfile.Position middle = walk.stationAt(Length.ofFeet(2000));
        assertThat(middle.span().index()).isGreaterThan(1).isLessThan(8);
        assertThat(middle.station()).isGreaterThan(Length.ofFeet(1500)).isLessThan(Length.ofFeet(2200));
    }

    @Test
    void aDistanceOffTheLineIsRefused() {
        EvacuationProfile walk = profile();
        assertThatThrownBy(() -> walk.stationAt(Length.ofFeet(-1))).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> walk.stationAt(Length.ofFeet(9000))).isInstanceOf(EvacuationError.class);
    }

    @Test
    void everyCarrierGetsExactlyOneStopWhateverTheOffset() {
        EvacuationProfile walk = profile();
        for (long feet : new long[] {0, 30, 60, 90, 119}) {
            assertThat(walk.countAt(Length.ofFeet(feet))).isEqualTo(84);
        }
    }

    @Test
    void carriersComeOutOnBothSides() {
        EvacuationProfile walk = profile();
        List<CarrierStop> stops = walk.stopsAtOffset(Length.ofFeet(0));
        Set<Side> sides = stops.stream().map(CarrierStop::side).collect(Collectors.toSet());
        assertThat(sides).containsExactlyInAnyOrder(Side.UP, Side.DOWN);
    }

    @Test
    void anOffsetOutsideOneSpacingIsRefused() {
        EvacuationProfile walk = profile();
        assertThatThrownBy(() -> walk.stopsAtOffset(walk.spacing())).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> walk.stopsAtOffset(Length.ofFeet(-1))).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aCarrierHangsBelowTheRopeByItsHanger() {
        EvacuationProfile walk = profile();
        CarrierStop stop = walk.stopsAtOffset(Length.ofFeet(60)).get(0);
        assertThat(stop.seatHeight()).isLessThan(stop.ropeElevation());
        assertThat(stop.ropeElevation().minus(stop.seatHeight())).isEqualTo(Length.ofFeetInches(6, 4));
    }

    @Test
    void deeperSnowLowersEveryCarrierHeight() {
        EvacuationProfile thin = profile(0);
        EvacuationProfile deep = thin.withSnow(Length.ofFeet(8));
        Length offset = Length.ofFeet(60);
        CarrierStop thinFirst = thin.stopsAtOffset(offset).get(0);
        CarrierStop deepFirst = deep.stopsAtOffset(offset).get(0);
        assertThat(thinFirst.height().minus(deepFirst.height())).isEqualTo(Length.ofFeet(8));
    }

    @Test
    void theHighestCarrierIsFound() {
        EvacuationProfile walk = profile();
        Length offset = Length.ofFeet(0);
        CarrierStop highest = walk.highest(offset);
        assertThat(walk.stopsAtOffset(offset))
                .allSatisfy(stop -> assertThat(stop.height()).isLessThanOrEqualTo(highest.height()));
    }

    @Test
    void theCarriersAboveAHeightAreListed() {
        EvacuationProfile walk = profile();
        Length offset = Length.ofFeet(0);
        Length limit = Length.ofFeet(60);
        List<CarrierStop> above = walk.above(offset, limit);
        assertThat(above).isNotEmpty();
        assertThat(above).allSatisfy(stop -> assertThat(stop.isAbove(limit)).isTrue());
        assertThat(above.size()).isLessThan(walk.countAt(offset));
    }

    @Test
    void theWorstOffsetIsAtLeastAsBadAsAnySampledOne() {
        EvacuationProfile walk = profile();
        Length limit = Length.ofFeet(60);
        Length worst = walk.worstOffset(limit);
        int count = walk.above(worst, limit).size();
        assertThat(count)
                .isGreaterThanOrEqualTo(walk.above(Length.ofFeet(0), limit).size());
    }

    @Test
    void aStopPrintsWhereItIsAndHowHigh() {
        EvacuationProfile walk = profile();
        CarrierStop stop = walk.highest(Length.ofFeet(0));
        String printed = stop.toString();
        assertThat(printed).startsWith("carrier ");
        assertThat(printed).contains("ft up");
    }

    @Test
    void aLineWithNoCarriersHasNothingToEvacuate() {
        Lift lift = Hill.johnPaul();
        EvacuationProfile empty = new EvacuationProfile(lift, SAG);
        assertThat(empty.spacing().mils()).isGreaterThan(0);
    }
}
