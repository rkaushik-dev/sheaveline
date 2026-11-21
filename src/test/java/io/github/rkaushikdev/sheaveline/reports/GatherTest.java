package io.github.rkaushikdev.sheaveline.reports;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.HillCalendar;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.upkeep.InspectionRecord;
import io.github.rkaushikdev.sheaveline.upkeep.Library;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceAssessment;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceStatus;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.Station;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatherTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");
    private static final Station SUMMIT = new Station("summit", Length.ofFeet(7981));

    private UnitOfWork work;

    @BeforeEach
    void twoMachinesOnTheRegister() {
        Database database = TestDatabase.fresh();
        try (UnitOfWork stocking = UnitOfWork.on(database).open()) {
            stocking.lifts().add(johnPaul());
            stocking.lifts().add(strawberry());
            stocking.commit();
        }
        work = UnitOfWork.on(database).open();
    }

    @AfterEach
    void closeIt() {
        work.close();
    }

    private static PreOpSheet sheet(Lift machine, LocalDate day, Set<String> failing) {
        List<CheckResult> results = new ArrayList<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(machine)) {
                boolean failed = failing.contains(item.code());
                results.add(new CheckResult(
                        item, failed ? CheckOutcome.FAIL : CheckOutcome.PASS, "R Skaggs", failed ? "dry" : ""));
            }
        }
        return new PreOpSheet(machine, day, results, "R Skaggs", "D Chin");
    }

    private void logged(Lift machine, LocalDate day, Set<String> failing) {
        DayLog log = DayLog.start(machine.code(), day, WINDOW, sheet(machine, day, failing));
        if (failing.isEmpty()) {
            log = log.openAt(TimeOfDay.parse("09:00"), "D Chin");
        }
        work.dayLogs().add(log);
    }

    private void logged(Lift machine, LocalDate day) {
        logged(machine, day, Set.of());
    }

    private void logged(Lift machine) {
        logged(machine, ON);
    }

    /** Every job on the ladder signed off yesterday, so the ladder is not the story. */
    private void upToDate(Lift machine) {
        for (String code : Library.codesFor(machine.liftClass())) {
            work.inspections()
                    .add(InspectionRecord.of(
                            machine.code(),
                            code,
                            ON.minusDays(1),
                            "R Skaggs",
                            Optional.of(new BigDecimal("40000.0")),
                            Optional.of(1_400_000L),
                            List.of()));
        }
        work.dayLogs()
                .readMeter(new MeterReading(machine.code(), ON, new BigDecimal("40000.5"), 1_400_100, "R Skaggs"));
    }

    private void blowing() {
        work.weather().addStation(SUMMIT);
        work.weather()
                .record(new Observation(SUMMIT, ON.atTime(6, 0), Wind.parse("ENE 30G60"), Temperature.ofDegreesF(8)));
    }

    private static StopEvent stopped(LocalDate day, StopKind kind, int restartHour, int restartMinute) {
        return new StopEvent(
                JOHN_PAUL,
                kind,
                day.atTime(11, 0),
                "D Chin",
                "",
                Optional.of(day.atTime(restartHour, restartMinute)),
                Optional.of("D Chin"),
                true);
    }

    @Test
    void aMachineNobodyHasTouchedReadsAsNotWalked() {
        MachineLine line = Gather.machineLine(work, johnPaul(), ON);
        assertThat(line.sheetWalked()).isFalse();
        assertThat(line.opening()).isFalse();
        assertThat(line.reasons()).contains("morning walk not signed");
    }

    @Test
    void aWalkedSheetOpensTheMachine() {
        upToDate(johnPaul());
        logged(johnPaul());
        MachineLine line = Gather.machineLine(work, johnPaul(), ON);
        assertThat(line.sheetWalked()).isTrue();
        assertThat(line.opening()).isTrue();
        assertThat(line.verdict()).isEqualTo("opening");
    }

    @Test
    void aFailedCriticalCheckKeepsItShut() {
        logged(johnPaul(), ON, Set.of("B1"));
        MachineLine line = Gather.machineLine(work, johnPaul(), ON);
        assertThat(line.opening()).isFalse();
        assertThat(line.reasons()).anyMatch(one -> one.contains("B1"));
    }

    @Test
    void aWindReadingOverTheLimitHoldsTheMachine() {
        upToDate(johnPaul());
        logged(johnPaul());
        blowing();
        MachineLine line = Gather.machineLine(work, johnPaul(), ON);
        assertThat(line.holds()).isNotEmpty();
        assertThat(line.verdict()).isEqualTo("held");
    }

    @Test
    void aStaleReadingIsNotUsedToHoldAnything() {
        logged(johnPaul());
        work.weather().addStation(SUMMIT);
        work.weather()
                .record(new Observation(
                        SUMMIT,
                        LocalDateTime.of(2024, 12, 1, 6, 0),
                        Wind.parse("ENE 30G60"),
                        Temperature.ofDegreesF(8)));
        assertThat(Gather.machineLine(work, johnPaul(), ON).holds()).isEmpty();
    }

    @Test
    void aHillWithNoAnemometerIsANormalHill() {
        logged(johnPaul());
        assertThat(Gather.machineLine(work, johnPaul(), ON).holds()).isEmpty();
    }

    @Test
    void theLineIsOnlyWalkedWhenATensionIsGiven() {
        Lift lift = johnPaul();
        assertThat(Gather.assess(work, lift, ON).geometry()).isEmpty();
        assertThat(Gather.assess(work, lift, ON, Optional.of(Load.ofPounds(34500)))
                        .geometry())
                .isPresent();
    }

    @Test
    void theAssessmentReadsTheLadderOffWhatWasRecorded() {
        ServiceAssessment reading = Gather.assess(work, johnPaul(), ON);
        assertThat(reading.status()).isNotSameAs(ServiceStatus.IN_SERVICE);
        assertThat(reading.schedule().neverDone()).isNotEmpty();
    }

    @Test
    void theMorningPageCoversEveryMachineOnTheRegister() {
        logged(johnPaul());
        logged(strawberry());
        Morning page = Gather.morning(work, ON, "Ogden Peak", "eight inches overnight");
        assertThat(page.machines()).extracting(MachineLine::lift).containsExactly(JOHN_PAUL, STRAWBERRY);
        assertThat(page.render().text()).contains("eight inches overnight");
    }

    @Test
    void theSeasonAddsUpTheDaysThatWereWrittenUp() {
        Season season = HillCalendar.theSeason();
        for (int offset = 0; offset < 3; offset++) {
            logged(johnPaul(), season.opens().plusDays(offset));
        }
        SeasonSummary summary = Gather.seasonSummary(work, season);
        assertThat(summary.daysOpen(JOHN_PAUL)).isEqualTo(3);
        assertThat(summary.scheduled(JOHN_PAUL).hours()).isEqualByComparingTo("22.5");
    }

    @Test
    void aDayOutsideTheSeasonIsNotCounted() {
        Season season = HillCalendar.theSeason();
        logged(johnPaul(), season.opens());
        logged(johnPaul(), season.closes().plusDays(30));
        assertThat(Gather.seasonSummary(work, season).daysOpen(JOHN_PAUL)).isEqualTo(1);
    }

    @Test
    void theStopsOnADayComeThroughToTheSeasonArithmetic() {
        Season season = HillCalendar.theSeason();
        LocalDate day = season.opens();
        logged(johnPaul(), day);
        work.dayLogs().stop(stopped(day, StopKind.GATE, 11, 30));
        SeasonSummary summary = Gather.seasonSummary(work, season);
        assertThat(summary.downtime(JOHN_PAUL).minutes()).isEqualByComparingTo("30");
        assertThat(summary.chargeable(JOHN_PAUL).minutes()).isEqualByComparingTo("30");
        assertThat(summary.stopsByKind(JOHN_PAUL)).isEqualTo(Map.of(StopKind.GATE, 1));
    }

    @Test
    void aPowerCutIsDowntimeButNotTheMachinesFault() {
        Season season = HillCalendar.theSeason();
        LocalDate day = season.opens();
        logged(johnPaul(), day);
        work.dayLogs().stop(stopped(day, StopKind.POWER, 12, 0));
        SeasonSummary summary = Gather.seasonSummary(work, season);
        assertThat(summary.downtime(JOHN_PAUL).minutes()).isEqualByComparingTo("60");
        assertThat(summary.chargeable(JOHN_PAUL).isZero()).isTrue();
        assertThat(summary.availability(JOHN_PAUL)).isEqualByComparingTo("1");
    }
}
