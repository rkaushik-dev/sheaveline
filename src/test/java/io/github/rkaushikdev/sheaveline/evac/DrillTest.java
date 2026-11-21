package io.github.rkaushikdev.sheaveline.evac;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.HillCalendar;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DrillTest {

    private static final Season SEASON = HillCalendar.theSeason();
    private static final List<String> TEAMS = List.of("alpha", "bravo", "charlie");

    /** A drill sheet filled in with the usual answers, any of which a test can change. */
    private static final class Sheet {
        private LiftCode lift = JOHN_PAUL;
        private LocalDate on = LocalDate.of(2024, 12, 5);
        private DrillKind kind = DrillKind.FULL_LINE;
        private List<String> teams = TEAMS;
        private int carriers = 8;
        private long minutes = 95;
        private String observer = "H Okafor";

        Sheet lift(LiftCode changed) {
            lift = changed;
            return this;
        }

        Sheet on(LocalDate changed) {
            on = changed;
            return this;
        }

        Sheet kind(DrillKind changed) {
            kind = changed;
            return this;
        }

        Sheet teams(String... changed) {
            teams = List.of(changed);
            return this;
        }

        Sheet carriers(int changed) {
            carriers = changed;
            return this;
        }

        Sheet minutes(long changed) {
            minutes = changed;
            return this;
        }

        Sheet observer(String changed) {
            observer = changed;
            return this;
        }

        Drill build() {
            return Drill.of(lift, on, kind, teams, carriers, Duration.ofMinutes(minutes), observer);
        }
    }

    private static Sheet sheet() {
        return new Sheet();
    }

    private static Drill drill() {
        return sheet().build();
    }

    private static DrillRegister register(Drill... drills) {
        return DrillRegister.of(List.of(drills));
    }

    @Test
    void aDrillNeedsTeamsAnObserverAndNoDuplicates() {
        assertThatThrownBy(() -> sheet().teams().build()).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> sheet().teams("alpha", "alpha").build()).isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> sheet().observer("  ").build()).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aDrillOnTheLineWorksAtLeastOneCarrier() {
        assertThatThrownBy(() -> sheet().kind(DrillKind.PARTIAL).carriers(0).build())
                .isInstanceOf(EvacuationError.class);
        assertThatThrownBy(() -> sheet().carriers(-1).build()).isInstanceOf(EvacuationError.class);
    }

    @Test
    void aTabletopDrillTouchesNoCarriers() {
        assertThatThrownBy(() -> sheet().kind(DrillKind.TABLETOP).carriers(4).build())
                .isInstanceOf(EvacuationError.class);
        assertThat(sheet().kind(DrillKind.TABLETOP).carriers(0).build().carriersWorked())
                .isZero();
    }

    @Test
    void onlyAFullLineDrillWithEnoughCarriersCountsForTheAnnual() {
        assertThat(sheet().kind(DrillKind.FULL_LINE).carriers(8).build().countsForTheAnnual())
                .isTrue();
        assertThat(sheet().kind(DrillKind.FULL_LINE).carriers(2).build().countsForTheAnnual())
                .isFalse();
        assertThat(sheet().kind(DrillKind.PARTIAL).carriers(8).build().countsForTheAnnual())
                .isFalse();
        assertThat(sheet().kind(DrillKind.TABLETOP).carriers(0).build().countsForTheAnnual())
                .isFalse();
    }

    @Test
    void theKindsKnowWhatTheyExercise() {
        assertThat(DrillKind.FULL_LINE.putsPeopleOnTheLine()).isTrue();
        assertThat(DrillKind.PARTIAL.putsPeopleOnTheLine()).isTrue();
        assertThat(DrillKind.TABLETOP.putsPeopleOnTheLine()).isFalse();
        assertThat(DrillKind.FULL_LINE.satisfiesTheAnnual()).isTrue();
        assertThat(DrillKind.PARTIAL.satisfiesTheAnnual()).isFalse();
    }

    @Test
    void aDrillReportsTheRateItAchieved() {
        assertThat(sheet().carriers(8).minutes(96).build().minutesPerCarrier()).isEqualTo(Duration.ofMinutes(12));
        assertThatThrownBy(() ->
                        sheet().kind(DrillKind.TABLETOP).carriers(0).build().minutesPerCarrier())
                .isInstanceOf(EvacuationError.class);
    }

    @Test
    void aDrillExpiresAYearAfterItWasRun() {
        assertThat(sheet().on(LocalDate.of(2024, 12, 5)).build().expires()).isEqualTo(LocalDate.of(2025, 12, 5));
    }

    @Test
    void aDrillPrintsAOneLiner() {
        assertThat(drill()).hasToString("JP full line on 2024-12-05, 8 carriers");
    }

    @Test
    void theLiftIsCurrentForAYearAfterAFullDrill() {
        DrillRegister held = register(sheet().on(LocalDate.of(2024, 12, 5)).build());
        assertThat(held.liftIsCurrent(JOHN_PAUL, LocalDate.of(2025, 1, 15))).isTrue();
        assertThat(held.liftIsCurrent(JOHN_PAUL, LocalDate.of(2026, 1, 15))).isFalse();
        assertThat(held.liftExpires(JOHN_PAUL)).contains(LocalDate.of(2025, 12, 5));
    }

    @Test
    void aMachineWithNoFullDrillIsNeverCurrent() {
        DrillRegister held =
                register(sheet().kind(DrillKind.PARTIAL).carriers(3).build());
        assertThat(held.lastFull(JOHN_PAUL)).isEmpty();
        assertThat(held.liftIsCurrent(JOHN_PAUL, LocalDate.of(2025, 1, 15))).isFalse();
        assertThat(held.liftExpires(JOHN_PAUL)).isEmpty();
    }

    @Test
    void drillsAreKeptApartByMachine() {
        DrillRegister held = register(
                sheet().lift(JOHN_PAUL).build(), sheet().lift(STRAWBERRY).build());
        assertThat(held.size()).isEqualTo(2);
        assertThat(held.onLift(JOHN_PAUL)).hasSize(1);
        assertThat(held.liftIsCurrent(STRAWBERRY, LocalDate.of(2025, 1, 15))).isTrue();
    }

    @Test
    void aTeamIsCurrentOnlyIfItWasInADrillThisSeason() {
        DrillRegister held = register(sheet().teams("alpha", "bravo").build());
        assertThat(held.teamTookPart("alpha", JOHN_PAUL, SEASON)).isTrue();
        assertThat(held.teamTookPart("charlie", JOHN_PAUL, SEASON)).isFalse();
    }

    @Test
    void aDrillFromLastSeasonDoesNotMakeATeamCurrent() {
        DrillRegister held =
                register(sheet().on(LocalDate.of(2024, 3, 1)).teams("alpha").build());
        assertThat(held.teamTookPart("alpha", JOHN_PAUL, SEASON)).isFalse();
    }

    @Test
    void theTeamsStillShortAreListed() {
        DrillRegister held = register(sheet().teams("alpha", "bravo").build());
        assertThat(held.teamsShort(JOHN_PAUL, SEASON, TEAMS)).containsExactly("charlie");
    }

    @Test
    void aMachineWithNewTeamsIsNotCurrentHoweverGoodTheDrillWas() {
        DrillRegister held = register(sheet().teams("alpha", "bravo").build());
        assertThat(held.liftIsCurrent(JOHN_PAUL, LocalDate.of(2025, 1, 15))).isTrue();
        assertThat(held.isCurrent(JOHN_PAUL, LocalDate.of(2025, 1, 15), SEASON, TEAMS))
                .isFalse();
    }

    @Test
    void aMachineWithEverybodyInIsCurrentOnBothCounts() {
        DrillRegister held = register(sheet().teams("alpha", "bravo", "charlie").build());
        assertThat(held.isCurrent(JOHN_PAUL, LocalDate.of(2025, 1, 15), SEASON, TEAMS))
                .isTrue();
    }

    @Test
    void theBestRateTheHillHasAchievedIsWhatAPlanIsCheckedAgainst() {
        DrillRegister held = register(
                sheet().on(LocalDate.of(2024, 12, 5)).carriers(8).minutes(96).build(),
                sheet().on(LocalDate.of(2025, 1, 5))
                        .kind(DrillKind.PARTIAL)
                        .carriers(4)
                        .minutes(32)
                        .build());
        assertThat(held.rateFor(JOHN_PAUL)).isEqualTo(Duration.ofMinutes(8));
        assertThatThrownBy(() -> held.rateFor(STRAWBERRY)).isInstanceOf(EvacuationError.class);
    }

    @Test
    void addingADrillMakesANewRegister() {
        DrillRegister held = register();
        DrillRegister longer = held.withDrill(drill());
        assertThat(held.size()).isZero();
        assertThat(longer.size()).isEqualTo(1);
    }
}
