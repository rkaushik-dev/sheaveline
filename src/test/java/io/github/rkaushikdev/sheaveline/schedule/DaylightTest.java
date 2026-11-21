package io.github.rkaushikdev.sheaveline.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DaylightTest {

    /** The summit terminal, forty one degrees north in the Wasatch. */
    private static final Site SUMMIT = new Site("summit", 41.216, -111.857, -420);

    private static final Site POLE = new Site("pole", 78.0, 15.0, 60, false);

    static Stream<Arguments> almanac() {
        return Stream.of(
                Arguments.of(LocalDate.of(2024, 12, 21), "7:50", "17:01"),
                Arguments.of(LocalDate.of(2025, 1, 15), "7:51", "17:22"),
                Arguments.of(LocalDate.of(2025, 3, 20), "7:32", "19:39"),
                Arguments.of(LocalDate.of(2025, 4, 20), "6:41", "20:12"),
                Arguments.of(LocalDate.of(2025, 6, 21), "5:54", "21:03"));
    }

    @ParameterizedTest
    @MethodSource("almanac")
    void sunriseAndSunsetMatchTheAlmanac(LocalDate day, String sunrise, String sunset) {
        assertThat(SUMMIT.sunrise(day)).isEqualTo(TimeOfDay.parse(sunrise));
        assertThat(SUMMIT.sunset(day)).isEqualTo(TimeOfDay.parse(sunset));
    }

    @Test
    void theShortestDayOfTheSeasonIsAroundTheSolstice() {
        long shortest = IntStream.range(18, 26)
                .mapToLong(day -> SUMMIT.daylight(LocalDate.of(2024, 12, day)).seconds())
                .min()
                .orElseThrow();
        assertThat(SUMMIT.daylight(LocalDate.of(2024, 12, 21)).seconds()).isEqualTo(shortest);
    }

    @Test
    void civilTwilightBracketsTheSun() {
        LocalDate day = LocalDate.of(2025, 1, 15);
        assertThat(SUMMIT.civilDawn(day)).isLessThan(SUMMIT.sunrise(day));
        assertThat(SUMMIT.civilDusk(day)).isGreaterThan(SUMMIT.sunset(day));
        assertThat(SUMMIT.civilDawn(day)).isEqualTo(TimeOfDay.parse("7:20"));
        assertThat(SUMMIT.civilDusk(day)).isEqualTo(TimeOfDay.parse("17:52"));
    }

    @Test
    void daylightIsTheSpanBetweenTheTwo() {
        assertThat(SUMMIT.daylight(LocalDate.of(2025, 1, 15))).isEqualTo(Duration.ofHms(9, 31, 0));
        assertThat(SUMMIT.daylight(LocalDate.of(2025, 6, 21)))
                .isGreaterThan(SUMMIT.daylight(LocalDate.of(2024, 12, 21)));
    }

    @Test
    void theClockMovesInMarchAndBackInNovember() {
        assertThat(Daylight.inSummerTime(LocalDate.of(2025, 3, 8))).isFalse();
        assertThat(Daylight.inSummerTime(LocalDate.of(2025, 3, 9))).isTrue();
        assertThat(Daylight.inSummerTime(LocalDate.of(2025, 11, 1))).isTrue();
        assertThat(Daylight.inSummerTime(LocalDate.of(2025, 11, 2))).isFalse();
        assertThat(Daylight.inSummerTime(LocalDate.of(2025, 1, 1))).isFalse();
    }

    @Test
    void summerTimeShiftsTheReportedTimes() {
        Site standard = new Site("standard", 41.216, -111.857, -420, false);
        LocalDate day = LocalDate.of(2025, 4, 20);
        assertThat(SUMMIT.sunrise(day).minutes() - standard.sunrise(day).minutes())
                .isEqualTo(60);
    }

    @Test
    void aSiteReportsTheOffsetInForce() {
        assertThat(SUMMIT.offsetMinutes(LocalDate.of(2025, 1, 15))).isEqualTo(-420);
        assertThat(SUMMIT.offsetMinutes(LocalDate.of(2025, 4, 20))).isEqualTo(-360);
    }

    @Test
    void whetherAMomentIsInTheLight() {
        LocalDate day = LocalDate.of(2025, 1, 15);
        assertThat(SUMMIT.isDaylight(day, TimeOfDay.at(12))).isTrue();
        assertThat(SUMMIT.isDaylight(day, TimeOfDay.at(7))).isFalse();
        assertThat(SUMMIT.isDaylight(day, TimeOfDay.at(18))).isFalse();
    }

    @Test
    void theLatestStartLeavesRoomToFinishBeforeDark() {
        LocalDate day = LocalDate.of(2025, 1, 15);
        assertThat(SUMMIT.latestStart(day, Duration.ofHours(3))).isEqualTo(TimeOfDay.at(14, 22));
        assertThat(SUMMIT.latestStart(day, Duration.ofMinutes(0))).isEqualTo(SUMMIT.sunset(day));
    }

    @Test
    void aJobLongerThanTheDayHasNoStart() {
        assertThatThrownBy(() -> SUMMIT.latestStart(LocalDate.of(2024, 12, 21), Duration.ofHours(20)))
                .isInstanceOf(DaylightError.class)
                .hasMessage("20:00:00 does not fit in the daylight of 2024-12-21");
    }

    @Test
    void whetherAJobFitsFromAGivenStart() {
        LocalDate day = LocalDate.of(2025, 1, 15);
        assertThat(SUMMIT.fitsInDaylight(day, TimeOfDay.at(14), Duration.ofHours(3)))
                .isTrue();
        assertThat(SUMMIT.fitsInDaylight(day, TimeOfDay.at(15), Duration.ofHours(3)))
                .isFalse();
        assertThat(SUMMIT.fitsInDaylight(day, TimeOfDay.at(6), Duration.ofHours(1)))
                .isFalse();
    }

    @Test
    void aPlaceWhereTheSunDoesNotSetIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> POLE.sunrise(LocalDate.of(2025, 6, 21))).isInstanceOf(DaylightError.class);
        assertThatThrownBy(() -> POLE.sunset(LocalDate.of(2024, 12, 21))).isInstanceOf(DaylightError.class);
    }

    @Test
    void aSiteChecksItsOwnCoordinates() {
        assertThatThrownBy(() -> new Site("bad", 95.0, 0.0, 0))
                .isInstanceOf(RangeError.class)
                .hasMessage("latitude 95.0 is outside -90 to 90");
        assertThatThrownBy(() -> new Site("bad", 0.0, 200.0, 0)).isInstanceOf(RangeError.class);
    }

    @Test
    void aLeapYearDoesNotShiftTheSolsticeByADay() {
        assertThat(SUMMIT.sunset(LocalDate.of(2024, 12, 21))).isEqualTo(TimeOfDay.parse("17:01"));
        assertThat(SUMMIT.sunset(LocalDate.of(2023, 12, 21))).isEqualTo(TimeOfDay.parse("17:01"));
    }
}
