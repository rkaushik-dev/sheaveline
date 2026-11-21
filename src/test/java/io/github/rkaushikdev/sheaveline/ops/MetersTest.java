package io.github.rkaushikdev.sheaveline.ops;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetersTest {

    private static MeterReading reading(int day, String hours, long cycles, boolean replaced, LiftCode lift) {
        return new MeterReading(lift, LocalDate.of(2025, 1, day), new BigDecimal(hours), cycles, "M Vetter", replaced);
    }

    private static MeterReading reading(int day, String hours, long cycles, boolean replaced) {
        return reading(day, hours, cycles, replaced, JOHN_PAUL);
    }

    private static MeterReading reading(int day, String hours, long cycles) {
        return reading(day, hours, cycles, false);
    }

    private static MeterReading reading(int day, String hours) {
        return reading(day, hours, 1_000_000);
    }

    private static MeterReading reading(int day) {
        return reading(day, "42000.0");
    }

    private static MeterReading reading() {
        return reading(1);
    }

    private static MeterHistory history(MeterReading... readings) {
        return MeterHistory.of(JOHN_PAUL, readings.length == 0 ? List.of(reading()) : List.of(readings));
    }

    @Test
    void aReadingIsSignedAndInsideTheDial() {
        assertThatThrownBy(
                        () -> new MeterReading(JOHN_PAUL, LocalDate.of(2025, 1, 1), new BigDecimal("100.0"), 5, "  "))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a meter reading is signed by whoever took it");
        assertThatThrownBy(() -> reading(1, "-1"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("an hour meter reads under 100000.0");
        assertThatThrownBy(() -> reading(1, "100000.0"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("an hour meter reads under 100000.0");
        assertThatThrownBy(() -> reading(1, "42000.0", -1))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a cycle counter does not read negative");
    }

    @Test
    void aReadingConvertsToRunningTime() {
        assertThat(reading(1, "10.5").runningTime()).isEqualTo(Duration.ofSeconds(37800));
    }

    @Test
    void aReadingPrintsForTheBook() {
        assertThat(reading(1, "42000.0")).hasToString("JP 2025-01-01, 42000.0 hours, 1,000,000 cycles");
    }

    @Test
    void aHistoryBelongsToOneMachineAndOneReadingADay() {
        assertThatThrownBy(
                        () -> MeterHistory.of(JOHN_PAUL, List.of(reading(1, "42000.0", 1_000_000, false, STRAWBERRY))))
                .isInstanceOf(OperationsError.class)
                .hasMessage("SB does not belong in a JP history");
        assertThatThrownBy(() -> history(reading(1), reading(1, "42001.0")))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a machine is read once a day");
        assertThatThrownBy(() -> MeterHistory.of(JOHN_PAUL, List.of()))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a meter history has readings in it");
    }

    @Test
    void aHistorySortsByDay() {
        MeterHistory held = history(reading(3, "42020.0"), reading(1, "42000.0"));
        assertThat(held.readings()).extracting(one -> one.day().getDayOfMonth()).containsExactly(1, 3);
        assertThat(held.first().hours()).isEqualByComparingTo("42000.0");
        assertThat(held.latest().hours()).isEqualByComparingTo("42020.0");
    }

    @Test
    void theOrdinaryStepIsTheDifference() {
        MeterHistory held = history(reading(1, "42000.0"), reading(2, "42009.5"));
        assertThat(held.hoursRun()).isEqualByComparingTo("9.5");
    }

    @Test
    void aWrappedMeterIsReadAsAWrapNotAHundredThousandHours() {
        MeterHistory held = history(reading(1, "99995.0", 1_000_000), reading(2, "4.5", 1_000_100));
        assertThat(held.hoursRun()).isEqualByComparingTo("9.5");
        assertThat(held.rollovers()).isEqualTo(1);
    }

    @Test
    void aReplacedMeterIsReadFromZero() {
        MeterHistory held = history(reading(1, "42000.0", 1_000_000), reading(2, "9.5", 100, true));
        assertThat(held.hoursRun()).isEqualByComparingTo("9.5");
        assertThat(held.rollovers()).isZero();
        assertThat(held.replacements()).hasSize(1);
    }

    @Test
    void aCycleCounterThatFallsWithoutAReplacementIsAnError() {
        MeterHistory held = history(reading(1, "42000.0", 1_000_000), reading(2, "42009.5", 900_000));
        assertThatThrownBy(held::cyclesRun)
                .isInstanceOf(OperationsError.class)
                .hasMessage("the cycle counter fell without the meter being replaced");
    }

    @Test
    void cyclesAddUpAcrossAReplacement() {
        MeterHistory held = history(
                reading(1, "42000.0", 1_000_000), reading(2, "42009.5", 1_000_400), reading(3, "9.5", 300, true));
        assertThat(held.cyclesRun()).isEqualTo(700);
    }

    @Test
    void twoReadingsTheWrongWayRoundAreRefused() {
        MeterHistory held = history(reading(1), reading(2, "42009.5"));
        MeterReading earlier = held.readings().get(0);
        MeterReading later = held.readings().get(1);
        assertThatThrownBy(() -> held.step(later, earlier))
                .isInstanceOf(OperationsError.class)
                .hasMessage("those readings are the wrong way round");
        assertThatThrownBy(() -> held.cycleStep(later, earlier))
                .isInstanceOf(OperationsError.class)
                .hasMessage("those readings are the wrong way round");
    }

    @Test
    void aDaysRunningNeedsTheDayBeforeIt() {
        MeterHistory held = history(reading(1, "42000.0"), reading(2, "42009.5"), reading(3, "42018.0"));
        assertThat(held.hoursOn(LocalDate.of(2025, 1, 2))).isEqualByComparingTo("9.5");
        assertThat(held.hoursOn(LocalDate.of(2025, 1, 3))).isEqualByComparingTo("8.5");
        assertThatThrownBy(() -> held.hoursOn(LocalDate.of(2025, 1, 1)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("2025-01-01 is the first reading, so it has no day");
    }

    @Test
    void aDayThatWasNotReadHasNoAnswer() {
        assertThatThrownBy(() -> history().on(LocalDate.of(2025, 2, 1)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("JP was not read on 2025-02-01");
    }

    @Test
    void ropeTravelComesOffTheHoursAndTheSpeed() {
        MeterHistory held = history(reading(1, "42000.0"), reading(2, "42010.0"));
        Length travelled = held.ropeTravel(Speed.ofFeetPerMinute(1000));
        assertThat(travelled).isEqualTo(Length.ofFeet(600_000));
    }

    @Test
    void aSingleReadingHistoryHasRunNothingYet() {
        MeterHistory held = history();
        assertThat(held.hoursRun()).isEqualByComparingTo("0");
        assertThat(held.cyclesRun()).isZero();
        assertThat(held.size()).isEqualTo(1);
    }

    @Test
    void addingAReadingMakesANewHistory() {
        MeterHistory held = history(reading(1));
        MeterHistory longer = held.withReading(reading(2, "42009.5"));
        assertThat(held.size()).isEqualTo(1);
        assertThat(longer.size()).isEqualTo(2);
        assertThat(longer.hoursRun()).isEqualByComparingTo("9.5");
    }
}
