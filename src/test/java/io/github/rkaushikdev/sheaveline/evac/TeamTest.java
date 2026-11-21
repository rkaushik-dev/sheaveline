package io.github.rkaushikdev.sheaveline.evac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TeamTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(34500));
    private static final List<String> CREW = List.of("R Diaz", "T Woo", "J Marsh");
    private static final LocalDate UNTIL = LocalDate.of(2025, 12, 1);

    /** The kit the tests vary one piece of at a time; a null length means the piece is not carried. */
    private static RescueKit kit(Long ladderFeet, Long ropeFeet, int seats, int harnesses, int radios) {
        return new RescueKit(feet(ladderFeet), feet(ropeFeet), seats, harnesses, radios);
    }

    private static Optional<Length> feet(Long feet) {
        return Optional.ofNullable(feet).map(Length::ofFeet);
    }

    private static RescueKit kit(Long ladderFeet, Long ropeFeet) {
        return kit(ladderFeet, ropeFeet, 2, 3, 1);
    }

    private static RescueKit kit() {
        return kit(24L, 120L);
    }

    private static RescueKit equipment(int seats, int harnesses, int radios) {
        return kit(24L, 120L, seats, harnesses, radios);
    }

    private static RescueTeam team(String name, List<String> members, long staged, LocalDate until, RescueKit kit) {
        return RescueTeam.of(name, members, kit, Length.ofFeet(staged), until);
    }

    private static RescueTeam team() {
        return team("alpha", CREW, 0, UNTIL, kit());
    }

    private static RescueTeam team(String name) {
        return team(name, CREW, 0, UNTIL, kit());
    }

    private static RescueTeam team(RescueKit kit) {
        return team("alpha", CREW, 0, UNTIL, kit);
    }

    private static List<CarrierStop> stops() {
        EvacuationProfile walk = new EvacuationProfile(Hill.johnPaul(), SAG, Length.ofFeet(2));
        return walk.stopsAtOffset(Length.ofFeet(0));
    }

    private static CarrierStop lowestStop() {
        return stops().stream()
                .min(Comparator.comparingLong(stop -> stop.height().mils()))
                .orElseThrow();
    }

    private static CarrierStop highestStop() {
        return stops().stream()
                .max(Comparator.comparingLong(stop -> stop.height().mils()))
                .orElseThrow();
    }

    @Test
    void aLadderReachesLessThanItsLength() {
        assertThat(kit(24L, null).ladderReach()).isEqualTo(Length.ofFeet(20));
        assertThat(kit(null, null).ladderReach().isZero()).isTrue();
        assertThat(kit(3L, null).ladderReach().isZero()).isTrue();
    }

    @Test
    void theReachIsTheLongerOfTheTwo() {
        assertThat(kit(24L, 120L).reach()).isEqualTo(Length.ofFeet(120));
        assertThat(kit(60L, null).reach()).isEqualTo(Length.ofFeet(56));
    }

    @Test
    void aKitWithNoLengthOnAPieceIsRefused() {
        assertThatThrownBy(() -> new RescueKit(Optional.of(Length.ofFeet(0)), Optional.empty(), 0, 0, 0))
                .isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> new RescueKit(Optional.empty(), Optional.of(Length.ofFeet(0)), 0, 0, 0))
                .isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> new RescueKit(Optional.empty(), Optional.empty(), -1, 0, 0))
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void aKitSaysWhetherTheLadderAloneWillDo() {
        RescueKit small = kit(24L, 120L);
        assertThat(small.needsARopeFor(Length.ofFeet(18))).isFalse();
        assertThat(small.needsARopeFor(Length.ofFeet(40))).isTrue();
    }

    @Test
    void aKitIsCompleteOnlyWithAHarnessEachAndARadio() {
        assertThat(equipment(1, 3, 1).isComplete(3)).isTrue();
        assertThat(equipment(1, 2, 1).isComplete(3)).isFalse();
        assertThat(equipment(1, 3, 0).isComplete(3)).isFalse();
        assertThat(equipment(0, 3, 1).isComplete(3)).isFalse();
    }

    @Test
    void aKitReportsWhatItCarries() {
        RescueKit full = kit();
        assertThat(full.carries(KitItem.LADDER)).isTrue();
        assertThat(full.carries(KitItem.ROPE_SYSTEM)).isTrue();
        assertThat(full.carries(KitItem.RADIO)).isTrue();
        assertThat(kit(null, 120L).carries(KitItem.LADDER)).isFalse();
        assertThat(KitItem.LADDER.reaches()).isTrue();
        assertThat(KitItem.RADIO.reaches()).isFalse();
    }

    @Test
    void aKitPrintsItsReach() {
        assertThat(kit(24L, 120L, 2, 3, 1)).hasToString("reach 120 ft, 2 seats");
    }

    @Test
    void aTeamNeedsANameAndTwoPeople() {
        assertThatThrownBy(() -> team("  ")).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> team("alpha", List.of("R Diaz"), 0, UNTIL, kit()))
                .isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> team("alpha", List.of("R Diaz", "R Diaz"), 0, UNTIL, kit()))
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void aTeamIsCurrentUntilItsTrainingLapses() {
        assertThat(team("alpha", CREW, 0, LocalDate.of(2025, 12, 1), kit()).isCurrent(ON))
                .isTrue();
        assertThat(team("alpha", CREW, 0, LocalDate.of(2024, 12, 1), kit()).isCurrent(ON))
                .isFalse();
    }

    @Test
    void aTeamWithAShortKitIsNotEquipped() {
        assertThat(team().isEquipped()).isTrue();
        assertThat(team(equipment(2, 1, 1)).isEquipped()).isFalse();
    }

    @Test
    void aTeamCanTakeACarrierItCanReach() {
        CarrierStop low = lowestStop();
        assertThat(team().canReach(low)).isTrue();
        assertThat(team().mayWork(low, ON)).isTrue();
    }

    @Test
    void aTeamThatCannotReachSaysSoAndSaysWhy() {
        CarrierStop high = highestStop();
        RescueTeam shorter = team(kit(16L, 20L));
        assertThat(shorter.canReach(high)).isFalse();
        List<String> reasons = shorter.refusals(high, ON);
        assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("reaches"));
    }

    @Test
    void aLapsedTeamIsRefusedHoweverGoodItsKitIs() {
        CarrierStop low = lowestStop();
        RescueTeam lapsed = team("alpha", CREW, 0, LocalDate.of(2024, 12, 1), kit());
        assertThat(lapsed.mayWork(low, ON)).isFalse();
        assertThat(lapsed.refusals(low, ON))
                .anySatisfy(reason -> assertThat(reason).contains("training lapsed"));
    }

    @Test
    void aTeamShortOfKitIsRefusedHoweverCurrentItIs() {
        CarrierStop low = lowestStop();
        RescueTeam thin = team(equipment(2, 1, 1));
        assertThat(thin.mayWork(low, ON)).isFalse();
        assertThat(thin.refusals(low, ON))
                .anySatisfy(reason -> assertThat(reason).contains("kit is short"));
    }

    @Test
    void aTeamThatCanDoTheJobHasNothingAgainstIt() {
        assertThat(team().refusals(lowestStop(), ON)).isEmpty();
    }

    @Test
    void aTeamReportsHowFarItIsFromAStation() {
        RescueTeam staged = team("alpha", CREW, 2400, UNTIL, kit());
        assertThat(staged.distanceTo(Length.ofFeet(3000))).isEqualTo(Length.ofFeet(600));
        assertThat(staged.distanceTo(Length.ofFeet(1800))).isEqualTo(Length.ofFeet(600));
    }

    @Test
    void aTeamPrintsItsSizeAndItsReach() {
        assertThat(team()).hasToString("alpha, 3 up, reach 120 ft, 2 seats");
    }

    @Test
    void aPoolNeedsTeamsAndUniqueNames() {
        assertThatThrownBy(() -> TeamPool.of(List.of())).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> TeamPool.of(List.of(team("alpha"), team("alpha"))))
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void aPoolSeparatesTheCurrentTeamsFromTheLapsedOnes() {
        TeamPool pool = TeamPool.of(List.of(team("alpha"), team("bravo", CREW, 0, LocalDate.of(2024, 6, 1), kit())));
        assertThat(pool.size()).isEqualTo(2);
        assertThat(pool.current(ON)).extracting(RescueTeam::name).containsExactly("alpha");
        assertThat(pool.lapsed(ON)).extracting(RescueTeam::name).containsExactly("bravo");
        assertThat(pool.people(ON)).isEqualTo(3);
    }

    @Test
    void theNearestAbleTeamIsTheOneThatGetsTheJob() {
        CarrierStop low = lowestStop();
        RescueTeam far = team("base", CREW, 0, UNTIL, kit());
        RescueTeam near = team("summit", CREW, 4800, UNTIL, kit());
        TeamPool pool = TeamPool.of(List.of(far, near));
        RescueTeam chosen = pool.nearestAble(low, ON);
        assertThat(chosen.distanceTo(low.station())).isLessThanOrEqualTo(far.distanceTo(low.station()));
    }

    @Test
    void aCarrierNobodyCanReachIsAnErrorRatherThanAGuess() {
        CarrierStop high = highestStop();
        TeamPool pool = TeamPool.of(List.of(team(kit(12L, null))));
        assertThat(pool.able(high, ON)).isEmpty();
        assertThatThrownBy(() -> pool.nearestAble(high, ON)).isInstanceOf(EvacuationError.class);
    }

    @Test
    void thePoolReportsItsDeepestReach() {
        TeamPool pool = TeamPool.of(
                List.of(team("a", CREW, 0, UNTIL, kit(24L, 80L)), team("b", CREW, 0, UNTIL, kit(24L, 160L))));
        assertThat(pool.deepestReach()).isEqualTo(Length.ofFeet(160));
    }
}
