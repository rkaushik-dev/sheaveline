package io.github.rkaushikdev.sheaveline.evac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.HillCalendar;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.schedule.Site;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TimingTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(34500));
    private static final List<String> CREW = List.of("R Diaz", "T Woo", "J Marsh");

    /** A team of the usual crew; a null rope length means it carries no rope. */
    private static RescueTeam team(String name, long ladder, Long rope, long staged) {
        RescueKit kit = new RescueKit(
                Optional.of(Length.ofFeet(ladder)), Optional.ofNullable(rope).map(Length::ofFeet), 2, 3, 1);
        return RescueTeam.of(name, CREW, kit, Length.ofFeet(staged), LocalDate.of(2025, 12, 1));
    }

    private static RescueTeam team(String name, long staged) {
        return team(name, 24, 200L, staged);
    }

    /** What the department actually puts on the hill, staged along the line. */
    private static List<RescueTeam> fullPool() {
        return IntStream.range(0, 8)
                .mapToObj(index -> team("team" + index, index * 600L))
                .toList();
    }

    private static EvacuationTiming timing(int seats, long snowFeet, List<RescueTeam> teams) {
        EvacuationProfile profile = new EvacuationProfile(Hill.johnPaul(), SAG, Length.ofFeet(snowFeet));
        TeamPool pool = TeamPool.of(teams.isEmpty() ? fullPool() : teams);
        EvacuationPlan plan = new EvacuationPlan(profile, pool, Length.ofFeet(0), ON);
        return new EvacuationTiming(plan, seats);
    }

    private static EvacuationTiming timing(RescueTeam... teams) {
        return timing(3, 2, List.of(teams));
    }

    private static EvacuationTiming timing(List<RescueTeam> teams) {
        return timing(3, 2, teams);
    }

    private static EvacuationTiming timingWithSeats(int seats) {
        return timing(seats, 2, List.of());
    }

    @Test
    void aTimingNeedsPeopleAndTeamsThatCanWalk() {
        assertThatThrownBy(() -> timing().withLoad(0)).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> new EvacuationTiming(timing().plan(), 3, Duration.ofMinutes(15), new Speed(0)))
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void everyStepIsCostedInThreeParts() {
        List<StepTiming> costed = timing().timings();
        assertThat(costed).isNotEmpty();
        for (StepTiming one : costed) {
            assertThat(one.total()).isEqualTo(one.approach().plus(one.setup()).plus(one.lowering()));
        }
    }

    @Test
    void aRopeJobTakesLongerThanALadderJob() {
        EvacuationTiming ladder = timing(IntStream.range(0, 8)
                .mapToObj(index -> team("t" + index, 200, null, index * 600L))
                .toList());
        EvacuationTiming rope = timing(IntStream.range(0, 8)
                .mapToObj(index -> team("t" + index, 12, 200L, index * 600L))
                .toList());
        assertThat(rope.totalTime()).isGreaterThan(ladder.totalTime());
    }

    @Test
    void morePeopleInACarrierTakesLonger() {
        EvacuationTiming light = timingWithSeats(1);
        EvacuationTiming full = timingWithSeats(4);
        assertThat(full.totalTime()).isGreaterThan(light.totalTime());
        assertThat(full.people()).isGreaterThan(light.people());
    }

    @Test
    void aStepOffCostsAlmostNothingAndNeedsNoWalk() {
        EvacuationTiming low = timing(3, 30, List.of());
        List<StepTiming> stepOffs = low.timings().stream()
                .filter(one -> one.step().method() == Method.STEP_OFF)
                .toList();
        assertThat(stepOffs).isNotEmpty();
        for (StepTiming one : stepOffs) {
            assertThat(one.approach().isZero()).isTrue();
            assertThat(one.setup()).isEqualTo(Duration.ofMinutes(1));
        }
    }

    @Test
    void theFinishIsSetByTheBusiestTeamNotByTheTotalWork() {
        EvacuationTiming oneTeam = timing(team("alpha", 0));
        assertThat(oneTeam.totalTime()).isEqualTo(oneTeam.serialTime());
        EvacuationTiming twoTeams = timing(team("base", 0), team("summit", 4800));
        assertThat(twoTeams.totalTime()).isLessThan(twoTeams.serialTime());
    }

    @Test
    void aSecondTeamDoesNotHalveTheTime() {
        EvacuationTiming twoTeams = timing(team("base", 0), team("summit", 4800));
        assertThat(twoTeams.speedUp()).isGreaterThan(BigDecimal.ONE);
        assertThat(twoTeams.speedUp()).isLessThan(BigDecimal.TWO);
    }

    @Test
    void theBusiestTeamCanBeNamed() {
        EvacuationTiming twoTeams = timing(team("base", 0), team("summit", 4800));
        Map<String, Duration> work = twoTeams.workByTeam();
        String busiest = twoTeams.busiestTeam();
        assertThat(work.get(busiest))
                .isEqualTo(work.values().stream()
                        .max(Comparator.comparingLong(Duration::seconds))
                        .orElseThrow());
    }

    @Test
    void theCallOutIsInTheTotalWhateverElseHappens() {
        EvacuationTiming costed = timing();
        assertThat(costed.totalTime()).isGreaterThan(Duration.ofMinutes(15));
        assertThat(costed.serialTime()).isGreaterThan(Duration.ofMinutes(15));
    }

    @Test
    void aTeamStagedFurtherAwayWalksLonger() {
        EvacuationTiming near = timing(team("near", 2400));
        EvacuationTiming far = timing(team("far", 0));
        assertThat(EvacuationTiming.approachDistance(far.plan()))
                .isNotEqualTo(EvacuationTiming.approachDistance(near.plan()));
    }

    @Test
    void theFinishTimeFallsOutOfTheStart() {
        EvacuationTiming costed = timing();
        TimeOfDay started = TimeOfDay.at(13);
        assertThat(costed.finishesBy(started)).isEqualTo(started.plus(costed.totalTime()));
    }

    @Test
    void whetherTheJobFitsInTheLight() {
        EvacuationTiming costed = timing();
        Site site = HillCalendar.theSite();
        assertThat(costed.fitsInDaylight(site, ON, TimeOfDay.at(9))).isTrue();
        assertThat(costed.fitsInDaylight(site, ON, TimeOfDay.at(15, 30))).isFalse();
    }

    @Test
    void theLatestStartIsTheNumberTheBoardAsksFor() {
        EvacuationTiming costed = timing();
        Site site = HillCalendar.theSite();
        TimeOfDay latest = costed.latestStart(site, ON);
        assertThat(costed.fitsInDaylight(site, ON, latest)).isTrue();
        assertThat(latest).isLessThan(site.sunset(ON));
    }

    @Test
    void aHeavierLoadPullsTheLatestStartEarlier() {
        Site site = HillCalendar.theSite();
        TimeOfDay light = timingWithSeats(1).latestStart(site, ON);
        TimeOfDay heavy = timingWithSeats(4).latestStart(site, ON);
        assertThat(heavy).isLessThan(light);
    }

    @Test
    void aTimingPrintsTheTwoNumbersThatMatter() {
        String printed = timing().toString();
        assertThat(printed).contains("people,");
        assertThat(printed).endsWith(timing().totalTime().formatHms());
    }

    @Test
    void aStepTimingPrintsShort() {
        StepTiming one = timing().timings().get(0);
        assertThat(one.toString()).startsWith("carrier ");
    }
}
