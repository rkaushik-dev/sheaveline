package io.github.rkaushikdev.sheaveline.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Constructors;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HoursTest {

    private static final Season SEASON = new Season(
            "2024/25",
            LocalDate.of(2024, 11, 29),
            LocalDate.of(2025, 4, 20),
            Optional.of(LocalDate.of(2024, 11, 23)),
            Optional.of(LocalDate.of(2025, 3, 24)),
            List.of(new HolidayPeriod("christmas", LocalDate.of(2024, 12, 21), LocalDate.of(2025, 1, 1))),
            Set.of(LocalDate.of(2025, 1, 14)));

    private static final OperatingWindow MIDWEEK = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm");
    private static final OperatingWindow HOLIDAY = OperatingWindow.parse("8:30", "4:00 pm", "4:30 pm");
    private static final OperatingWindow SPRING = OperatingWindow.parse("9:00", "3:00 pm", "3:30 pm");
    private static final HoursTable TABLE =
            new HoursTable(MIDWEEK, Map.of(DayType.HOLIDAY, HOLIDAY, DayType.SPRING, SPRING));

    @Test
    void minutesSinceMidnightAreTheBaseUnit() {
        assertThat(TimeOfDay.at(9).minutes()).isEqualTo(540);
        assertThat(TimeOfDay.at(15, 45).minutes()).isEqualTo(945);
        assertThat(new TimeOfDay(945).hour()).isEqualTo(15);
        assertThat(new TimeOfDay(945).minute()).isEqualTo(45);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"9:00|540", "08:30|510", "15:45|945", "4:15 pm|975", "4:15PM|975", "12:00 am|0", "12:30 pm|750"})
    void parseReadsTheShapesASignUses(String text, int minutes) {
        assertThat(TimeOfDay.parse(text).minutes()).isEqualTo(minutes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "noon", "24:00", "9:60", "0:15 pm", "930"})
    void parseRefusesWhatIsNotATime(String text) {
        assertThatThrownBy(() -> TimeOfDay.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void aTimeOfDayStaysInsideTheDay() {
        assertThatThrownBy(() -> new TimeOfDay(-1)).isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> new TimeOfDay(1440)).isInstanceOf(RangeError.class);
        Constructors.takeOnly(TimeOfDay.class, int.class);
    }

    @Test
    void addingADurationMovesAlongTheSameDay() {
        assertThat(TimeOfDay.at(9).plus(Duration.ofMinutes(45))).isEqualTo(TimeOfDay.at(9, 45));
        assertThat(TimeOfDay.at(16).minus(Duration.ofMinutes(30))).isEqualTo(TimeOfDay.at(15, 30));
    }

    @Test
    void aWindowThatRunsPastMidnightIsRefused() {
        assertThatThrownBy(() -> TimeOfDay.at(23, 30).plus(Duration.ofHours(1))).isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> TimeOfDay.at(0, 30).minus(Duration.ofHours(1))).isInstanceOf(RangeError.class);
    }

    @Test
    void theIntervalBetweenTwoTimes() {
        assertThat(TimeOfDay.at(9).until(TimeOfDay.at(15, 45))).isEqualTo(Duration.ofMinutes(405));
        assertThatThrownBy(() -> TimeOfDay.at(15).until(TimeOfDay.at(9)))
                .isInstanceOf(RangeError.class)
                .hasMessage("interval 9:00 is outside 15:00 to later");
    }

    @Test
    void conversionsToAndFromTheClockTimesOfTheStandardLibrary() {
        assertThat(TimeOfDay.of(LocalTime.of(15, 45))).isEqualTo(TimeOfDay.at(15, 45));
        assertThat(TimeOfDay.at(15, 45).toLocalTime()).isEqualTo(LocalTime.of(15, 45));
    }

    @Test
    void orderingAndEquality() {
        TimeOfDay early = TimeOfDay.at(9);
        TimeOfDay late = TimeOfDay.at(16);
        assertThat(early).isLessThan(late);
        assertThat(late).isGreaterThan(early);
        assertThat(early).isLessThanOrEqualTo(TimeOfDay.at(9));
        assertThat(early).isGreaterThanOrEqualTo(TimeOfDay.at(9));
        assertThat(early).isNotEqualTo(late);
        assertThat((Object) early).isNotEqualTo(540);
        assertThat(new HashSet<>(List.of(early, new TimeOfDay(540)))).hasSize(1);
    }

    @Test
    void formatting() {
        assertThat(TimeOfDay.at(9, 5).formatClock()).isEqualTo("9:05");
        assertThat(TimeOfDay.at(15, 45).formatMeridiem()).isEqualTo("3:45 pm");
        assertThat(TimeOfDay.at(0, 30).formatMeridiem()).isEqualTo("12:30 am");
        assertThat(TimeOfDay.at(12, 30).formatMeridiem()).isEqualTo("12:30 pm");
        assertThat(TimeOfDay.at(9).repr()).isEqualTo("new TimeOfDay(540)");
        assertThat(TimeOfDay.at(9)).hasToString("9:00");
    }

    @Test
    void aWindowKnowsWhenItIsLoadingAndWhenItIsRunning() {
        assertThat(MIDWEEK.isLoading(TimeOfDay.at(12))).isTrue();
        assertThat(MIDWEEK.isLoading(TimeOfDay.at(16))).isFalse();
        assertThat(MIDWEEK.isRunning(TimeOfDay.at(16))).isTrue();
        assertThat(MIDWEEK.isRunning(TimeOfDay.at(16, 30))).isFalse();
        assertThat(MIDWEEK.isLoading(TimeOfDay.at(8, 30))).isFalse();
    }

    @Test
    void aWindowReportsItsTwoSpans() {
        assertThat(MIDWEEK.loadingTime()).isEqualTo(Duration.ofMinutes(405));
        assertThat(MIDWEEK.sweepAllowance()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void aWindowHasToRunForwards() {
        assertThatThrownBy(() -> OperatingWindow.parse("15:00", "9:00", "16:00"))
                .isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> OperatingWindow.parse("9:00", "15:45", "15:30"))
                .isInstanceOf(RangeError.class);
    }

    @Test
    void theTablePicksTheWindowForTheDay() {
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2025, 1, 15))).contains(MIDWEEK);
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2024, 12, 24))).contains(HOLIDAY);
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2025, 4, 1))).contains(SPRING);
    }

    @Test
    void aWeekendFallsBackToTheDefaultWhenItIsNotListed() {
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2025, 1, 11))).contains(MIDWEEK);
    }

    @Test
    void aClosedDayHasNoWindow() {
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2025, 1, 14))).isEmpty();
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2025, 6, 1))).isEmpty();
        assertThat(TABLE.windowForType(DayType.CLOSED)).isEmpty();
    }

    @Test
    void aPreviewDayStillGetsAWindow() {
        assertThat(TABLE.windowOn(SEASON, LocalDate.of(2024, 11, 25))).contains(MIDWEEK);
    }

    @Test
    void aWindowPrintsAllThreeTimes() {
        assertThat(MIDWEEK).hasToString("9:00 to 15:45, swept by 16:15");
    }
}
