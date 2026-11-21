package io.github.rkaushikdev.sheaveline.upkeep;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ops.MeterHistory;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleTest {

    private static final LocalDate BUILT = LocalDate.of(2024, 11, 1);

    private static InspectionRecord record(
            String code, LocalDate done, Optional<BigDecimal> hours, Optional<Long> cycles) {
        return InspectionRecord.of(JOHN_PAUL, code, done, "M Vetter", hours, cycles, List.of());
    }

    private static InspectionRecord record(String code, LocalDate done) {
        return record(code, done, Optional.empty(), Optional.empty());
    }

    private static InspectionRecord withHours(String code, LocalDate done, String hours) {
        return record(code, done, Optional.of(new BigDecimal(hours)), Optional.empty());
    }

    private static UpkeepSchedule schedule(
            LiftClass liftClass, LocalDate built, Optional<MeterHistory> meter, InspectionRecord... records) {
        return new UpkeepSchedule(JOHN_PAUL, liftClass, built, RecordBook.of(JOHN_PAUL, List.of(records)), meter);
    }

    private static UpkeepSchedule schedule(InspectionRecord... records) {
        return schedule(LiftClass.DETACHABLE_CHAIR, BUILT, Optional.empty(), records);
    }

    private static UpkeepSchedule metered(MeterHistory meter, InspectionRecord... records) {
        return schedule(LiftClass.DETACHABLE_CHAIR, BUILT, Optional.of(meter), records);
    }

    private static UpkeepSchedule builtOn(LocalDate built, Optional<MeterHistory> meter, InspectionRecord... records) {
        return schedule(LiftClass.DETACHABLE_CHAIR, built, meter, records);
    }

    private static MeterHistory meter(String hours, long cycles, int day, LiftCode lift) {
        return MeterHistory.of(
                lift,
                List.of(new MeterReading(lift, LocalDate.of(2025, 1, day), new BigDecimal(hours), cycles, "M Vetter")));
    }

    private static MeterHistory meter(String hours, long cycles) {
        return meter(hours, cycles, 15, JOHN_PAUL);
    }

    private static MeterHistory meter() {
        return meter("42000.0", 1_000_000);
    }

    @Test
    void aScheduleCarriesTheJobsItsClassHas() {
        UpkeepSchedule quad = schedule();
        UpkeepSchedule tow = schedule(LiftClass.SURFACE_TOW, BUILT, Optional.empty());
        assertThat(quad.jobs().size()).isGreaterThan(tow.jobs().size());
        assertThat(quad.toString()).startsWith("JP ladder");
        assertThat(quad).hasToString("JP ladder, 20 jobs");
    }

    @Test
    void aBookOrAMeterFromAnotherMachineIsRefused() {
        assertThatThrownBy(() -> new UpkeepSchedule(JOHN_PAUL, LiftClass.FIXED_CHAIR, BUILT, RecordBook.of(STRAWBERRY)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("that record book is for SB");
        MeterHistory theirs = meter("42000.0", 1_000_000, 15, STRAWBERRY);
        assertThatThrownBy(() -> new UpkeepSchedule(
                        JOHN_PAUL, LiftClass.FIXED_CHAIR, BUILT, RecordBook.of(JOHN_PAUL), Optional.of(theirs)))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("that meter history is for SB");
    }

    @Test
    void aJobThatIsNotOnThisClassHasNoDueDate() {
        assertThatThrownBy(() ->
                        schedule(LiftClass.SURFACE_TOW, BUILT, Optional.empty()).dueItem("W2"))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("W2 is not a job on a surface lift");
    }

    @Test
    void aJobNeverDoneIsDueAnIntervalAfterTheMachineWasBuilt() {
        DueItem item = schedule().dueItem("M1");
        assertThat(item.dueOn()).contains(LocalDate.of(2024, 12, 1));
        assertThat(item.everDone()).isFalse();
        assertThat(schedule().neverDone()).contains(item);
    }

    @Test
    void aRecordFromBeforeTheMachineWasBuiltIsRefused() {
        assertThatThrownBy(() -> schedule(record("M1", LocalDate.of(2024, 1, 1))))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("M1 was done before JP was built");
    }

    @Test
    void aJobDoneEarlyCountsFromTheDayItWasDone() {
        UpkeepSchedule walked = schedule(record("A1", LocalDate.of(2025, 1, 6)));
        assertThat(walked.dueItem("A1").dueOn()).contains(LocalDate.of(2026, 1, 6));
    }

    @Test
    void aJobDoneOnTimeMovesOnByOneInterval() {
        UpkeepSchedule walked = schedule(record("M1", LocalDate.of(2024, 12, 1)));
        assertThat(walked.dueItem("M1").dueOn()).contains(LocalDate.of(2024, 12, 31));
    }

    @Test
    void aJobDoneInsideGraceKeepsTheOldAnchor() {
        UpkeepSchedule walked = schedule(record("M1", LocalDate.of(2024, 12, 6)));
        assertThat(walked.dueItem("M1").dueOn()).contains(LocalDate.of(2024, 12, 31));
    }

    @Test
    void aJobDoneOutsideGraceRestartsTheChain() {
        UpkeepSchedule walked = schedule(record("M1", LocalDate.of(2024, 12, 20)));
        assertThat(walked.dueItem("M1").dueOn()).contains(LocalDate.of(2025, 1, 19));
    }

    @Test
    void theLadderDoesNotWalkLaterEveryMonth() {
        UpkeepSchedule walked = schedule(
                record("M1", LocalDate.of(2024, 12, 4)),
                record("M1", LocalDate.of(2025, 1, 5)),
                record("M1", LocalDate.of(2025, 2, 3)));
        assertThat(walked.dueItem("M1").dueOn()).contains(LocalDate.of(2025, 3, 1));
    }

    @Test
    void anOverdueJobIsReportedWithTheClockThatFired() {
        UpkeepSchedule walked = schedule(record("M1", LocalDate.of(2024, 12, 1)));
        LocalDate on = LocalDate.of(2025, 1, 15);
        DueItem item = walked.dueItem("M1");
        assertThat(item.isOverdue(on)).isTrue();
        assertThat(item.fired(on)).isEqualTo(Set.of(Basis.CALENDAR));
        assertThat(walked.overdue(on)).contains(item);
    }

    @Test
    void aJobInsideItsIntervalIsNotOverdue() {
        UpkeepSchedule walked = schedule(record("M1", LocalDate.of(2025, 1, 10)));
        assertThat(walked.dueItem("M1").isOverdue(LocalDate.of(2025, 1, 15))).isFalse();
    }

    @Test
    void anHourBasedJobFiresOnTheMeterAndNotOnTheCalendar() {
        UpkeepSchedule walked =
                metered(meter("42600.0", 1_000_000), withHours("H1", LocalDate.of(2024, 12, 1), "41000.0"));
        DueItem item = walked.dueItem("H1");
        BigDecimal reading = new BigDecimal("42600.0");
        assertThat(item.dueAtHours()).contains(new BigDecimal("42500.0"));
        assertThat(item.hoursOverdue(Optional.of(reading))).isTrue();
        assertThat(item.calendarOverdue(LocalDate.of(2025, 1, 15))).isFalse();
        assertThat(item.fired(LocalDate.of(2025, 1, 15), Meters.ofHours(reading)))
                .isEqualTo(Set.of(Basis.HOURS));
    }

    @Test
    void theSameJobCanFireOnTheCalendarInstead() {
        UpkeepSchedule walked = builtOn(
                LocalDate.of(2022, 1, 1),
                Optional.of(meter("41200.0", 1_000_000)),
                withHours("H2", LocalDate.of(2023, 6, 1), "41000.0"));
        DueItem item = walked.dueItem("H2");
        assertThat(item.calendarOverdue(LocalDate.of(2025, 1, 15))).isTrue();
        assertThat(item.hoursOverdue(Optional.of(new BigDecimal("41200.0")))).isFalse();
    }

    @Test
    void bothClocksCanFireAtOnce() {
        UpkeepSchedule walked = builtOn(
                LocalDate.of(2022, 1, 1),
                Optional.of(meter("43000.0", 1_000_000)),
                withHours("H2", LocalDate.of(2023, 6, 1), "41000.0"));
        Set<Basis> fired =
                walked.dueItem("H2").fired(LocalDate.of(2025, 1, 15), Meters.ofHours(new BigDecimal("43000.0")));
        assertThat(fired).isEqualTo(Set.of(Basis.CALENDAR, Basis.HOURS));
    }

    @Test
    void aCycleBasedJobFiresOnTheCounter() {
        UpkeepSchedule walked = metered(
                meter("42000.0", 1_300_000),
                record("C1", LocalDate.of(2024, 12, 1), Optional.empty(), Optional.of(700_000L)));
        DueItem item = walked.dueItem("C1");
        assertThat(item.dueAtCycles()).contains(1_200_000L);
        assertThat(item.cyclesOverdue(Optional.of(1_300_000L))).isTrue();
    }

    @Test
    void withoutAMeterTheMeterClocksNeverFire() {
        UpkeepSchedule walked = schedule(withHours("H1", LocalDate.of(2024, 12, 1), "41000.0"));
        DueItem item = walked.dueItem("H1");
        assertThat(walked.currentHours()).isEmpty();
        assertThat(item.hoursOverdue(Optional.empty())).isFalse();
    }

    @Test
    void theOverdueListReadsTheMeterTheScheduleHolds() {
        UpkeepSchedule walked =
                metered(meter("42600.0", 1_000_000), withHours("H1", LocalDate.of(2024, 12, 1), "41000.0"));
        List<String> codes = walked.overdue(LocalDate.of(2024, 12, 15)).stream()
                .map(item -> item.job().code())
                .toList();
        assertThat(codes).contains("H1");
    }

    @Test
    void onlyTheCriticalOverdueJobsStopAMachineBeingCurrent() {
        UpkeepSchedule walked =
                builtOn(LocalDate.of(2022, 1, 1), Optional.empty(), record("A5", LocalDate.of(2023, 1, 1)));
        LocalDate on = LocalDate.of(2025, 1, 15);
        assertThat(walked.overdue(on)).anyMatch(item -> item.job().code().equals("A5"));
        assertThat(walked.overdueCritical(on))
                .noneMatch(item -> item.job().code().equals("A5"));
        assertThat(walked.isCurrent(on)).isFalse();
    }

    @Test
    void aMachineWithEverythingFreshIsCurrent() {
        LocalDate on = LocalDate.of(2025, 1, 15);
        List<InspectionRecord> records = new ArrayList<>();
        for (Requirement job : schedule().jobs()) {
            records.add(record(
                    job.code(),
                    LocalDate.of(2025, 1, 14),
                    Optional.of(new BigDecimal("42000.0")),
                    Optional.of(1_000_000L)));
        }
        UpkeepSchedule walked = metered(meter(), records.toArray(InspectionRecord[]::new));
        assertThat(walked.isCurrent(on)).isTrue();
        assertThat(walked.neverDone()).isEmpty();
    }

    @Test
    void theJobsFallingDueThisWeekCanBeListed() {
        UpkeepSchedule walked = schedule(record("M1", LocalDate.of(2025, 1, 1)));
        List<DueItem> soon = walked.dueWithin(LocalDate.of(2025, 1, 25), 10);
        assertThat(soon).anyMatch(item -> item.job().code().equals("M1"));
        assertThat(walked.dueItem("M1").daysUntil(LocalDate.of(2025, 1, 25))).contains(6L);
    }

    @Test
    void aDueItemPrintsWhenItIsDue() {
        DueItem item = schedule(record("M1", LocalDate.of(2025, 1, 1))).dueItem("M1");
        assertThat(item).hasToString("M1 due 2025-01-31");
    }
}
