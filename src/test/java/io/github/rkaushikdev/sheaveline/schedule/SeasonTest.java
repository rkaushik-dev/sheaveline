package io.github.rkaushikdev.sheaveline.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeasonTest {

    private static final List<HolidayPeriod> HOLIDAYS = List.of(
            new HolidayPeriod("christmas", LocalDate.of(2024, 12, 21), LocalDate.of(2025, 1, 1)),
            new HolidayPeriod("presidents", LocalDate.of(2025, 2, 15), LocalDate.of(2025, 2, 23)));

    private static Season season(
            LocalDate opens,
            LocalDate closes,
            Optional<LocalDate> previewFrom,
            Optional<LocalDate> springFrom,
            List<HolidayPeriod> holidays) {
        return new Season(
                "2024/25", opens, closes, previewFrom, springFrom, holidays, Set.of(LocalDate.of(2025, 1, 14)));
    }

    private static Season withPreview(Optional<LocalDate> previewFrom) {
        return season(
                LocalDate.of(2024, 11, 29),
                LocalDate.of(2025, 4, 20),
                previewFrom,
                Optional.of(LocalDate.of(2025, 3, 24)),
                HOLIDAYS);
    }

    private static Season withSpring(LocalDate springFrom) {
        return season(
                LocalDate.of(2024, 11, 29),
                LocalDate.of(2025, 4, 20),
                Optional.of(LocalDate.of(2024, 11, 23)),
                Optional.of(springFrom),
                HOLIDAYS);
    }

    private static Season buildSeason() {
        return withPreview(Optional.of(LocalDate.of(2024, 11, 23)));
    }

    @Test
    void aDayOutsideTheSeasonIsClosed() {
        Season season = buildSeason();
        assertThat(season.dayType(LocalDate.of(2024, 11, 1))).isSameAs(DayType.CLOSED);
        assertThat(season.dayType(LocalDate.of(2025, 5, 1))).isSameAs(DayType.CLOSED);
        assertThat(season.covers(LocalDate.of(2025, 5, 1))).isFalse();
    }

    @Test
    void aScheduledClosureBeatsEverythingElse() {
        Season season = buildSeason();
        assertThat(season.dayType(LocalDate.of(2025, 1, 14))).isSameAs(DayType.CLOSED);
        assertThat(season.isOperating(LocalDate.of(2025, 1, 14))).isFalse();
    }

    @Test
    void previewDaysRunButAreNotPublic() {
        Season season = buildSeason();
        LocalDate day = LocalDate.of(2024, 11, 25);
        assertThat(season.dayType(day)).isSameAs(DayType.PREVIEW);
        assertThat(season.isOperating(day)).isTrue();
        assertThat(season.isPublic(day)).isFalse();
    }

    @Test
    void aHolidayBeatsTheWeekend() {
        Season season = buildSeason();
        LocalDate saturday = LocalDate.of(2024, 12, 28);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(season.dayType(saturday)).isSameAs(DayType.HOLIDAY);
    }

    @Test
    void aHolidayBeatsSpring() {
        Season season = withSpring(LocalDate.of(2025, 2, 1));
        assertThat(season.dayType(LocalDate.of(2025, 2, 18))).isSameAs(DayType.HOLIDAY);
    }

    @Test
    void springBeatsTheWeekend() {
        Season season = buildSeason();
        LocalDate saturday = LocalDate.of(2025, 3, 29);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(season.dayType(saturday)).isSameAs(DayType.SPRING);
    }

    @Test
    void anOrdinaryWeekendAndAnOrdinaryWeekday() {
        Season season = buildSeason();
        assertThat(season.dayType(LocalDate.of(2025, 1, 11))).isSameAs(DayType.WEEKEND);
        assertThat(season.dayType(LocalDate.of(2025, 1, 15))).isSameAs(DayType.MIDWEEK);
    }

    @Test
    void theFirstAndLastDayOfTheSeasonAreInIt() {
        Season season = buildSeason();
        assertThat(season.firstDay()).isEqualTo(LocalDate.of(2024, 11, 23));
        assertThat(season.dayType(LocalDate.of(2024, 11, 23))).isSameAs(DayType.PREVIEW);
        assertThat(season.dayType(LocalDate.of(2024, 11, 29))).isNotSameAs(DayType.CLOSED);
        assertThat(season.dayType(LocalDate.of(2025, 4, 20))).isSameAs(DayType.SPRING);
    }

    @Test
    void withoutAPreviewTheSeasonStartsOnOpeningDay() {
        Season season = withPreview(Optional.empty());
        assertThat(season.firstDay()).isEqualTo(LocalDate.of(2024, 11, 29));
        assertThat(season.dayType(LocalDate.of(2024, 11, 25))).isSameAs(DayType.CLOSED);
    }

    @Test
    void holidaysAnswerWhichOneADayFallsIn() {
        Season season = buildSeason();
        HolidayPeriod holiday = season.holidayOn(LocalDate.of(2024, 12, 24)).orElseThrow();
        assertThat(holiday.name()).isEqualTo("christmas");
        assertThat(holiday.days()).isEqualTo(12);
        assertThat(season.holidayOn(LocalDate.of(2025, 1, 20))).isEmpty();
    }

    @Test
    void theOperatingDayCountSkipsClosures() {
        Season season = buildSeason();
        assertThat(season.length()).isEqualTo(148);
        assertThat(season.days()).hasSize(149);
    }

    @Test
    void dayNumbersCountOperatingDaysFromOne() {
        Season season = buildSeason();
        assertThat(season.dayNumber(LocalDate.of(2024, 11, 23))).isEqualTo(1);
        assertThat(season.dayNumber(LocalDate.of(2024, 11, 29))).isEqualTo(7);
        assertThat(season.dayNumber(LocalDate.of(2025, 4, 20))).isEqualTo(season.length());
    }

    @Test
    void aClosedDayHasNoNumber() {
        Season season = buildSeason();
        assertThatThrownBy(() -> season.dayNumber(LocalDate.of(2025, 1, 14)))
                .isInstanceOf(SeasonError.class)
                .hasMessage("2025-01-14 is not an operating day of 2024/25");
    }

    @Test
    void daysRemainingCountsWhatIsStillToCome() {
        Season season = buildSeason();
        assertThat(season.daysRemaining(LocalDate.of(2025, 4, 20))).isZero();
        assertThat(season.daysRemaining(LocalDate.of(2024, 11, 23))).isEqualTo(season.length() - 1);
    }

    @Test
    void publicDaysAreTheOperatingDaysLessThePreview() {
        Season season = buildSeason();
        assertThat(season.publicDays()).hasSize(season.length() - 6);
    }

    @Test
    void aSeasonThatClosesBeforeItOpensIsRefused() {
        assertThatThrownBy(() -> season(
                        LocalDate.of(2025, 4, 1),
                        LocalDate.of(2025, 3, 1),
                        Optional.of(LocalDate.of(2024, 11, 23)),
                        Optional.of(LocalDate.of(2025, 3, 24)),
                        HOLIDAYS))
                .isInstanceOf(SeasonError.class);
    }

    @Test
    void aPreviewAfterOpeningDayIsRefused() {
        assertThatThrownBy(() -> withPreview(Optional.of(LocalDate.of(2024, 12, 5))))
                .isInstanceOf(SeasonError.class);
    }

    @Test
    void springOutsideTheSeasonIsRefused() {
        assertThatThrownBy(() -> withSpring(LocalDate.of(2025, 5, 5))).isInstanceOf(SeasonError.class);
    }

    @Test
    void aHolidayOutsideTheSeasonIsRefused() {
        List<HolidayPeriod> july =
                List.of(new HolidayPeriod("july", LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 4)));
        assertThatThrownBy(() -> season(
                        LocalDate.of(2024, 11, 29),
                        LocalDate.of(2025, 4, 20),
                        Optional.of(LocalDate.of(2024, 11, 23)),
                        Optional.of(LocalDate.of(2025, 3, 24)),
                        july))
                .isInstanceOf(SeasonError.class)
                .hasMessage("holiday july falls outside 2024/25");
    }

    @Test
    void aHolidayThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> new HolidayPeriod("backwards", LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(SeasonError.class);
    }

    @Test
    void dayTypesKnowWhetherTheHillIsOpen() {
        assertThat(DayType.CLOSED.isOperating()).isFalse();
        assertThat(DayType.PREVIEW.isOperating()).isTrue();
        assertThat(DayType.PREVIEW.isPublic()).isFalse();
        assertThat(DayType.MIDWEEK.isPublic()).isTrue();
    }

    @Test
    void aSeasonPrintsItsDates() {
        assertThat(buildSeason()).hasToString("2024/25 (2024-11-29 to 2025-04-20)");
    }
}
