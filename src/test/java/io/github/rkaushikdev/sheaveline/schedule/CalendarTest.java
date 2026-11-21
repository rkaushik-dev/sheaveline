package io.github.rkaushikdev.sheaveline.schedule;

import static io.github.rkaushikdev.sheaveline.HillCalendar.theSeason;
import static io.github.rkaushikdev.sheaveline.HillCalendar.theSite;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarTest {

    private static final LocalDate SOLSTICE = LocalDate.of(2024, 12, 21);
    private static final LocalDate SPRING = LocalDate.of(2025, 4, 12);
    private static final OperatingWindow MIDWEEK = OperatingWindow.parse("09:00", "16:00", "16:30");
    private static final OperatingWindow HOLIDAY = OperatingWindow.parse("08:30", "16:30", "17:00");

    private static HoursTable table() {
        return new HoursTable(MIDWEEK, Map.of(DayType.HOLIDAY, HOLIDAY));
    }

    private static SeasonCalendar calendar(Duration clearsIn) {
        return new SeasonCalendar(theSeason(), table(), theSite(), clearsIn);
    }

    private static SeasonCalendar calendar() {
        return calendar(Duration.ofMinutes(45));
    }

    @Test
    void aMachineThatClearsInstantlyHasNotBeenDrilled() {
        assertThatThrownBy(() -> new SeasonCalendar(theSeason(), table(), theSite(), new Duration(0)))
                .isInstanceOf(SeasonError.class);
    }

    @Test
    void aClosedDayGetsNoWindow() {
        OperatingDay made = calendar().day(LocalDate.of(2025, 6, 1));
        assertThat(made.dayType()).isSameAs(DayType.CLOSED);
        assertThat(made.isOperating()).isFalse();
        assertThat(made.isClipped()).isFalse();
        assertThat(made.toString()).contains("closed");
    }

    @Test
    void aLongSpringDayKeepsThePublishedHours() {
        OperatingDay made = calendar().day(SPRING);
        assertThat(made.isOperating()).isTrue();
        assertThat(made.window()).isEqualTo(made.published());
        assertThat(made.isClipped()).isFalse();
        assertThat(made.lost().isZero()).isTrue();
    }

    @Test
    void theSolsticeLosesLoadingTimeToTheLight() {
        OperatingDay made = calendar().day(SOLSTICE);
        assertThat(made.isOperating()).isTrue();
        assertThat(made.isClipped()).isTrue();
        assertThat(made.window().orElseThrow().lastLoad())
                .isLessThan(made.published().orElseThrow().lastLoad());
        assertThat(made.lost().isZero()).isFalse();
        assertThat(made.toString()).contains("clipped");
    }

    @Test
    void theClippedWindowStillRunsForwards() {
        OperatingWindow window = calendar().day(SOLSTICE).window().orElseThrow();
        assertThat(window.firstLoad()).isLessThanOrEqualTo(window.lastLoad());
        assertThat(window.lastLoad()).isLessThanOrEqualTo(window.sweepBy());
    }

    @Test
    void aSlowerEvacuationTakesMoreOfTheShortDays() {
        OperatingDay quick = calendar(Duration.ofMinutes(30)).day(SOLSTICE);
        OperatingDay slow = calendar(Duration.ofHours(3)).day(SOLSTICE);
        assertThat(slow.window().orElseThrow().lastLoad())
                .isLessThan(quick.window().orElseThrow().lastLoad());
        assertThat(slow.lost()).isGreaterThan(quick.lost());
    }

    @Test
    void anEvacuationLongerThanTheDayLeavesNoWindowAtAll() {
        OperatingDay made = calendar(Duration.ofHours(20)).day(SOLSTICE);
        assertThat(made.isImpossible()).isTrue();
        assertThat(made.window()).isEmpty();
        assertThat(made.toString()).contains("no light to load");
    }

    @Test
    void aSecondTeamHandsTheLoadingTimeBack() {
        SeasonCalendar alone = calendar(Duration.ofHours(3));
        SeasonCalendar paired = alone.withEvacuation(Duration.ofMinutes(90));
        assertThat(paired.loadingTime()).isGreaterThan(alone.loadingTime());
        assertThat(paired.clippedDays().size()).isLessThan(alone.clippedDays().size());
    }

    @Test
    void theDaysThatLoseLightAreListedAndTotalled() {
        SeasonCalendar made = calendar(Duration.ofHours(2));
        List<OperatingDay> clipped = made.clippedDays();
        assertThat(clipped).isNotEmpty().allMatch(OperatingDay::isClipped);
        assertThat(made.lostToTheLight().seconds())
                .isEqualTo(
                        clipped.stream().mapToLong(one -> one.lost().seconds()).sum());
    }

    @Test
    void theWorstDayIsTheOneThatLosesMost() {
        SeasonCalendar made = calendar(Duration.ofHours(2));
        OperatingDay worst = made.worstDay().orElseThrow();
        assertThat(made.clippedDays()).allMatch(one -> one.lost().compareTo(worst.lost()) <= 0);
    }

    @Test
    void aSeasonThatNeverRunsShortHasNoWorstDay() {
        assertThat(calendar(Duration.ofMinutes(1)).worstDay()).isEmpty();
        assertThat(calendar(Duration.ofMinutes(1)).lostToTheLight().isZero()).isTrue();
    }

    @Test
    void everyOperatingDayInTheSeasonIsWalked() {
        assertThat(calendar().operatingDays()).hasSameSizeAs(theSeason().operatingDays());
    }

    @Test
    void aHolidayGetsTheHolidayHoursWhereTheLightAllows() {
        Season season = theSeason();
        LocalDate holiday = season.days().stream()
                .filter(one -> season.dayType(one) == DayType.HOLIDAY)
                .findFirst()
                .orElseThrow();
        OperatingDay made = new SeasonCalendar(season, table(), theSite(), Duration.ofMinutes(20)).day(holiday);
        assertThat(made.published()).contains(HOLIDAY);
    }

    @Test
    void theCalendarSaysHowManyDaysAreShortOfLight() {
        String printed = calendar(Duration.ofHours(2)).toString();
        assertThat(printed).contains("operating days").contains("short of light");
    }

    @Test
    void noDayIsClippedPastItsOwnFirstLoad() {
        for (OperatingDay one : calendar(Duration.ofHours(4)).days()) {
            one.window().ifPresent(window -> assertThat(window.lastLoad()).isGreaterThanOrEqualTo(window.firstLoad()));
        }
    }
}
