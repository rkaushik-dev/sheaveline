package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.Defect;
import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import io.github.rkaushikdev.sheaveline.upkeep.InspectionRecord;
import io.github.rkaushikdev.sheaveline.upkeep.UpkeepError;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The inspection records and their defects, written and read through the repository. */
class InspectionRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);

    private UnitOfWork work;
    private InspectionRepository inspections;

    @BeforeEach
    void bothMachinesOnFile() {
        work = UnitOfWork.on(TestDatabase.fresh()).open();
        LiftRepository lifts = work.lifts();
        lifts.add(johnPaul());
        lifts.add(strawberry());
        work.commit();
        inspections = work.inspections();
    }

    @AfterEach
    void closeIt() {
        work.close();
    }

    private static InspectionRecord walk(LiftCode lift, LocalDate done, Defect... defects) {
        return InspectionRecord.of(lift, "w1", done, "M Vetter", Optional.empty(), Optional.empty(), List.of(defects));
    }

    private static Defect liner(String where, LocalDate raised) {
        return new Defect(where, DefectLevel.DEFECT, "sheave liner cracked", raised);
    }

    @Test
    void aRecordComesBackWithItsDefectsAndItsMeters() {
        Defect found = liner("JP-T4", DAY);
        inspections.add(walk(JOHN_PAUL, DAY, found));
        inspections.add(InspectionRecord.of(
                JOHN_PAUL,
                "H1",
                DAY.plusDays(1),
                "M Vetter",
                Optional.of(new BigDecimal("42000.5")),
                Optional.empty(),
                List.of()));
        inspections.add(InspectionRecord.of(
                JOHN_PAUL, "C1", DAY.plusDays(2), "M Vetter", Optional.empty(), Optional.of(1_000_000L), List.of()));
        work.commit();
        List<InspectionRecord> records = inspections.forLift(JOHN_PAUL);
        assertThat(records.stream().map(InspectionRecord::code)).containsExactly("W1", "H1", "C1");
        assertThat(records.get(0).defects()).containsExactly(found);
        assertThat(records.get(1).hours())
                .hasValueSatisfying(hours -> assertThat(hours).isEqualByComparingTo("42000.5"));
        assertThat(records.get(2).cycles()).contains(1_000_000L);
        assertThat(inspections.forLift(JOHN_PAUL, "h1").stream().map(InspectionRecord::done))
                .containsExactly(DAY.plusDays(1));
        assertThat(inspections.forLift(STRAWBERRY)).isEmpty();
    }

    @Test
    void theLastTimeAJobWasDoneIsItsLatestRecord() {
        inspections.add(walk(JOHN_PAUL, DAY.plusDays(7)));
        inspections.add(walk(JOHN_PAUL, DAY));
        work.commit();
        assertThat(inspections.lastDone(JOHN_PAUL, "w1"))
                .hasValueSatisfying(last -> assertThat(last.done()).isEqualTo(DAY.plusDays(7)));
        assertThat(inspections.lastDone(JOHN_PAUL, "A1")).isEmpty();
    }

    @Test
    void theOpenDefectsCanBeNarrowedToOneMachine() {
        inspections.add(walk(JOHN_PAUL, DAY, liner("JP-T4", DAY.plusDays(1))));
        inspections.add(walk(STRAWBERRY, DAY, liner("SB-T2", DAY)));
        inspections.add(
                walk(JOHN_PAUL, DAY.plusDays(7), liner("JP-T6", DAY.plusDays(7)).clear(DAY.plusDays(8), "M Vetter")));
        work.commit();
        assertThat(inspections.openDefects().stream().map(Defect::where)).containsExactly("SB-T2", "JP-T4");
        assertThat(inspections.openDefects(STRAWBERRY).stream().map(Defect::where))
                .containsExactly("SB-T2");
    }

    @Test
    void clearingADefectWritesTheClearanceBack() {
        inspections.add(walk(JOHN_PAUL, DAY, liner("JP-T4", DAY)));
        work.commit();
        Defect cleared = inspections.clearDefect("JP-T4", DAY, DAY.plusDays(2), "M Vetter");
        work.commit();
        assertThat(cleared.cleared()).contains(DAY.plusDays(2));
        assertThat(inspections.openDefects()).isEmpty();
        assertThat(inspections.forLift(JOHN_PAUL).get(0).defects().get(0).clearedBy())
                .contains("M Vetter");
    }

    @Test
    void clearingADefectNobodyRaised() {
        assertThatThrownBy(() -> inspections.clearDefect("JP-T9", DAY, DAY, "M Vetter"))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no open defect at JP-T9 raised 2025-01-15");
    }

    @Test
    void aDefectIsClearedOnce() {
        inspections.add(walk(JOHN_PAUL, DAY, liner("JP-T4", DAY)));
        inspections.clearDefect("JP-T4", DAY, DAY.plusDays(2), "M Vetter");
        work.commit();
        assertThatThrownBy(() -> inspections.clearDefect("JP-T4", DAY, DAY.plusDays(3), "M Vetter"))
                .isInstanceOf(UpkeepError.class)
                .hasMessageContaining("already cleared");
    }
}
