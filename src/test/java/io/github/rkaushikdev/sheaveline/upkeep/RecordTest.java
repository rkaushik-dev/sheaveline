package io.github.rkaushikdev.sheaveline.upkeep;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);

    private static Defect defect(
            String where, DefectLevel level, String detail, LocalDate raised, Optional<LocalDate> cleared) {
        return new Defect(where, level, detail, raised, cleared, cleared.map(any -> "M Vetter"));
    }

    private static Defect defect(String where, DefectLevel level, String detail) {
        return defect(where, level, detail, DAY, Optional.empty());
    }

    private static Defect defect(DefectLevel level) {
        return defect("JP-T4", level, "sheave liner cracked");
    }

    private static Defect defect(LocalDate raised) {
        return defect("JP-T4", DefectLevel.DEFECT, "sheave liner cracked", raised, Optional.empty());
    }

    private static Defect defect() {
        return defect(DAY);
    }

    private static Defect cleared(LocalDate on) {
        return defect("JP-T4", DefectLevel.DEFECT, "sheave liner cracked", DAY, Optional.of(on));
    }

    private static InspectionRecord record(
            String code,
            LocalDate done,
            List<Defect> defects,
            Optional<BigDecimal> hours,
            Optional<Long> cycles,
            LiftCode lift) {
        return InspectionRecord.of(lift, code, done, "M Vetter", hours, cycles, defects);
    }

    private static InspectionRecord record(String code, LocalDate done, List<Defect> defects) {
        return record(code, done, defects, Optional.empty(), Optional.empty(), JOHN_PAUL);
    }

    private static InspectionRecord record(String code, LocalDate done) {
        return record(code, done, List.of());
    }

    private static InspectionRecord record(String code) {
        return record(code, DAY);
    }

    private static InspectionRecord record(List<Defect> defects) {
        return record("W1", DAY, defects);
    }

    private static InspectionRecord record() {
        return record("W1");
    }

    @Test
    void aDefectSaysWhereItIsAndWhatIsWrong() {
        assertThatThrownBy(() -> defect("  ", DefectLevel.DEFECT, "sheave liner cracked"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a defect says where it is");
        assertThatThrownBy(() -> defect("JP-T4", DefectLevel.DEFECT, ""))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a defect says what is wrong");
    }

    @Test
    void aDefectCannotBeClearedBeforeItWasRaised() {
        assertThatThrownBy(() -> cleared(LocalDate.of(2025, 1, 1)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a defect cannot be cleared before it was raised");
    }

    @Test
    void aDefectIsClearedBySomebodyOnSomeDayOrNeither() {
        assertThatThrownBy(() -> new Defect(
                        "JP-T4",
                        DefectLevel.DEFECT,
                        "cracked",
                        DAY,
                        Optional.of(LocalDate.of(2025, 2, 1)),
                        Optional.empty()))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a defect is cleared by somebody on some day, or neither");
    }

    @Test
    void anOpenDefectAgesUntilItIsCleared() {
        Defect openOne = defect();
        assertThat(openOne.isOpen()).isTrue();
        assertThat(openOne.ageInDays(LocalDate.of(2025, 2, 14))).isEqualTo(30);
        assertThatThrownBy(() -> openOne.ageInDays(LocalDate.of(2025, 1, 1)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("that day is before the defect was raised");
    }

    @Test
    void aClearedDefectStopsAgeing() {
        Defect closed = cleared(LocalDate.of(2025, 1, 25));
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.ageInDays(LocalDate.of(2025, 3, 1))).isEqualTo(10);
    }

    @Test
    void clearingADefectMakesANewRecord() {
        Defect openOne = defect();
        Defect closed = openOne.clear(LocalDate.of(2025, 1, 25), "M Vetter");
        assertThat(openOne.isOpen()).isTrue();
        assertThat(closed.isOpen()).isFalse();
        assertThatThrownBy(() -> closed.clear(LocalDate.of(2025, 2, 1), "M Vetter"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("the defect at JP-T4 is already cleared");
        assertThatThrownBy(() -> openOne.clear(LocalDate.of(2025, 1, 25), "  "))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a defect is cleared by somebody");
    }

    @Test
    void onlyASeriousDefectStopsTheMachine() {
        assertThat(DefectLevel.SERIOUS.stopsTheMachine()).isTrue();
        assertThat(DefectLevel.DEFECT.stopsTheMachine()).isFalse();
        assertThat(DefectLevel.DEFECT.needsAWorkOrder()).isTrue();
        assertThat(DefectLevel.OBSERVATION.needsAWorkOrder()).isFalse();
    }

    @Test
    void aDefectPrintsItsState() {
        assertThat(defect()).hasToString("JP-T4 defect: sheave liner cracked (open)");
        Defect closed = cleared(LocalDate.of(2025, 1, 25));
        assertThat(closed.toString()).endsWith("(cleared 2025-01-25)");
    }

    @Test
    void aRecordNamesTheJobItWas() {
        InspectionRecord walked = record("w1");
        assertThat(walked.code()).isEqualTo("W1");
        assertThat(walked.job().description()).startsWith("walk the towers");
    }

    @Test
    void aRecordIsSigned() {
        assertThatThrownBy(() -> InspectionRecord.of(JOHN_PAUL, "W1", DAY, "  "))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("W1 is signed by whoever did it");
    }

    @Test
    void aJobThatRunsOnTheMeterNeedsAMeterReading() {
        assertThatThrownBy(() -> record("H1"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("H1 runs on the hour meter and none was recorded");
        InspectionRecord changed =
                record("H1", DAY, List.of(), Optional.of(new BigDecimal("42000.0")), Optional.empty(), JOHN_PAUL);
        assertThat(changed.hours()).contains(new BigDecimal("42000.0"));
    }

    @Test
    void aJobThatRunsOnTheCounterNeedsACounterReading() {
        assertThatThrownBy(() -> record("C1"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("C1 runs on the cycle counter and none was recorded");
        InspectionRecord changed = record("C1", DAY, List.of(), Optional.empty(), Optional.of(1_000_000L), JOHN_PAUL);
        assertThat(changed.cycles()).contains(1_000_000L);
    }

    @Test
    void aDefectCannotPredateTheInspectionThatFoundIt() {
        assertThatThrownBy(() -> record(List.of(defect(LocalDate.of(2025, 1, 1)))))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a defect cannot be raised before the inspection that found it");
    }

    @Test
    void aCleanRecordSaysSo() {
        assertThat(record().wasClean()).isTrue();
        assertThat(record(List.of(defect(DefectLevel.OBSERVATION))).wasClean()).isTrue();
        assertThat(record(List.of(defect())).wasClean()).isFalse();
    }

    @Test
    void theSeriousDefectsArePickedOut() {
        InspectionRecord walked = record(List.of(
                defect("JP-T4", DefectLevel.DEFECT, "sheave liner cracked"),
                defect("JP-T7", DefectLevel.SERIOUS, "crossarm bolt sheared")));
        assertThat(walked.serious()).hasSize(1);
        assertThat(walked.openSerious()).hasSize(1);
        assertThat(walked.openDefects()).hasSize(2);
    }

    @Test
    void aSeriousDefectAlreadyClearedNoLongerCounts() {
        InspectionRecord walked = record(List.of(defect(
                "JP-T7", DefectLevel.SERIOUS, "crossarm bolt sheared", DAY, Optional.of(LocalDate.of(2025, 1, 16)))));
        assertThat(walked.serious()).hasSize(1);
        assertThat(walked.openSerious()).isEmpty();
    }

    @Test
    void aRecordPrintsAOneLiner() {
        assertThat(record()).hasToString("JP W1 on 2025-01-15, clean");
        assertThat(record(List.of(defect()))).hasToString("JP W1 on 2025-01-15, 1 written up");
    }

    @Test
    void aBookBelongsToOneMachine() {
        InspectionRecord elsewhere = record("W1", DAY, List.of(), Optional.empty(), Optional.empty(), STRAWBERRY);
        assertThatThrownBy(() -> RecordBook.of(JOHN_PAUL, List.of(elsewhere)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("SB does not belong in a JP book");
    }

    @Test
    void aBookSortsByDayAndFindsTheLastOfAJob() {
        RecordBook book = RecordBook.of(
                JOHN_PAUL,
                List.of(
                        record("W1", LocalDate.of(2025, 1, 8)),
                        record("W1", LocalDate.of(2025, 1, 15)),
                        record("M1", LocalDate.of(2025, 1, 10))));
        assertThat(book.size()).isEqualTo(3);
        assertThat(book.forCode("W1")).hasSize(2);
        Optional<InspectionRecord> last = book.lastDone("W1");
        assertThat(last).isPresent();
        assertThat(last.get().done()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(book.lastDone("A1")).isEmpty();
    }

    @Test
    void theOpenDefectsAcrossTheWholeBook() {
        LocalDate earlier = LocalDate.of(2025, 1, 8);
        RecordBook book = RecordBook.of(
                JOHN_PAUL,
                List.of(
                        record("W1", earlier, List.of(defect(earlier))),
                        record("W1", DAY, List.of(defect("JP-T7", DefectLevel.SERIOUS, "bolt sheared")))));
        assertThat(book.openDefects()).hasSize(2);
        assertThat(book.openSerious()).hasSize(1);
        assertThat(book.defectsAt("JP-T4")).hasSize(1);
        assertThat(book.defectsAt("JP-T9")).isEmpty();
    }

    @Test
    void addingARecordMakesANewBook() {
        RecordBook book = RecordBook.of(JOHN_PAUL);
        RecordBook longer = book.withRecord(record());
        assertThat(book.size()).isZero();
        assertThat(longer.size()).isEqualTo(1);
    }
}
