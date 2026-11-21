package io.github.rkaushikdev.sheaveline.persistence.mappers;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.models.DayLogRow;
import io.github.rkaushikdev.sheaveline.persistence.models.PreOpResultRow;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The day on the hill turned into rows and back, and the rows it refuses to read. */
class OpsMapperTest {

    private static final Lift LIFT = Hill.johnPaul();
    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");

    private static DayLog day() {
        List<CheckResult> results = new ArrayList<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(LIFT)) {
                results.add(new CheckResult(item, CheckOutcome.PASS, "R Skaggs", "all good"));
            }
        }
        return DayLog.start(LIFT.code(), DAY, WINDOW, new PreOpSheet(LIFT, DAY, results, "R Skaggs", "D Chin"))
                .note(TimeOfDay.parse("08:40"), "line ridden, all clear", "R Skaggs")
                .openAt(TimeOfDay.parse("09:00"), "D Chin")
                .closeAt(TimeOfDay.parse("16:22"), "D Chin");
    }

    private static DayLogRow unsignedRow() {
        return new DayLogRow(LIFT.code(), DAY, WINDOW.firstLoad(), WINDOW.lastLoad(), WINDOW.sweepBy());
    }

    @Test
    void aDayComesBackWithItsSheetItsLinesAndItsHours() {
        DayLog before = day();
        DayLog after = OpsMapper.dayLogFromRow(OpsMapper.dayLogToRow(before), LIFT);
        assertThat(after.window()).isEqualTo(WINDOW);
        assertThat(after.entries()).isEqualTo(before.entries());
        assertThat(after.opened()).isEqualTo(before.opened());
        assertThat(after.closed()).isEqualTo(before.closed());
        assertThat(after.stops()).isEmpty();
        assertThat(after.meter()).isEmpty();
        PreOpSheet sheet = after.sheet().orElseThrow();
        assertThat(sheet.results())
                .containsExactlyInAnyOrderElementsOf(
                        before.sheet().orElseThrow().results());
        assertThat(sheet.operator()).isEqualTo("D Chin");
    }

    @Test
    void aDayIsReadBackOnlyAgainstItsOwnMachine() {
        DayLogRow row = OpsMapper.dayLogToRow(day());
        assertThatThrownBy(() -> OpsMapper.dayLogFromRow(row, Hill.strawberry()))
                .isInstanceOf(StorageError.class)
                .hasMessage("that log is for JP and not SB");
    }

    @Test
    void aSheetWithChecksOnItAndNobodySigningIsRefused() {
        DayLogRow row = unsignedRow();
        row.addCheck(new PreOpResultRow("A1", CheckOutcome.PASS, "R Skaggs"));
        row.setMechanic("R Skaggs");
        assertThatThrownBy(() -> OpsMapper.dayLogFromRow(row, LIFT))
                .isInstanceOf(StorageError.class)
                .hasMessage("the sheet for 2025-01-15 has checks on it but nobody signed it");
    }

    @Test
    void aCheckTheMachineDoesNotCarryIsRefused() {
        DayLogRow row = unsignedRow();
        row.setMechanic("R Skaggs");
        row.setOperator("D Chin");
        row.addCheck(new PreOpResultRow("Z9", CheckOutcome.PASS, "R Skaggs"));
        assertThatThrownBy(() -> OpsMapper.dayLogFromRow(row, LIFT))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no check called Z9 on a detachable chairlift");
    }

    @Test
    void aStopComesBackWithItsRestartAndOnlyOnItsOwnMachine() {
        StopEvent stop = new StopEvent(
                JOHN_PAUL,
                StopKind.DEROPEMENT,
                LocalDateTime.of(2025, 1, 15, 11, 30),
                "D Chin",
                "grip off at tower four",
                Optional.of(LocalDateTime.of(2025, 1, 15, 13, 15)),
                Optional.of("R Skaggs"),
                true);
        assertThat(OpsMapper.stopFromRow(OpsMapper.stopToRow(stop))).isEqualTo(stop);
        StopEvent elsewhere =
                new StopEvent(STRAWBERRY, StopKind.OPERATOR, LocalDateTime.of(2025, 1, 15, 12, 0), "T Woo");
        assertThatThrownBy(() -> OpsMapper.stopLogFromRows(
                        JOHN_PAUL, List.of(OpsMapper.stopToRow(stop), OpsMapper.stopToRow(elsewhere))))
                .isInstanceOf(StorageError.class)
                .hasMessage("that stop is on SB and not JP");
    }

    @Test
    void aHoldAndAMeterReadingComeBackAsTheyWent() {
        HoldRecord hold = new HoldRecord(
                        JOHN_PAUL, HoldReason.GUST, LocalDateTime.of(2025, 1, 15, 10, 0), "D Chin", "gusting 58")
                .closed(LocalDateTime.of(2025, 1, 15, 11, 20), "D Chin");
        assertThat(OpsMapper.holdFromRow(OpsMapper.holdToRow(hold))).isEqualTo(hold);
        MeterReading reading = new MeterReading(JOHN_PAUL, DAY, new BigDecimal("40120.5"), 1_402_000, "R Skaggs", true);
        assertThat(OpsMapper.meterFromRow(OpsMapper.meterToRow(reading))).isEqualTo(reading);
    }
}
