package io.github.rkaushikdev.sheaveline.evac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StagingTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(34500));
    private static final List<String> CREW = List.of("R Diaz", "T Woo", "J Marsh");
    private static final LocalDate UNTIL = LocalDate.of(2025, 12, 1);
    private static final Length NOWHERE = Length.ofFeet(0);

    private static EvacuationProfile profile() {
        return new EvacuationProfile(Hill.johnPaul(), SAG, Length.ofFeet(2));
    }

    /** A team of the usual crew; a null length means the piece is not carried. */
    private static RescueTeam team(String name, Long ladder, Long rope, long staged, LocalDate until) {
        RescueKit kit = new RescueKit(
                Optional.ofNullable(ladder).map(Length::ofFeet),
                Optional.ofNullable(rope).map(Length::ofFeet),
                2,
                3,
                1);
        return RescueTeam.of(name, CREW, kit, Length.ofFeet(staged), until);
    }

    private static RescueTeam team(String name) {
        return team(name, 24L, 200L, 0, UNTIL);
    }

    private static RescueTeam team() {
        return team("alpha");
    }

    private static RescueTeam team(String name, long staged) {
        return team(name, 24L, 200L, staged, UNTIL);
    }

    private static RescueTeam team(String name, LocalDate until) {
        return team(name, 24L, 200L, 0, until);
    }

    private static RescueTeam team(String name, Long ladder, Long rope) {
        return team(name, ladder, rope, 0, UNTIL);
    }

    private static StagingPlan staged(int seats, RescueTeam... teams) {
        TeamPool pool = TeamPool.of(teams.length == 0 ? List.of(team()) : List.of(teams));
        return Staging.stage(profile(), pool, Length.ofFeet(0), ON, seats);
    }

    private static StagingPlan staged(RescueTeam... teams) {
        return staged(3, teams);
    }

    @Test
    void oneTeamWalksTheWholeLine() {
        StagingPlan plan = staged(team());
        assertThat(plan.size()).isEqualTo(1);
        assertThat(plan.stops()).hasSize(84);
        assertThat(plan.isWorkable()).isTrue();
    }

    @Test
    void theMiddleOfARunIsBetweenItsEnds() {
        List<CarrierStop> stops = profile().stopsAtOffset(NOWHERE).subList(0, 5);
        Length middle = Staging.stagingPoint(stops);
        List<Length> stations = stops.stream().map(CarrierStop::station).toList();
        assertThat(middle)
                .isGreaterThanOrEqualTo(
                        stations.stream().min(Comparator.naturalOrder()).orElseThrow())
                .isLessThanOrEqualTo(
                        stations.stream().max(Comparator.naturalOrder()).orElseThrow());
    }

    @Test
    void anEmptyRunHasNoMiddle() {
        assertThatThrownBy(() -> Staging.stagingPoint(List.of())).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aBeatWithNothingInItIsRefused() {
        assertThatThrownBy(() -> new Beat(team(), List.of(), NOWHERE, 3)).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aBeatWithNobodyOnTheLineIsRefused() {
        List<CarrierStop> stops = profile().stopsAtOffset(NOWHERE).subList(0, 2);
        assertThatThrownBy(() -> new Beat(team(), stops, NOWHERE, 0)).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aSecondTeamCutsTheWorstShareDown() {
        StagingPlan alone = staged(team("alpha"));
        StagingPlan pair = staged(team("alpha"), team("bravo"));
        assertThat(pair.size()).isEqualTo(2);
        assertThat(pair.busiest().work()).isLessThan(alone.busiest().work());
        assertThat(pair.clearsIn()).isLessThan(alone.clearsIn());
    }

    @Test
    void moreTeamsKeepCuttingItDown() {
        List<Duration> times = IntStream.of(1, 2, 4)
                .mapToObj(count -> staged(IntStream.range(0, count)
                                .mapToObj(one -> team("team" + one))
                                .toArray(RescueTeam[]::new))
                        .clearsIn())
                .toList();
        assertThat(times.get(0)).isGreaterThan(times.get(1));
        assertThat(times.get(1)).isGreaterThan(times.get(2));
    }

    @Test
    void everyCarrierLandsInExactlyOneBeat() {
        StagingPlan plan = staged(team("alpha"), team("bravo"), team("charlie"));
        List<Integer> numbers = plan.beats().stream()
                .flatMap(beat -> beat.stops().stream())
                .map(CarrierStop::number)
                .sorted()
                .toList();
        TreeSet<Integer> distinct = new TreeSet<>();
        plan.stops().forEach(one -> distinct.add(one.number()));
        assertThat(numbers).containsExactlyElementsOf(distinct);
        assertThat(numbers).hasSize(84);
    }

    @Test
    void theBeatsAreContiguousAlongTheLine() {
        StagingPlan plan = staged(team("alpha"), team("bravo"), team("charlie"));
        List<Long> ends = plan.beats().stream()
                .map(beat -> beat.stops().stream()
                        .mapToLong(one -> one.station().mils())
                        .max()
                        .orElseThrow())
                .toList();
        List<Long> starts = plan.beats().stream()
                .map(beat -> beat.stops().stream()
                        .mapToLong(one -> one.station().mils())
                        .min()
                        .orElseThrow())
                .toList();
        for (int index = 0; index + 1 < starts.size(); index++) {
            assertThat(ends.get(index)).isLessThanOrEqualTo(starts.get(index + 1));
        }
    }

    @Test
    void theCutIsTighterThanHandingTheLineOutByNearestTeam() {
        TeamPool pool = TeamPool.of(List.of(team("alpha", 0), team("bravo", 4800)));
        EvacuationTiming nearest = new EvacuationTiming(new EvacuationPlan(profile(), pool, NOWHERE, ON), 3);
        StagingPlan cut = Staging.stage(profile(), pool, NOWHERE, ON, 3);
        assertThat(cut.clearsIn()).isLessThan(nearest.totalTime());
    }

    @Test
    void theSpreadBetweenBusiestAndQuietestIsSmall() {
        StagingPlan plan = staged(team("alpha"), team("bravo"), team("charlie"), team("delta"));
        assertThat(plan.spread()).isLessThan(plan.busiest().work());
    }

    @Test
    void aTeamWhoseTrainingHasLapsedIsNotStaged() {
        StagingPlan plan = staged(team("alpha"), team("stale", LocalDate.of(2024, 12, 1)));
        assertThat(plan.teams()).extracting(RescueTeam::name).containsExactly("alpha");
    }

    @Test
    void aHillWithNobodyFitToWorkRefuses() {
        assertThatThrownBy(() -> staged(team("stale", LocalDate.of(2024, 12, 1))))
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void aCarrierNobodyCanReachIsReportedRatherThanHidden() {
        StagingPlan plan = staged(team("short", 8L, null));
        assertThat(plan.stranded()).isNotEmpty();
        assertThat(plan.isWorkable()).isFalse();
        assertThat(plan.refusals()).anySatisfy(one -> assertThat(one).contains("nobody reaches it"));
    }

    @Test
    void theSmallestKitThatCoversARunTakesIt() {
        StagingPlan plan = staged(team("ladders", 24L, null), team("ropes", 24L, 200L));
        for (Beat beat : plan.beats()) {
            if (beat.team().name().equals("ladders")) {
                assertThat(beat.stops())
                        .allSatisfy(one -> assertThat(beat.methodFor(one)).isNotSameAs(Method.ROPE));
            }
        }
    }

    @Test
    void aBeatPrintsWhereItIsAndHowLongItTakes() {
        StagingPlan plan = staged(team("alpha"));
        String printed = plan.busiest().toString();
        assertThat(printed).contains("alpha at");
        assertThat(printed).contains("84 carriers");
    }

    @Test
    void thePlanPrintsWhatTheManagerAsks() {
        StagingPlan plan = staged(team("alpha"), team("bravo"));
        assertThat(plan.toString()).startsWith("2 teams, 84 carriers, clear in");
    }

    @Test
    void theStepsAreNumberedFromOneWithoutAGap() {
        StagingPlan plan = staged(team("alpha"), team("bravo"));
        assertThat(plan.steps())
                .extracting(PlanStep::order)
                .isEqualTo(IntStream.range(1, 85).boxed().toList());
    }

    @Test
    void aFullerChairTakesLongerOnTheSameCut() {
        StagingPlan quiet = staged(2, team("alpha"), team("bravo"));
        StagingPlan busy = Staging.restage(quiet, 4);
        assertThat(busy.clearsIn()).isGreaterThan(quiet.clearsIn());
        assertThat(busy.beats().stream().map(one -> one.stops().size()).toList())
                .isEqualTo(quiet.beats().stream().map(one -> one.stops().size()).toList());
    }

    @Test
    void workingSeriallyTakesLongerThanWorkingAtOnce() {
        StagingPlan plan = staged(team("alpha"), team("bravo"), team("charlie"));
        assertThat(plan.serialTime()).isGreaterThan(plan.clearsIn());
    }

    @Test
    void theWalkingIsLessWhenTheTeamsAreStagedOnTheirOwnRuns() {
        StagingPlan parked = staged(team("alpha", 0), team("bravo", 0));
        assertThat(parked.walking()).isLessThan(parked.profile().slopeLength().scaled(84));
    }
}
