package io.github.rkaushikdev.sheaveline.ops;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import io.github.rkaushikdev.sheaveline.weather.HoldRegister;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogbookTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm");

    private static PreOpSheet clearSheet(Lift machine) {
        List<CheckResult> results = PreOpSheet.STANDARD_SHEET.stream()
                .filter(item -> item.appliesTo(machine))
                .map(item -> new CheckResult(item, CheckOutcome.PASS, "M Vetter"))
                .toList();
        return new PreOpSheet(machine, DAY, results, "M Vetter", "K Ostler");
    }

    private static PreOpSheet clearSheet() {
        return clearSheet(johnPaul());
    }

    private static DayLog log(PreOpSheet sheet, HoldRegister holds, StopLog stops) {
        return DayLog.start(JOHN_PAUL, DAY, WINDOW, Optional.of(sheet), holds, List.of())
                .withStops(stops);
    }

    private static DayLog log() {
        return log(clearSheet(), HoldRegister.of(), StopLog.of(JOHN_PAUL));
    }

    private static DayLog withSheet(PreOpSheet sheet) {
        return log(sheet, HoldRegister.of(), StopLog.of(JOHN_PAUL));
    }

    private static DayLog withHolds(HoldRegister holds) {
        return log(clearSheet(), holds, StopLog.of(JOHN_PAUL));
    }

    private static DayLog withStops(StopLog stops) {
        return log(clearSheet(), HoldRegister.of(), stops);
    }

    /** A book built whole, as storage would load it, with nothing in it but the times given. */
    private static DayLog built(
            Optional<PreOpSheet> sheet,
            Optional<StopLog> stops,
            Optional<TimeOfDay> opened,
            Optional<TimeOfDay> closed) {
        return new DayLog(
                JOHN_PAUL, DAY, WINDOW, sheet, HoldRegister.of(), stops, List.of(), opened, closed, Optional.empty());
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2025, 1, 15, hour, minute);
    }

    private static LocalDateTime at(int hour) {
        return at(hour, 0);
    }

    @Test
    void aMachineWithAClearSheetAndNoHoldsMayOpen() {
        DayLog book = log();
        assertThat(book.mayOpen()).isTrue();
        assertThat(book.blockers()).isEmpty();
    }

    @Test
    void noSheetMeansNoOpening() {
        DayLog book = DayLog.start(JOHN_PAUL, DAY, WINDOW);
        assertThat(book.mayOpen()).isFalse();
        assertThat(book.blockers()).containsExactly("no pre opening sheet");
        assertThat(book).hasToString("JP 2025-01-15, never opened");
    }

    @Test
    void aFailedCriticalLineKeepsTheMachineShut() {
        Lift machine = johnPaul();
        List<CheckResult> results = PreOpSheet.STANDARD_SHEET.stream()
                .filter(item -> item.appliesTo(machine))
                .map(item -> item.code().equals("B2")
                        ? new CheckResult(item, CheckOutcome.FAIL, "M Vetter", "rope out at T4")
                        : new CheckResult(item, CheckOutcome.PASS, "M Vetter", ""))
                .toList();
        DayLog book = withSheet(new PreOpSheet(machine, DAY, results, "M Vetter", "K Ostler"));
        assertThat(book.mayOpen()).isFalse();
        assertThat(book.blockers()).contains("B2 failed");
    }

    @Test
    void anOpenWeatherHoldKeepsTheMachineShut() {
        HoldRegister holds = HoldRegister.of(List.of(new HoldRecord(JOHN_PAUL, HoldReason.GUST, at(8), "K Ostler")));
        DayLog book = withHolds(holds);
        assertThat(book.mayOpen()).isFalse();
        assertThat(book.blockers()).contains("gust hold open");
    }

    @Test
    void anUnclearedStopKeepsTheMachineShut() {
        StopLog stops =
                StopLog.of(JOHN_PAUL, List.of(new StopEvent(JOHN_PAUL, StopKind.SAFETY_CIRCUIT, at(8), "K Ostler")));
        DayLog book = withStops(stops);
        assertThat(book.mayOpen()).isFalse();
        assertThat(book.blockers()).contains("safety circuit not cleared");
    }

    @Test
    void openingWritesALineInTheBook() {
        DayLog book = log().openAt(TimeOfDay.at(9), "K Ostler");
        assertThat(book.isOpen()).isTrue();
        assertThat(book.opened()).hasValue(TimeOfDay.at(9));
        List<LogEntry> lines = book.lines();
        assertThat(lines.get(lines.size() - 1).kind()).isSameAs(EntryKind.OPENED);
        assertThat(lines.get(lines.size() - 1).text()).isEqualTo("first load at 9:00");
        assertThat(book).hasToString("JP 2025-01-15, 9:00, still running");
    }

    @Test
    void aMachineThatIsNotClearWillNotOpen() {
        DayLog book = DayLog.start(JOHN_PAUL, DAY, WINDOW);
        assertThatThrownBy(() -> book.openAt(TimeOfDay.at(9), "K Ostler"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("JP is not clear to open: no pre opening sheet");
    }

    @Test
    void aMachineOpensOnce() {
        DayLog book = log().openAt(TimeOfDay.at(9), "K Ostler");
        assertThatThrownBy(() -> book.openAt(TimeOfDay.at(10), "K Ostler"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("JP already opened at 9:00");
    }

    @Test
    void closingNeedsAnOpeningFirst() {
        assertThatThrownBy(() -> log().closeAt(TimeOfDay.at(16), "K Ostler"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("JP never opened");
    }

    @Test
    void aMachineClosesOnceAndAfterItOpened() {
        DayLog book = log().openAt(TimeOfDay.at(9), "K Ostler").closeAt(TimeOfDay.at(16), "K Ostler");
        assertThat(book.isOpen()).isFalse();
        assertThat(book).hasToString("JP 2025-01-15, 9:00 to 16:00");
        assertThatThrownBy(() -> book.closeAt(TimeOfDay.at(17), "K Ostler"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("JP already closed at 16:00");
    }

    @Test
    void aLogRefusesACloseBeforeTheOpen() {
        assertThatThrownBy(() -> built(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(TimeOfDay.at(12)),
                        Optional.of(TimeOfDay.at(9))))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a machine cannot close before it opens");
    }

    @Test
    void aLogRefusesACloseWithNoOpen() {
        assertThatThrownBy(() ->
                        built(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(TimeOfDay.at(16))))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a machine that never opened cannot close");
    }

    @Test
    void aSheetOrAStopLogFromAnotherMachineIsRefused() {
        assertThatThrownBy(() -> {
                    DayLog.start(JOHN_PAUL, DAY, WINDOW, clearSheet(johnPaul().withCode(JOHN_PAUL)));
                    new DayLog(
                            STRAWBERRY,
                            DAY,
                            WINDOW,
                            Optional.of(clearSheet()),
                            HoldRegister.of(),
                            Optional.empty(),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty());
                })
                .isInstanceOf(OperationsError.class)
                .hasMessage("that sheet is for another machine");
        assertThatThrownBy(() -> built(
                        Optional.empty(), Optional.of(StopLog.of(STRAWBERRY)), Optional.empty(), Optional.empty()))
                .isInstanceOf(OperationsError.class)
                .hasMessage("that stop log is for another machine");
    }

    @Test
    void notesAreKeptApartFromTheStructuralLines() {
        DayLog book = log().openAt(TimeOfDay.at(9), "K Ostler")
                .note(TimeOfDay.at(11), "ski school loading on the far side", "K Ostler")
                .closeAt(TimeOfDay.at(16), "K Ostler");
        assertThat(book.lines()).hasSize(3);
        assertThat(book.notes()).hasSize(1);
        assertThat(book.notes().get(0).text()).startsWith("ski school");
    }

    @Test
    void aLineSaysSomethingAndIsSigned() {
        assertThatThrownBy(() -> new LogEntry(at(11), EntryKind.NOTE, "   ", "K Ostler"))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a log line says something");
        assertThatThrownBy(() -> new LogEntry(at(11), EntryKind.NOTE, "something", " "))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a log line is signed");
    }

    @Test
    void aLinePrintsTheWayABookReads() {
        LogEntry entry = new LogEntry(at(11, 5), EntryKind.NOTE, "wind picking up", "K Ostler");
        assertThat(entry).hasToString("11:05 K Ostler: wind picking up");
    }

    @Test
    void lateOpeningAndLateSweepingAreBothNoticed() {
        DayLog late = log().openAt(TimeOfDay.at(9, 20), "K Ostler").closeAt(TimeOfDay.at(16, 40), "K Ostler");
        assertThat(late.openedLate()).isTrue();
        assertThat(late.sweptLate()).isTrue();
        DayLog onTime = log().openAt(TimeOfDay.at(9), "K Ostler").closeAt(TimeOfDay.at(16), "K Ostler");
        assertThat(onTime.openedLate()).isFalse();
        assertThat(onTime.sweptLate()).isFalse();
    }

    @Test
    void theRunningTimeTakesTheStopsOffTheOpenTime() {
        StopLog stops = StopLog.of(
                JOHN_PAUL,
                List.of(new StopEvent(
                        JOHN_PAUL,
                        StopKind.GATE,
                        at(11),
                        "K Ostler",
                        "",
                        Optional.of(at(11, 20)),
                        Optional.of("K Ostler"),
                        true)));
        DayLog book = withStops(stops).openAt(TimeOfDay.at(9), "K Ostler").closeAt(TimeOfDay.at(16), "K Ostler");
        assertThat(book.openTime()).isEqualTo(Duration.ofHours(7));
        assertThat(book.runningTime()).isEqualTo(Duration.ofMinutes(400));
        assertThat(book.scheduledTime()).isEqualTo(Duration.ofMinutes(435));
    }

    @Test
    void aDayThatHasNotFinishedHasNoOpenTime() {
        DayLog running = log().openAt(TimeOfDay.at(9), "K Ostler");
        assertThatThrownBy(running::openTime)
                .isInstanceOf(OperationsError.class)
                .hasMessage("the machine has not run a full day yet");
    }

    @Test
    void theMeterReadingHasToMatchTheMachineAndTheDay() {
        DayLog book = log();
        MeterReading good = new MeterReading(JOHN_PAUL, DAY, new BigDecimal("42000.0"), 1_000_000, "M Vetter");
        assertThat(book.withMeter(good).meter()).containsSame(good);
        MeterReading wrongDay =
                new MeterReading(JOHN_PAUL, LocalDate.of(2025, 1, 16), new BigDecimal("42000.0"), 1, "M Vetter");
        assertThatThrownBy(() -> book.withMeter(wrongDay))
                .isInstanceOf(OperationsError.class)
                .hasMessage("that meter reading is for another machine or another day");
    }
}
