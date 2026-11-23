package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Ropes.ROPE;
import static io.github.rkaushikdev.sheaveline.Ropes.corrosion;
import static io.github.rkaushikdev.sheaveline.Ropes.fault;
import static io.github.rkaushikdev.sheaveline.Ropes.loss;
import static io.github.rkaushikdev.sheaveline.Ropes.magnetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MrtTest {

    private static HaulRope otherRope() {
        return new HaulRope(RopeSerial.parse("BRI-2019-00777"), ROPE.spec(), ROPE.installed(), ROPE.length(), 1);
    }

    private static MagneticTest magneticTest(HaulRope rope, LocalDate taken, Indication... indications) {
        return MagneticTest.of(rope, taken, "Wire Rope Diagnostics", "LMA-250", List.of(indications));
    }

    @Test
    void aLossIndicationCarriesAPercentage() {
        Indication mark = loss(1200, "4.5");
        assertThat(mark.kind()).isSameAs(IndicationKind.LOSS_OF_AREA);
        assertThat(mark.magnitude()).isEqualByComparingTo("4.5");
        assertThat(mark).hasToString("4.5% loss of metallic area at 1200 ft");
    }

    @Test
    void aLocalFaultIsCountedInWholeWires() {
        Indication mark = fault(2400, 3);
        assertThat(mark.kind()).isSameAs(IndicationKind.LOCAL_FAULT);
        assertThat(mark.kind().isMeasuredAsPercent()).isFalse();
        assertThat(mark).hasToString("3 wire local fault at 2400 ft");
    }

    @Test
    void anIndicationChecksItsOwnNumbers() {
        assertThatThrownBy(() -> Indication.loss(Length.ofFeet(-1), "3")).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> Indication.loss(Length.ofFeet(0), "0")).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> Indication.loss(Length.ofFeet(0), "140")).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new Indication(Length.ofFeet(0), IndicationKind.LOCAL_FAULT, new BigDecimal("2.5")))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aTestNamesTheOutfitAndTheInstrument() {
        assertThatThrownBy(() -> MagneticTest.of(ROPE, LocalDate.of(2025, 1, 15), " ", "LMA-250"))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> MagneticTest.of(ROPE, LocalDate.of(2025, 1, 15), "Wire Rope Diagnostics", " "))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aTestCannotPredateTheRopeOrRunPastItsEnd() {
        assertThatThrownBy(() -> MagneticTest.of(ROPE, LocalDate.of(2015, 1, 1), "WRD", "LMA-250"))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> magnetic(loss(12000, "2"))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void indicationsCanBePulledOutByKind() {
        MagneticTest walked = magnetic(loss(600, "2.0"), fault(2400, 3));
        assertThat(walked.ofKind(IndicationKind.LOSS_OF_AREA)).hasSize(1);
        assertThat(walked.ofKind(IndicationKind.LOCAL_FAULT)).hasSize(1);
        assertThat(walked.ofKind(IndicationKind.CORROSION)).isEmpty();
    }

    @Test
    void faultsInsideOneLayAddUpAndFaultsALayApartDoNot() {
        Length lay = ROPE.spec().layLength();
        Indication first = Indication.fault(Length.ofFeet(2400), 2);
        Indication close = Indication.fault(Length.ofFeet(2400).plus(lay), 2);
        Indication apart = Indication.fault(Length.ofFeet(2400).plus(lay).plus(Length.ofMils(1)), 2);
        assertThat(magnetic(first, close).wiresInOneLay()).isEqualTo(4);
        assertThat(magnetic(first, apart).wiresInOneLay()).isEqualTo(2);
        assertThat(magnetic(first, close).largestFault()).isEqualTo(2);
    }

    @Test
    void theLayGroupIsReadInStationOrderWhateverOrderTheReportGaveIt() {
        MagneticTest walked = magnetic(fault(4000, 1), fault(600, 3), loss(2400, "5"), fault(4000, 2));
        assertThat(walked.wiresInOneLay()).isEqualTo(3);
        assertThat(magnetic(loss(600, "2.0")).wiresInOneLay()).isZero();
    }

    @Test
    void theWorstCorrosionIsReadOffItsOwnMarks() {
        MagneticTest walked = magnetic(corrosion(600, "1.5"), loss(1200, "8"), corrosion(4000, "3.25"));
        assertThat(walked.peakCorrosion()).isEqualByComparingTo("3.25");
        assertThat(walked.peakLoss()).isEqualByComparingTo("8");
        assertThat(magnetic(loss(600, "2")).peakCorrosion()).isZero();
        assertThat(corrosion(600, "1.5")).hasToString("1.5% corrosion at 600 ft");
    }

    @Test
    void anUntestedRopeIsDueFourYearsAfterItWentOn() {
        RetestSchedule schedule = new RetestSchedule(ROPE);
        assertThat(schedule.nextDue(LocalDate.of(2021, 1, 1))).isEqualTo(LocalDate.of(2023, 10, 4));
    }

    @Test
    void aRopeUnderTenGetsTwoYearsBetweenTests() {
        RetestSchedule schedule = new RetestSchedule(ROPE, LocalDate.of(2023, 10, 4));
        assertThat(schedule.intervalYears(LocalDate.of(2025, 1, 15))).isEqualTo(2);
        assertThat(schedule.nextDue(LocalDate.of(2025, 1, 15))).isEqualTo(LocalDate.of(2025, 10, 4));
    }

    @Test
    void aRopePastTenGetsOneYear() {
        RetestSchedule schedule = new RetestSchedule(ROPE, LocalDate.of(2029, 11, 1));
        assertThat(schedule.intervalYears(LocalDate.of(2030, 6, 1))).isEqualTo(1);
        assertThat(schedule.nextDue(LocalDate.of(2030, 6, 1))).isEqualTo(LocalDate.of(2030, 11, 1));
    }

    @Test
    void theIntervalTurnsOnTheAgeAtTheTimeOfAsking() {
        RetestSchedule schedule = new RetestSchedule(ROPE, LocalDate.of(2029, 6, 1));
        assertThat(schedule.nextDue(LocalDate.of(2029, 7, 1))).isEqualTo(LocalDate.of(2031, 6, 1));
        assertThat(schedule.nextDue(LocalDate.of(2030, 1, 1))).isEqualTo(LocalDate.of(2030, 6, 1));
    }

    @Test
    void anOverdueRopeSaysSo() {
        RetestSchedule schedule = new RetestSchedule(ROPE, LocalDate.of(2022, 10, 4));
        assertThat(schedule.isOverdue(LocalDate.of(2025, 1, 15))).isTrue();
        assertThat(schedule.isOverdue(LocalDate.of(2024, 6, 1))).isFalse();
    }

    @Test
    void theDaysUntilDueCountDown() {
        RetestSchedule schedule = new RetestSchedule(ROPE, LocalDate.of(2024, 10, 4));
        assertThat(schedule.daysUntilDue(LocalDate.of(2026, 10, 3))).isEqualTo(1);
        assertThat(schedule.daysUntilDue(LocalDate.of(2026, 10, 5))).isEqualTo(-1);
    }

    @Test
    void recordingATestMovesTheScheduleOn() {
        RetestSchedule schedule = new RetestSchedule(ROPE).after(magneticTest(ROPE, LocalDate.of(2024, 3, 5)));
        assertThat(schedule.lastTest()).contains(LocalDate.of(2024, 3, 5));
        assertThat(schedule.nextDue(LocalDate.of(2024, 6, 1))).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void aTestOfAnotherRopeDoesNotMoveThisSchedule() {
        assertThatThrownBy(() -> new RetestSchedule(ROPE).after(magneticTest(otherRope(), LocalDate.of(2025, 1, 15))))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aLastTestBeforeTheRopeWentOnIsRefused() {
        assertThatThrownBy(() -> new RetestSchedule(ROPE, LocalDate.of(2015, 1, 1)))
                .isInstanceOf(RopeworkError.class);
    }
}
