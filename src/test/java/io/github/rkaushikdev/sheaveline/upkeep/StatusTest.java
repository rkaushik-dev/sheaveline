package io.github.rkaushikdev.sheaveline.upkeep;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.line.GeometryVerdict;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.ropework.WearVerdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatusTest {

    private static final LocalDate BUILT = LocalDate.of(2024, 11, 1);
    private static final LocalDate ON = LocalDate.of(2025, 1, 15);

    private static InspectionRecord record(
            String code, LocalDate done, List<Defect> defects, Optional<BigDecimal> hours, Optional<Long> cycles) {
        return InspectionRecord.of(JOHN_PAUL, code, done, "M Vetter", hours, cycles, defects);
    }

    private static InspectionRecord walk(Defect defect) {
        return record("W1", LocalDate.of(2025, 1, 14), List.of(defect), Optional.empty(), Optional.empty());
    }

    private static UpkeepSchedule currentLadder(InspectionRecord... extra) {
        List<Requirement> jobs =
                new UpkeepSchedule(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, BUILT, RecordBook.of(JOHN_PAUL)).jobs();
        List<InspectionRecord> records = new ArrayList<>();
        for (Requirement job : jobs) {
            records.add(record(
                    job.code(),
                    LocalDate.of(2025, 1, 14),
                    List.of(),
                    Optional.of(new BigDecimal("42000.0")),
                    Optional.of(1_000_000L)));
        }
        records.addAll(List.of(extra));
        return new UpkeepSchedule(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, BUILT, RecordBook.of(JOHN_PAUL, records));
    }

    private static ServiceAssessment assessment(
            UpkeepSchedule schedule, Optional<WearVerdict> rope, Optional<GeometryVerdict> geometry) {
        return new ServiceAssessment(JOHN_PAUL, ON, schedule, rope, geometry);
    }

    private static ServiceAssessment assessment(UpkeepSchedule schedule) {
        return assessment(schedule, Optional.empty(), Optional.empty());
    }

    private static ServiceAssessment assessment(WearVerdict rope, GeometryVerdict geometry) {
        return assessment(currentLadder(), Optional.of(rope), Optional.of(geometry));
    }

    private static ServiceAssessment assessment(WearVerdict rope) {
        return assessment(currentLadder(), Optional.of(rope), Optional.empty());
    }

    private static ServiceAssessment assessment(GeometryVerdict geometry) {
        return assessment(currentLadder(), Optional.empty(), Optional.of(geometry));
    }

    private static ServiceAssessment assessment() {
        return assessment(currentLadder());
    }

    @Test
    void aMachineWithNothingAgainstItIsInService() {
        ServiceAssessment read = assessment();
        assertThat(read.status()).isSameAs(ServiceStatus.IN_SERVICE);
        assertThat(read.reasons()).isEmpty();
        assertThat(read.mayOpen()).isTrue();
        assertThat(read).hasToString("JP in service");
    }

    @Test
    void aLadderFromAnotherMachineIsRefused() {
        UpkeepSchedule other = new UpkeepSchedule(STRAWBERRY, LiftClass.FIXED_CHAIR, BUILT, RecordBook.of(STRAWBERRY));
        assertThatThrownBy(() -> new ServiceAssessment(JOHN_PAUL, ON, other))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("that ladder is for SB");
    }

    @Test
    void anOverdueCriticalJobTakesTheMachineOut() {
        UpkeepSchedule thin =
                new UpkeepSchedule(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, BUILT, RecordBook.of(JOHN_PAUL));
        ServiceAssessment read = assessment(thin);
        assertThat(read.status()).isSameAs(ServiceStatus.OUT_OF_SERVICE);
        assertThat(read.mayOpen()).isFalse();
        assertThat(read.sourcesReporting()).contains("ladder");
    }

    @Test
    void anOverdueJobThatIsNotCriticalOnlyRestricts() {
        UpkeepSchedule ladder = currentLadder();
        RecordBook withoutPaint = RecordBook.of(
                JOHN_PAUL,
                ladder.book().records().stream()
                        .filter(rec -> !rec.code().equals("A5"))
                        .toList());
        ServiceAssessment read = assessment(
                new UpkeepSchedule(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, LocalDate.of(2022, 1, 1), withoutPaint));
        List<String> codes = read.reasons().stream()
                .map(reason -> reason.detail().strip().split("\\s+")[0])
                .toList();
        assertThat(codes).contains("A5");
    }

    @Test
    void anOpenSeriousDefectTakesTheMachineOut() {
        Defect serious = new Defect("JP-T7", DefectLevel.SERIOUS, "crossarm bolt sheared", LocalDate.of(2025, 1, 14));
        ServiceAssessment read = assessment(currentLadder(walk(serious)));
        assertThat(read.status()).isSameAs(ServiceStatus.OUT_OF_SERVICE);
        assertThat(read.sourcesReporting()).contains("record book");
    }

    @Test
    void aSeriousDefectAlreadyClearedDoesNotCount() {
        Defect cleared = new Defect(
                "JP-T7",
                DefectLevel.SERIOUS,
                "crossarm bolt sheared",
                LocalDate.of(2025, 1, 14),
                Optional.of(LocalDate.of(2025, 1, 15)),
                Optional.of("M Vetter"));
        ServiceAssessment read = assessment(currentLadder(walk(cleared)));
        assertThat(read.status()).isSameAs(ServiceStatus.IN_SERVICE);
    }

    @Test
    void anOrdinaryDefectDoesNotTakeTheMachineOut() {
        Defect ordinary = new Defect("JP-T4", DefectLevel.DEFECT, "liner cracked", LocalDate.of(2025, 1, 14));
        ServiceAssessment read = assessment(currentLadder(walk(ordinary)));
        assertThat(read.status()).isSameAs(ServiceStatus.IN_SERVICE);
    }

    @Test
    void aCondemnedRopeTakesTheMachineOut() {
        ServiceAssessment read = assessment(WearVerdict.CONDEMNED);
        assertThat(read.status()).isSameAs(ServiceStatus.OUT_OF_SERVICE);
        assertThat(read.sourcesReporting()).contains("rope file");
    }

    @Test
    void aRopeOnWatchOnlyRestricts() {
        ServiceAssessment read = assessment(WearVerdict.MONITOR);
        assertThat(read.status()).isSameAs(ServiceStatus.RESTRICTED);
        assertThat(read.mayOpen()).isTrue();
        assertThat(read.status().needsANoteOnTheSheet()).isTrue();
    }

    @Test
    void aServiceableRopeSaysNothing() {
        assertThat(assessment(WearVerdict.SERVICEABLE).reasons()).isEmpty();
    }

    @Test
    void anUnfitLineTakesTheMachineOut() {
        ServiceAssessment read = assessment(GeometryVerdict.UNFIT);
        assertThat(read.status()).isSameAs(ServiceStatus.OUT_OF_SERVICE);
        assertThat(read.sourcesReporting()).contains("line walk");
    }

    @Test
    void aRestrictedLineOnlyRestricts() {
        ServiceAssessment read = assessment(GeometryVerdict.RESTRICTED);
        assertThat(read.status()).isSameAs(ServiceStatus.RESTRICTED);
    }

    @Test
    void aFitLineSaysNothing() {
        assertThat(assessment(GeometryVerdict.FIT).reasons()).isEmpty();
    }

    @Test
    void theWorstOfTheFourWins() {
        ServiceAssessment read = assessment(WearVerdict.MONITOR, GeometryVerdict.UNFIT);
        assertThat(read.status()).isSameAs(ServiceStatus.OUT_OF_SERVICE);
        assertThat(read.reasons()).hasSize(2);
        assertThat(read.blocking()).hasSize(1);
    }

    @Test
    void twoSourcesCanBothRestrictWithoutTakingItOut() {
        ServiceAssessment read = assessment(WearVerdict.MONITOR, GeometryVerdict.RESTRICTED);
        assertThat(read.status()).isSameAs(ServiceStatus.RESTRICTED);
        assertThat(read.sourcesReporting()).isEqualTo(Set.of("rope file", "line walk"));
    }

    @Test
    void theReasonsCarryTheSourceThatRaisedThem() {
        ServiceAssessment read = assessment(WearVerdict.CONDEMNED);
        StatusReason reason = read.reasons().get(0);
        assertThat(reason).isInstanceOf(StatusReason.class);
        assertThat(reason).hasToString("rope file: rope condemned");
    }

    @Test
    void theStatusPrintsWithACountOfReasons() {
        ServiceAssessment read = assessment(WearVerdict.MONITOR, GeometryVerdict.RESTRICTED);
        assertThat(read).hasToString("JP restricted, 2 reasons");
    }

    @Test
    void onlyTheOutOfServiceStatusStopsThePublic() {
        assertThat(ServiceStatus.IN_SERVICE.mayCarryPublic()).isTrue();
        assertThat(ServiceStatus.RESTRICTED.mayCarryPublic()).isTrue();
        assertThat(ServiceStatus.OUT_OF_SERVICE.mayCarryPublic()).isFalse();
        assertThat(ServiceStatus.IN_SERVICE.needsANoteOnTheSheet()).isFalse();
    }
}
