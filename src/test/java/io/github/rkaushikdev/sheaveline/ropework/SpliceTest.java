package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpliceTest {

    private static final HaulRope ROPE = johnPaul().rope();
    private static final Length LONG = ROPE.diameter().scaled(1200);
    private static final LocalDate MADE = LocalDate.of(2019, 10, 4);

    private static Splice splice(long stationFeet, boolean mill, Length length, int tucks) {
        return new Splice(Length.ofFeet(stationFeet), MADE, "B Kettner", tucks, length, mill);
    }

    private static Splice splice(long stationFeet, boolean mill) {
        return splice(stationFeet, mill, LONG, 5);
    }

    private static Splice splice(long stationFeet, Length length) {
        return splice(stationFeet, false, length, 5);
    }

    private static Splice splice(long stationFeet) {
        return splice(stationFeet, false);
    }

    private static Splice splice() {
        return splice(0);
    }

    private static SpliceRegister register(Splice... splices) {
        return SpliceRegister.of(ROPE, splices.length == 0 ? List.of(splice(0, true)) : List.of(splices));
    }

    @Test
    void aSpliceChecksItsOwnDescription() {
        assertThatThrownBy(() -> splice(-1)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new Splice(Length.ofFeet(0), MADE, " ", 5, LONG))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> splice(0, false, LONG, 3)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> splice(0, Length.ofFeet(0))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aLongSpliceIsTwelveHundredDiameters() {
        assertThat(splice().minimumLength(ROPE.spec()))
                .isEqualTo(ROPE.diameter().scaled(1200));
        assertThat(splice().isLongEnough(ROPE.spec())).isTrue();
        assertThat(splice(0, Length.ofFeet(50)).isLongEnough(ROPE.spec())).isFalse();
    }

    @Test
    void aSpliceReportsItsAge() {
        assertThat(splice().ageInDays(LocalDate.of(2020, 10, 3))).isEqualTo(365);
        assertThatThrownBy(() -> splice().ageInDays(LocalDate.of(2019, 1, 1))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aSplicePrintsWhichKindItIs() {
        assertThat(splice(0, true)).hasToString("mill splice at 0 ft, 2019-10-04");
        assertThat(splice(3200)).hasToString("field splice at 3200 ft, 2019-10-04");
    }

    @Test
    void aRegisterNeedsExactlyOneMillSplice() {
        assertThatThrownBy(() -> SpliceRegister.of(ROPE, List.of())).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> register(splice(0, false))).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> register(splice(0, true), splice(3200, true))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void twoSplicesCannotShareAStation() {
        assertThatThrownBy(() -> register(splice(0, true), splice(0))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aSplicePastTheEndOfTheRopeIsRefused() {
        assertThatThrownBy(() -> register(splice(0, true), splice(12000))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void theRegisterSortsByStationAndCounts() {
        SpliceRegister held = register(splice(3200), splice(0, true), splice(6400));
        assertThat(held.splices().stream().map(s -> s.station().feet()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(0), BigDecimal.valueOf(3200), BigDecimal.valueOf(6400));
        assertThat(held.count()).isEqualTo(3);
        assertThat(held.fieldSplices()).hasSize(2);
    }

    @Test
    void aSingleSpliceLeavesTheWholeRopeAsOneGap() {
        assertThat(register().separations()).containsExactly(ROPE.length());
    }

    @Test
    void separationsGoRoundTheLoopAndWrapPastTheEnd() {
        SpliceRegister held = register(splice(0, true), splice(4000));
        List<Length> gaps = held.separations();
        assertThat(gaps.get(0)).isEqualTo(Length.ofFeet(4000));
        assertThat(gaps.get(1)).isEqualTo(ROPE.length().minus(Length.ofFeet(4000)));
    }

    @Test
    void splicesHaveToBeKeptApart() {
        assertThat(register().minimumSeparation()).isEqualTo(ROPE.diameter().scaled(3000));
        SpliceRegister close = register(splice(0, true), splice(200));
        assertThat(close.tooClose()).isNotEmpty();
        assertThat(close.isAcceptable()).isFalse();
    }

    @Test
    void aRegisterWithRoomBetweenTheSplicesIsAcceptable() {
        assertThat(register(splice(0, true), splice(4000)).isAcceptable()).isTrue();
    }

    @Test
    void shortSplicesAreListedAndFailTheRegister() {
        SpliceRegister held = register(splice(0, true), splice(4000, Length.ofFeet(60)));
        assertThat(held.shortSplices()).hasSize(1);
        assertThat(held.isAcceptable()).isFalse();
    }

    @Test
    void tooManySplicesFinishesTheRopeHoweverGoodTheyAre() {
        SpliceRegister held = register(splice(0, true), splice(1800), splice(3600), splice(5400), splice(7200));
        assertThat(held.count()).isEqualTo(5);
        assertThat(held.tooClose()).isEmpty();
        assertThat(held.isAcceptable()).isFalse();
    }

    @Test
    void theClearRopeIsMeasuredFromTheEndOfOneSpliceToTheStartOfTheNext() {
        SpliceRegister held = register(splice(0, true), splice(4000));
        assertThat(held.clearRope())
                .containsExactly(
                        Length.ofFeet(4000).minus(LONG),
                        ROPE.length().minus(Length.ofFeet(4000)).minus(LONG));
    }

    @Test
    void twoSplicesFarEnoughApartByStationCanStillCrowdEachOther() {
        Length limit = ROPE.diameter().scaled(3000);
        Splice after = new Splice(limit.plus(Length.ofFeet(10)), MADE, "B Kettner", 5, LONG);
        SpliceRegister held = register(splice(0, true), after);
        assertThat(held.separations().get(0)).isGreaterThan(limit);
        assertThat(held.tooClose()).hasSize(1);
        assertThat(held.isAcceptable()).isFalse();
        assertThat(held.problems()).containsExactly("217 ft clear after the splice at 0 ft, wants 344 ft");
    }

    @Test
    void aRegisterThatPassesHasNoProblems() {
        assertThat(register(splice(0, true), splice(4000)).problems()).isEmpty();
    }

    @Test
    void everyProblemInTheRegisterIsSaidOnItsOwnLine() {
        SpliceRegister held =
                register(splice(0, true), splice(1800), splice(3600), splice(5400, Length.ofFeet(60)), splice(7200));
        assertThat(held.problems())
                .containsExactly("5 splices against a limit of 4", "splice at 5400 ft is 60 ft long, wants 138 ft");
    }

    @Test
    void theRopeFileCondemnsOnceForEachProblem() {
        SpliceRegister held = register(splice(0, true), splice(200), splice(4000, Length.ofFeet(60)));
        RopeFile file = new RopeFile().withSplices(held);
        assertThat(file.findings())
                .extracting(Finding::source)
                .hasSize(held.problems().size())
                .containsOnly("splices");
        assertThat(file.findings()).extracting(Finding::detail).containsExactlyElementsOf(held.problems());
        assertThat(file.verdict()).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void theNearestSpliceToAStationIsFound() {
        SpliceRegister held = register(splice(0, true), splice(4000), splice(8000));
        assertThat(held.nearestTo(Length.ofFeet(3900)).station()).isEqualTo(Length.ofFeet(4000));
        assertThat(held.nearestTo(Length.ofFeet(100)).station()).isEqualTo(Length.ofFeet(0));
    }

    @Test
    void cuttingAPieceOutShortensTheRopeAndAddsASplice() {
        SpliceRegister before = register(splice(0, true));
        SpliceRegister after = before.shortenedBy(Length.ofFeet(40), splice(2000));
        assertThat(after.rope().length()).isEqualTo(ROPE.length().minus(Length.ofFeet(40)));
        assertThat(after.count()).isEqualTo(2);
        assertThat(after.rope().splices()).isEqualTo(2);
        assertThat(after.totalRemoved(ROPE.length())).isEqualTo(Length.ofFeet(40));
    }

    @Test
    void aShorteningRemovesARealLengthAndNotTheWholeRope() {
        SpliceRegister before = register(splice(0, true));
        assertThatThrownBy(() -> before.shortenedBy(Length.ofFeet(0), splice(2000)))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> before.shortenedBy(Length.ofFeet(20000), splice(2000)))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aShorteningIsNeverRecordedAsAMillSplice() {
        SpliceRegister before = register(splice(0, true));
        assertThatThrownBy(() -> before.shortenedBy(Length.ofFeet(40), splice(2000, true)))
                .isInstanceOf(RopeworkError.class);
    }
}
