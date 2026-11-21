package io.github.rkaushikdev.sheaveline.upkeep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntervalTest {

    @Test
    void anIntervalRunsOnAtLeastOneClock() {
        assertThatThrownBy(() -> new Interval(Optional.empty(), Optional.empty(), Optional.empty(), 0))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("an interval runs on at least one clock");
        assertThat(Interval.ofDays(30).bases()).isEqualTo(Set.of(Basis.CALENDAR));
        assertThat(Interval.ofHours(500).bases()).isEqualTo(Set.of(Basis.HOURS));
        assertThat(Interval.ofCycles(100000).bases()).isEqualTo(Set.of(Basis.CYCLES));
    }

    @Test
    void anIntervalCanRunOnTwoClocksAtOnce() {
        Interval both = Interval.ofDays(365).withHours(1500);
        assertThat(both.bases()).isEqualTo(Set.of(Basis.CALENDAR, Basis.HOURS));
        assertThat(both.runsOn(Basis.CALENDAR)).isTrue();
        assertThat(both.runsOn(Basis.HOURS)).isTrue();
        assertThat(both.runsOn(Basis.CYCLES)).isFalse();
    }

    @Test
    void anIntervalOfNothingIsRefused() {
        assertThatThrownBy(() -> Interval.ofDays(0))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("an interval of 0 days is not an interval");
        assertThatThrownBy(() -> Interval.ofHours(-5))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("an interval of -5 hours is not an interval");
        assertThatThrownBy(() -> Interval.ofDays(30).withGraceDays(-1))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("grace is not negative");
    }

    @Test
    void theNamedIntervalsAreWhatTheySay() {
        assertThat(Interval.daily().days()).contains(1);
        assertThat(Interval.weekly().days()).contains(7);
        assertThat(Interval.monthly().days()).contains(30);
        assertThat(Interval.annual().days()).contains(365);
        assertThat(Interval.fiveYearly().days()).contains(1826);
    }

    @Test
    void theNamedIntervalsCarrySensibleGrace() {
        assertThat(Interval.daily().graceDays()).isZero();
        assertThat(Interval.monthly().graceDays()).isEqualTo(7);
        assertThat(Interval.annual().graceDays()).isEqualTo(30);
    }

    @Test
    void theNextOneIsCountedFromWhenTheLastWasDue() {
        Interval monthly = Interval.monthly();
        assertThat(monthly.dueDate(LocalDate.of(2025, 1, 1))).contains(LocalDate.of(2025, 1, 31));
        assertThat(monthly.lastDayOfGrace(LocalDate.of(2025, 1, 1))).contains(LocalDate.of(2025, 2, 7));
    }

    @Test
    void aJobWithNoCalendarClockHasNoDueDate() {
        Interval hourly = Interval.ofHours(500);
        assertThat(hourly.dueDate(LocalDate.of(2025, 1, 1))).isEmpty();
        assertThat(hourly.lastDayOfGrace(LocalDate.of(2025, 1, 1))).isEmpty();
    }

    @Test
    void anHourBasedJobFallsDueOnTheMeter() {
        Interval hourly = Interval.ofHours(500);
        assertThat(hourly.dueHours(new BigDecimal("42000.0"))).contains(new BigDecimal("42500.0"));
        assertThat(Interval.ofDays(30).dueHours(new BigDecimal("42000.0"))).isEmpty();
    }

    @Test
    void aCycleBasedJobFallsDueOnTheCounter() {
        Interval counted = Interval.ofCycles(100_000);
        assertThat(counted.dueCycles(1_000_000)).contains(1_100_000L);
        assertThat(Interval.ofDays(30).dueCycles(1_000_000)).isEmpty();
    }

    @Test
    void anIntervalOnTwoClocksAnswersBoth() {
        Interval both = Interval.ofDays(365).withHours(1500);
        assertThat(both.dueDate(LocalDate.of(2025, 1, 1))).contains(LocalDate.of(2026, 1, 1));
        assertThat(both.dueHours(new BigDecimal("42000.0"))).contains(new BigDecimal("43500.0"));
    }

    @Test
    void anIntervalPrintsEveryClockItRunsOn() {
        assertThat(Interval.ofDays(30)).hasToString("every 30 days");
        assertThat(Interval.ofHours(500)).hasToString("every 500 hours");
        assertThat(Interval.ofDays(365).withHours(1500)).hasToString("every 365 days or every 1500 hours");
        assertThat(Interval.ofCycles(100_000)).hasToString("every 100,000 cycles");
    }

    @Test
    void aRequirementCarriesACodeAndADescription() {
        assertThatThrownBy(() -> new Requirement("  ", "something", Interval.monthly()))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("a requirement has a code");
        assertThatThrownBy(() -> new Requirement("M1", "  ", Interval.monthly()))
                .isInstanceOf(UpkeepError.class)
                .hasMessage("M1 has no description");
    }

    @Test
    void aRequirementSaysWhichMachinesItIsFor() {
        Requirement everything = new Requirement("M1", "grease the sheaves", Interval.monthly());
        Requirement aerialOnly = new Requirement("M2", "evacuation drill", Interval.annual(), Requirement.AERIAL);
        assertThat(everything.appliesTo(LiftClass.SURFACE_TOW)).isTrue();
        assertThat(aerialOnly.appliesTo(LiftClass.FIXED_CHAIR)).isTrue();
        assertThat(aerialOnly.appliesTo(LiftClass.SURFACE_TOW)).isFalse();
    }

    @Test
    void aRequirementPrintsItsInterval() {
        Requirement job = new Requirement("A1", "annual inspection", Interval.annual());
        assertThat(job).hasToString("A1 annual inspection, every 365 days");
    }

    @Test
    void aRequirementCanBeMarkedAsNotCritical() {
        Requirement tidy =
                new Requirement("E1", "paint the terminal", Interval.annual(), false, EnumSet.allOf(LiftClass.class));
        assertThat(tidy.critical()).isFalse();
        assertThat(new Requirement("A1", "annual", Interval.annual()).critical())
                .isTrue();
    }
}
