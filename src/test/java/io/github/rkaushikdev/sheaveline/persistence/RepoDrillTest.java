package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.evac.Drill;
import io.github.rkaushikdev.sheaveline.evac.DrillKind;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepoDrillTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final List<String> ALPHA_AND_BRAVO = List.of("alpha", "bravo");

    private Session session;
    private DrillRepository drills;

    @BeforeEach
    void bothMachinesOnFile() {
        session = TestDatabase.fresh().session();
        LiftRepository lifts = new LiftRepository(session);
        lifts.add(johnPaul());
        lifts.add(strawberry());
        session.commit();
        drills = new DrillRepository(session);
    }

    @AfterEach
    void closeIt() {
        session.close();
    }

    private static Drill drill(LiftCode lift, LocalDate on, DrillKind kind, List<String> teams, int carriers) {
        return Drill.of(lift, on, kind, teams, carriers, Duration.ofMinutes(48), "R Skaggs");
    }

    private static Drill drill(LiftCode lift, LocalDate on) {
        return drill(lift, on, DrillKind.FULL_LINE, ALPHA_AND_BRAVO, 6);
    }

    private static Drill drill() {
        return drill(JOHN_PAUL, ON);
    }

    @Test
    void aDrillGoesInAndComesBackOut() {
        drills.add(drill());
        session.commit();
        List<Drill> found = drills.onLift(JOHN_PAUL);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).teams()).containsExactly("alpha", "bravo");
        assertThat(found.get(0).elapsed().minutes()).isEqualByComparingTo("48");
        assertThat(found.get(0).countsForTheAnnual()).isTrue();
    }

    @Test
    void theTeamsComeBackInASettledOrder() {
        drills.add(drill(JOHN_PAUL, ON, DrillKind.FULL_LINE, List.of("charlie", "alpha", "bravo"), 6));
        session.commit();
        assertThat(drills.onLift(JOHN_PAUL).get(0).teams()).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void theSameMachineIsNotDrilledTwiceInADay() {
        drills.add(drill());
        session.commit();
        assertThatThrownBy(() -> drills.add(drill()))
                .isInstanceOf(StorageError.class)
                .hasMessageContaining("already drilled");
    }

    @Test
    void twoMachinesCanDrillOnTheSameDay() {
        drills.add(drill(JOHN_PAUL, ON));
        drills.add(drill(STRAWBERRY, ON));
        session.commit();
        assertThat(drills.register().size()).isEqualTo(2);
    }

    @Test
    void theLastFullDrillIsTheOneThatMatters() {
        drills.add(drill(JOHN_PAUL, LocalDate.of(2024, 11, 1)));
        drills.add(drill(JOHN_PAUL, LocalDate.of(2024, 12, 1), DrillKind.PARTIAL, ALPHA_AND_BRAVO, 2));
        session.commit();
        assertThat(drills.lastFull(JOHN_PAUL))
                .hasValueSatisfying(last -> assertThat(last.on()).isEqualTo(LocalDate.of(2024, 11, 1)));
    }

    @Test
    void theAnnualRunsAYearFromTheLastFullDrill() {
        drills.add(drill(JOHN_PAUL, LocalDate.of(2024, 11, 1)));
        session.commit();
        assertThat(drills.expires(JOHN_PAUL)).contains(LocalDate.of(2025, 11, 1));
    }

    @Test
    void aMachineThatHasNeverDrilledHasNoExpiry() {
        assertThat(drills.expires(JOHN_PAUL)).isEmpty();
        assertThat(drills.lastFull(JOHN_PAUL)).isEmpty();
    }

    @Test
    void whatIsOutOfDateIsListed() {
        drills.add(drill(JOHN_PAUL, LocalDate.of(2024, 11, 1)));
        session.commit();
        assertThat(drills.outOfDate(ON)).containsExactly(STRAWBERRY);
        assertThat(drills.outOfDate(LocalDate.of(2026, 1, 1))).containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void aTabletopDoesNotStopAMachineGoingOutOfDate() {
        drills.add(drill(JOHN_PAUL, ON, DrillKind.TABLETOP, ALPHA_AND_BRAVO, 0));
        session.commit();
        assertThat(drills.outOfDate(ON)).contains(JOHN_PAUL);
    }
}
