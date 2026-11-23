package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.EntryKind;
import io.github.rkaushikdev.sheaveline.ops.MeterHistory;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.OperationsError;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.ops.StopLog;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepoDayLogTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");
    private static final LocalDateTime HALF_ELEVEN = LocalDateTime.of(2025, 1, 15, 11, 30);

    private Session session;
    private DayLogRepository logs;

    @BeforeEach
    void bothMachinesOnFile() {
        session = TestDatabase.fresh().session();
        LiftRepository lifts = new LiftRepository(session);
        lifts.add(johnPaul());
        lifts.add(strawberry());
        session.commit();
        logs = new DayLogRepository(session);
    }

    @AfterEach
    void closeIt() {
        session.close();
    }

    private static PreOpSheet sheet(Lift machine, LocalDate day) {
        List<CheckResult> results = new ArrayList<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(machine)) {
                results.add(new CheckResult(item, CheckOutcome.PASS, "R Skaggs"));
            }
        }
        return new PreOpSheet(machine, day, results, "R Skaggs", "D Chin");
    }

    private static DayLog log(LocalDate day, Lift machine, boolean withSheet) {
        DayLog started = withSheet
                ? DayLog.start(machine.code(), day, WINDOW, sheet(machine, day))
                : DayLog.start(machine.code(), day, WINDOW);
        return started.note(TimeOfDay.parse("08:40"), "line ridden, all clear", "R Skaggs");
    }

    private static DayLog log(LocalDate day) {
        return log(day, johnPaul(), true);
    }

    private static DayLog log() {
        return log(DAY);
    }

    private static StopEvent stop(LocalDateTime at, StopKind kind, LiftCode lift) {
        return new StopEvent(lift, kind, at, "D Chin");
    }

    private static StopEvent stop() {
        return stop(HALF_ELEVEN, StopKind.OPERATOR, JOHN_PAUL);
    }

    private static StopEvent noted(StopKind kind, String note) {
        return new StopEvent(JOHN_PAUL, kind, HALF_ELEVEN, "D Chin", note);
    }

    private static StopEvent restarted(
            LocalDateTime at, StopKind kind, LocalDateTime restarted, String by, boolean lineChecked) {
        return new StopEvent(JOHN_PAUL, kind, at, "D Chin", "", Optional.of(restarted), Optional.of(by), lineChecked);
    }

    private static MeterReading reading(LocalDate day, String hours, long cycles) {
        return new MeterReading(JOHN_PAUL, day, new BigDecimal(hours), cycles, "R Skaggs");
    }

    @Test
    void aDayGoesInAndComesBackOut() {
        logs.add(log());
        session.commit();
        DayLog found = logs.get(JOHN_PAUL, DAY);
        assertThat(found.day()).isEqualTo(DAY);
        assertThat(found.window()).isEqualTo(WINDOW);
        assertThat(found.entries()).hasSize(1);
        assertThat(found.entries().get(0).kind()).isSameAs(EntryKind.NOTE);
    }

    @Test
    void theSheetComesBackWithTheDayAndKnowsItsMachine() {
        logs.add(log());
        session.commit();
        DayLog found = logs.get(JOHN_PAUL, DAY);
        assertThat(found.sheet()).isPresent();
        assertThat(found.sheet().get().mechanic()).isEqualTo("R Skaggs");
        assertThat(found.sheet().get().isComplete()).isTrue();
        assertThat(found.mayOpen()).isTrue();
    }

    @Test
    void aDayWithNoSheetComesBackWithoutOne() {
        logs.add(log(DAY, johnPaul(), false));
        session.commit();
        DayLog found = logs.get(JOHN_PAUL, DAY);
        assertThat(found.sheet()).isEmpty();
        assertThat(found.mayOpen()).isFalse();
    }

    @Test
    void theSameDayIsNotLoggedTwice() {
        logs.add(log());
        session.commit();
        assertThatThrownBy(() -> logs.add(log()))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP already has a log for 2025-01-15");
    }

    @Test
    void aDayNobodyWroteUp() {
        assertThatThrownBy(() -> logs.get(JOHN_PAUL, DAY))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no log for JP on 2025-01-15");
        assertThat(logs.findLog(JOHN_PAUL, DAY)).isEmpty();
    }

    @Test
    void theDaysAMachineHasBeenWrittenUp() {
        for (int offset = 0; offset < 3; offset++) {
            logs.add(log(DAY.plusDays(offset)));
        }
        logs.add(log(DAY, strawberry(), true));
        session.commit();
        assertThat(logs.daysLogged(JOHN_PAUL)).containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(2));
        assertThat(logs.daysLogged(STRAWBERRY)).containsExactly(DAY);
    }

    @Test
    void aStretchOfDaysComesBackInOrder() {
        for (int offset : new int[] {2, 0, 1}) {
            logs.add(log(DAY.plusDays(offset)));
        }
        session.commit();
        List<DayLog> found = logs.between(JOHN_PAUL, DAY, DAY.plusDays(1));
        assertThat(found.stream().map(DayLog::day)).containsExactly(DAY, DAY.plusDays(1));
    }

    @Test
    void rewritingADayReplacesWhatWasThere() {
        logs.add(log());
        session.commit();
        DayLog signed = logs.get(JOHN_PAUL, DAY).note(TimeOfDay.parse("17:00"), "swept at 16:22", "D Chin");
        logs.replace(signed);
        session.commit();
        assertThat(logs.get(JOHN_PAUL, DAY).entries()).hasSize(2);
    }

    @Test
    void rewritingADayNobodyWroteUp() {
        assertThatThrownBy(() -> logs.replace(log()))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no log for JP on 2025-01-15");
    }

    @Test
    void aStopGoesInAndComesBackAgainstTheDayItTouched() {
        logs.add(log());
        logs.stop(stop());
        session.commit();
        DayLog found = logs.get(JOHN_PAUL, DAY);
        assertThat(found.stops()).isPresent();
        assertThat(found.stops().get().size()).isEqualTo(1);
        assertThat(found.stops().get().stops().get(0).kind()).isSameAs(StopKind.OPERATOR);
    }

    @Test
    void twoStopsCannotStartAtTheSameMoment() {
        logs.stop(stop());
        session.commit();
        assertThatThrownBy(() -> logs.stop(stop()))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP is already stopped at 11:30");
    }

    @Test
    void restartingWritesTheTimeBackAgainstTheStop() {
        logs.stop(noted(StopKind.DEROPEMENT, "grip off at tower four, line walked"));
        session.commit();
        StopEvent restarted =
                logs.restart(JOHN_PAUL, HALF_ELEVEN, LocalDateTime.of(2025, 1, 15, 13, 15), "R Skaggs", true);
        session.commit();
        assertThat(restarted.restartedBy()).contains("R Skaggs");
        assertThat(restarted.lineChecked()).isTrue();
        assertThat(logs.openStops(JOHN_PAUL)).isEmpty();
    }

    @Test
    void restartingAStopNobodyCalled() {
        assertThatThrownBy(() -> logs.restart(
                        JOHN_PAUL,
                        LocalDateTime.of(2025, 1, 15, 7, 0),
                        LocalDateTime.of(2025, 1, 15, 8, 0),
                        "R Skaggs"))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no stop on JP at 07:00");
    }

    @Test
    void aStopThatRanOvernightBelongsToBothDays() {
        logs.stop(restarted(
                LocalDateTime.of(2025, 1, 15, 16, 5),
                StopKind.POWER,
                LocalDateTime.of(2025, 1, 16, 8, 40),
                "R Skaggs",
                true));
        session.commit();
        assertThat(logs.stopsOn(JOHN_PAUL, DAY).size()).isEqualTo(1);
        assertThat(logs.stopsOn(JOHN_PAUL, LocalDate.of(2025, 1, 16)).size()).isEqualTo(1);
        assertThat(logs.stopsOn(JOHN_PAUL, LocalDate.of(2025, 1, 17)).size()).isZero();
    }

    @Test
    void aStopStillOpenBelongsToEveryDaySince() {
        logs.stop(stop(LocalDateTime.of(2025, 1, 15, 16, 5), StopKind.SAFETY_CIRCUIT, JOHN_PAUL));
        session.commit();
        assertThat(logs.stopsOn(JOHN_PAUL, LocalDate.of(2025, 1, 20)).size()).isEqualTo(1);
        assertThat(logs.openStops().stream().map(StopEvent::lift)).containsExactly(JOHN_PAUL);
    }

    @Test
    void oneMachinesStopsDoNotLandInAnothersLog() {
        logs.stop(stop());
        logs.stop(stop(LocalDateTime.of(2025, 1, 15, 12, 0), StopKind.OPERATOR, STRAWBERRY));
        session.commit();
        assertThat(logs.stopsOn(JOHN_PAUL, DAY).size()).isEqualTo(1);
        assertThat(logs.stopsOn(STRAWBERRY, DAY).size()).isEqualTo(1);
    }

    @Test
    void theStopsThatComeBackAreALogThatCanBeTotalled() {
        logs.stop(restarted(
                LocalDateTime.of(2025, 1, 15, 10, 0),
                StopKind.WEATHER,
                LocalDateTime.of(2025, 1, 15, 10, 30),
                "D Chin",
                false));
        session.commit();
        StopLog held = logs.stopsOn(JOHN_PAUL, DAY);
        assertThat(held.downtime().minutes()).isEqualByComparingTo("30");
        assertThat(held.chargeableDowntime().isZero()).isTrue();
    }

    @Test
    void aDeropementWillNotRestartUntilSomebodyHasWalkedTheLine() {
        logs.stop(noted(StopKind.DEROPEMENT, "grip off at tower four"));
        session.commit();
        assertThatThrownBy(
                        () -> logs.restart(JOHN_PAUL, HALF_ELEVEN, LocalDateTime.of(2025, 1, 15, 13, 15), "R Skaggs"))
                .isInstanceOf(OperationsError.class);
    }

    @Test
    void aHoldIsCalledAndCleared() {
        logs.callHold(new HoldRecord(
                JOHN_PAUL,
                HoldReason.GUST,
                LocalDateTime.of(2025, 1, 15, 10, 0),
                "D Chin",
                "gusting 58 across the line"));
        session.commit();
        assertThat(logs.holds(JOHN_PAUL).isHeld(JOHN_PAUL)).isTrue();
        HoldRecord cleared = logs.clearHold(
                JOHN_PAUL, LocalDateTime.of(2025, 1, 15, 10, 0), LocalDateTime.of(2025, 1, 15, 11, 20), "D Chin");
        session.commit();
        assertThat(cleared.length().minutes()).isEqualByComparingTo("80");
        assertThat(logs.holds(JOHN_PAUL).isHeld(JOHN_PAUL)).isFalse();
    }

    @Test
    void clearingAHoldNobodyCalled() {
        assertThatThrownBy(() -> logs.clearHold(
                        JOHN_PAUL, LocalDateTime.of(2025, 1, 15, 6, 0), LocalDateTime.of(2025, 1, 15, 7, 0), "D Chin"))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no hold on JP from 06:00");
    }

    @Test
    void theHoldsComeBackAsARegisterAcrossTheHill() {
        logs.callHold(new HoldRecord(JOHN_PAUL, HoldReason.ICING, LocalDateTime.of(2025, 1, 15, 9, 0), "D Chin"));
        logs.callHold(new HoldRecord(STRAWBERRY, HoldReason.ICING, LocalDateTime.of(2025, 1, 15, 10, 0), "D Chin"));
        session.commit();
        assertThat(logs.holds().size()).isEqualTo(2);
        assertThat(logs.holds(JOHN_PAUL).size()).isEqualTo(1);
    }

    @Test
    void theMeterIsReadOnceADay() {
        MeterReading reading = reading(DAY, "40120.5", 1_402_000);
        logs.readMeter(reading);
        session.commit();
        assertThat(logs.meterOn(JOHN_PAUL, DAY)).contains(reading);
        assertThatThrownBy(() -> logs.readMeter(reading))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP was already read on 2025-01-15");
    }

    @Test
    void aReadingTheClockCannotHoldIsNotWritten() {
        assertThat(logs.readMeter(reading(DAY, "40120.5", 1_402_000))).isEqualByComparingTo("0");
        assertThat(logs.readMeter(reading(DAY.plusDays(1), "40130.0", 1_406_000)))
                .isEqualByComparingTo("9.5");
        session.commit();
        assertThatThrownBy(() -> logs.readMeter(reading(DAY.plusDays(2), "40129.0", 1_410_000)))
                .isInstanceOf(OperationsError.class);
        assertThat(logs.meterOn(JOHN_PAUL, DAY.plusDays(2))).isEmpty();
    }

    @Test
    void aDayNobodyReadTheMeterOn() {
        assertThat(logs.meterOn(JOHN_PAUL, DAY)).isEmpty();
    }

    @Test
    void theReadingsComeBackAsAHistoryThatCanBeStepped() {
        List<String> hours = List.of("40120.5", "40128.0", "40135.5");
        for (int offset = 0; offset < hours.size(); offset++) {
            logs.readMeter(reading(DAY.plusDays(offset), hours.get(offset), 1_402_000 + offset * 4000L));
        }
        session.commit();
        MeterHistory history = logs.meters(JOHN_PAUL).orElseThrow();
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.hoursRun()).isEqualByComparingTo("15.0");
        assertThat(history.cyclesRun()).isEqualTo(8000);
    }

    @Test
    void theMeterReadingLandsWithTheDayWhenTheLogCarriesOne() {
        MeterReading reading = reading(DAY, "40120.5", 1_402_000);
        logs.add(log().withMeter(reading));
        session.commit();
        DayLog found = logs.get(JOHN_PAUL, DAY);
        assertThat(found.meter()).contains(reading);
    }
}
