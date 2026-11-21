package io.github.rkaushikdev.sheaveline.evac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlanTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(34500));
    private static final List<String> CREW = List.of("R Diaz", "T Woo", "J Marsh");
    private static final LocalDate UNTIL = LocalDate.of(2025, 12, 1);

    private static EvacuationProfile profile(long snowFeet) {
        return new EvacuationProfile(Hill.johnPaul(), SAG, Length.ofFeet(snowFeet));
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

    private static RescueTeam team() {
        return team("alpha", 24L, 200L, 0, UNTIL);
    }

    private static RescueTeam team(Long ladder, Long rope) {
        return team("alpha", ladder, rope, 0, UNTIL);
    }

    private static RescueTeam team(String name, long staged) {
        return team(name, 24L, 200L, staged, UNTIL);
    }

    private static EvacuationPlan plan(long snowFeet, RescueTeam... teams) {
        TeamPool pool = TeamPool.of(teams.length == 0 ? List.of(team()) : List.of(teams));
        return new EvacuationPlan(profile(snowFeet), pool, Length.ofFeet(0), ON);
    }

    private static EvacuationPlan plan(RescueTeam... teams) {
        return plan(2, teams);
    }

    private static BigDecimal meanFeet(List<PlanStep> steps) {
        BigDecimal sum =
                steps.stream().map(step -> step.stop().station().feet()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Decimals.divide(sum, steps.size());
    }

    @Test
    void everyCarrierGetsAStepWhenThePoolCanDoTheWork() {
        EvacuationPlan made = plan();
        assertThat(made.steps()).hasSameSizeAs(made.stops());
        assertThat(made.isWorkable()).isTrue();
        assertThat(made.unassigned()).isEmpty();
    }

    @Test
    void theStepsAreNumberedFromOneWithoutGaps() {
        EvacuationPlan made = plan();
        List<Integer> orders = made.steps().stream().map(PlanStep::order).toList();
        assertThat(orders)
                .isEqualTo(IntStream.rangeClosed(1, orders.size()).boxed().toList());
    }

    @Test
    void theMostExposedCarrierIsWorkedFirst() {
        EvacuationPlan made = plan();
        List<Long> heights =
                made.steps().stream().map(step -> step.stop().height().mils()).toList();
        assertThat(heights).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void aCarrierLowEnoughToStepOffNeedsNobody() {
        EvacuationPlan low = plan(30, team());
        List<PlanStep> stepOffs = low.stepOffs();
        assertThat(stepOffs).isNotEmpty();
        for (PlanStep step : stepOffs) {
            assertThat(step.team()).isEmpty();
            assertThat(step.stop().height()).isLessThanOrEqualTo(EvacuationPlan.STEP_OFF_HEIGHT);
        }
    }

    @Test
    void aCarrierInsideTheLadderReachIsWorkedOffALadder() {
        EvacuationPlan made = plan(team(200L, null));
        assertThat(made.steps().stream().filter(step -> step.method().needsATeam()))
                .allSatisfy(step -> assertThat(step.method()).isSameAs(Method.LADDER));
    }

    @Test
    void aCarrierBeyondTheLadderNeedsTheRope() {
        EvacuationPlan made = plan(team(12L, 200L));
        List<PlanStep> technical = made.technicalSteps();
        assertThat(technical).isNotEmpty();
        assertThat(technical).allSatisfy(step -> assertThat(step.method()).isSameAs(Method.ROPE));
    }

    @Test
    void aCarrierNobodyCanReachIsLeftOutAndReported() {
        EvacuationPlan shorter = plan(team(12L, null));
        assertThat(shorter.isWorkable()).isFalse();
        assertThat(shorter.unassigned()).isNotEmpty();
        assertThat(shorter.steps().size()).isLessThan(shorter.stops().size());
        assertThat(shorter.toString()).contains("unreachable");
    }

    @Test
    void aLapsedPoolCanDoNothingAtAll() {
        EvacuationPlan lapsed = plan(2, team("alpha", 24L, 200L, 0, LocalDate.of(2024, 6, 1)));
        assertThat(lapsed.isWorkable()).isFalse();
        assertThat(lapsed.unassigned()).isNotEmpty();
    }

    @Test
    void theNearestTeamTakesTheJobWhenTwoCanDoIt() {
        EvacuationPlan made = plan(team("base", 0), team("summit", 4800));
        Map<String, List<PlanStep>> grouped = made.byTeam();
        assertThat(grouped.keySet()).containsExactlyInAnyOrder("base", "summit");
        grouped.forEach((name, steps) -> {
            for (PlanStep step : steps) {
                assertThat(step.team()).isPresent();
                assertThat(step.team().get().name()).isEqualTo(name);
            }
        });
    }

    @Test
    void theWorkIsSplitByWhereTheCarriersAre() {
        EvacuationPlan made = plan(team("base", 0), team("summit", 4800));
        Map<String, List<PlanStep>> grouped = made.byTeam();
        assertThat(meanFeet(grouped.get("base"))).isLessThan(meanFeet(grouped.get("summit")));
    }

    @Test
    void aStepNeedsATeamWhenTheMethodDoes() {
        EvacuationPlan made = plan();
        CarrierStop stop = made.orderedStops().get(0);
        assertThatThrownBy(() -> new PlanStep(1, stop, Method.ROPE, Optional.empty()))
                .isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> new PlanStep(1, stop, Method.STEP_OFF, team())).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> new PlanStep(0, stop, Method.ROPE, team())).isInstanceOf(EvacuationError.class);
    }

    @Test
    void theMethodsKnowWhatTheyNeed() {
        assertThat(Method.ROPE.needsATeam()).isTrue();
        assertThat(Method.LADDER.needsATeam()).isTrue();
        assertThat(Method.STEP_OFF.needsATeam()).isFalse();
        assertThat(Method.ROPE.isTechnical()).isTrue();
        assertThat(Method.LADDER.isTechnical()).isFalse();
    }

    @Test
    void aStepPrintsALineSomebodyCanReadInAStorm() {
        EvacuationPlan made = plan();
        String printed = made.steps().get(0).toString();
        assertThat(printed).startsWith("1. carrier ");
        assertThat(printed).contains("ft,");
    }

    @Test
    void thePlanCountsThePeopleOnTheLine() {
        EvacuationPlan made = plan();
        assertThat(made.peopleOnTheLine(4)).isEqualTo(made.stops().size() * 4);
        assertThat(made.peopleOnTheLine(0)).isZero();
        assertThatThrownBy(() -> made.peopleOnTheLine(-1)).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aPlanPrintsItsState() {
        assertThat(plan().toString()).endsWith("workable");
    }
}
