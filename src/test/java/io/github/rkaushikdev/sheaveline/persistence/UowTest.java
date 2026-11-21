package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UowTest {

    private Database database;

    @BeforeEach
    void emptyDatabase() {
        database = TestDatabase.fresh();
    }

    private static WorkOrder order() {
        return new WorkOrder(
                1,
                JOHN_PAUL,
                LocalDate.of(2025, 1, 15),
                "M Vetter",
                "sheave liner cracked",
                Priority.SCHEDULED,
                Optional.of("JP-T4"),
                Optional.empty(),
                Optional.empty());
    }

    private UnitOfWork work() {
        return UnitOfWork.on(database).open();
    }

    @Test
    void aCommittedUnitOfWorkKeepsWhatItWrote() {
        try (UnitOfWork work = work()) {
            work.lifts().add(johnPaul());
            work.commit();
        }
        try (UnitOfWork work = work()) {
            assertThat(work.lifts().exists(JOHN_PAUL)).isTrue();
        }
    }

    @Test
    void aUnitOfWorkThatIsNotCommittedKeepsNothing() {
        try (UnitOfWork work = work()) {
            work.lifts().add(johnPaul());
        }
        try (UnitOfWork work = work()) {
            assertThat(work.lifts().exists(JOHN_PAUL)).isFalse();
        }
    }

    @Test
    void anythingRaisedInsideRollsTheWholeThingBack() {
        assertThatThrownBy(() -> {
                    try (UnitOfWork work = work()) {
                        work.lifts().add(johnPaul());
                        work.commit();
                        work.lifts().add(strawberry());
                        throw new IllegalStateException("the mechanic was called away");
                    }
                })
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the mechanic was called away");
        try (UnitOfWork work = work()) {
            assertThat(work.lifts().codes()).containsExactly(JOHN_PAUL);
        }
    }

    @Test
    void aRollbackInsideLeavesTheEarlierCommitAlone() {
        try (UnitOfWork work = work()) {
            work.lifts().add(johnPaul());
            work.commit();
            work.lifts().add(strawberry());
            work.rollback();
        }
        try (UnitOfWork work = work()) {
            assertThat(work.lifts().codes()).containsExactly(JOHN_PAUL);
        }
    }

    @Test
    void aUnitOfWorkReportsWhetherItWasCommitted() {
        try (UnitOfWork work = work()) {
            assertThat(work.wasCommitted()).isFalse();
            work.lifts().add(johnPaul());
            work.commit();
            assertThat(work.wasCommitted()).isTrue();
            work.rollback();
            assertThat(work.wasCommitted()).isFalse();
        }
    }

    @Test
    void aUnitOfWorkIsOnlyUsableInsideItsBlock() {
        UnitOfWork work = UnitOfWork.on(database);
        assertThat(work.isOpen()).isFalse();
        assertThatThrownBy(work::commit).isInstanceOf(StorageError.class).hasMessage("this unit of work is not open");
        assertThatThrownBy(work::lifts).isInstanceOf(StorageError.class);
        try (UnitOfWork opened = work.open()) {
            assertThat(opened.isOpen()).isTrue();
        }
        assertThat(work.isOpen()).isFalse();
    }

    @Test
    void aUnitOfWorkCannotBeOpenedTwiceAtOnce() {
        UnitOfWork work = UnitOfWork.on(database);
        try (UnitOfWork opened = work.open()) {
            assertThatThrownBy(opened::open)
                    .isInstanceOf(StorageError.class)
                    .hasMessage("this unit of work is already open");
        }
    }

    @Test
    void aFlushLetsTheDatabaseRefuseEarly() {
        try (UnitOfWork work = work()) {
            work.lifts().add(johnPaul());
            work.flush();
            assertThat(work.lifts().exists(JOHN_PAUL)).isTrue();
        }
    }

    @Test
    void everyRepositoryHangsOffTheSameSession() {
        try (UnitOfWork work = work()) {
            Session shared = work.session();
            assertThat(work.lifts().session()).isSameAs(shared);
            assertThat(work.ropes().session()).isSameAs(shared);
            assertThat(work.wearSurveys().session()).isSameAs(shared);
            assertThat(work.incidents().session()).isSameAs(shared);
            assertThat(work.workOrders().session()).isSameAs(shared);
        }
    }

    @Test
    void twoRepositoriesSeeEachOtherInsideOneUnit() {
        try (UnitOfWork work = work()) {
            work.lifts().add(johnPaul());
            work.flush();
            assertThat(work.ropes().exists(johnPaul().rope().serial())).isTrue();
            work.commit();
        }
    }

    @Test
    void aSurveyAndAnOrderWrittenTogetherLandTogether() {
        HaulRope rope = johnPaul().rope();
        try (UnitOfWork work = work()) {
            work.lifts().add(johnPaul());
            work.flush();
            work.wearSurveys()
                    .add(WearSurvey.of(
                            rope,
                            LocalDate.of(2025, 1, 15),
                            "R Halvorsen",
                            List.of(new DiameterReading(
                                    Length.ofFeet(0), Length.ofInches("1.376"), Length.ofInches("1.376")))));
            work.workOrders().add(order());
            work.commit();
        }
        try (UnitOfWork work = work()) {
            assertThat(work.wearSurveys().countFor(rope)).isEqualTo(1);
            assertThat(work.workOrders().get(1).where()).contains("JP-T4");
        }
    }

    @Test
    void aFailureHalfwayLeavesNeitherOfThem() {
        HaulRope rope = johnPaul().rope();
        assertThatThrownBy(() -> {
                    try (UnitOfWork work = work()) {
                        work.lifts().add(johnPaul());
                        work.flush();
                        work.workOrders().add(order());
                        work.workOrders().add(order());
                    }
                })
                .isInstanceOf(StorageError.class);
        try (UnitOfWork work = work()) {
            assertThat(work.lifts().exists(JOHN_PAUL)).isFalse();
            assertThat(work.ropes().exists(rope.serial())).isFalse();
        }
    }

    @Test
    void aSecondUnitOfWorkCanBeOpenedAfterTheFirstCloses() {
        UnitOfWork work = UnitOfWork.on(database);
        try (UnitOfWork first = work.open()) {
            first.lifts().add(johnPaul());
            first.commit();
        }
        try (UnitOfWork second = work.open()) {
            assertThat(second.lifts().exists(JOHN_PAUL)).isTrue();
        }
    }
}
