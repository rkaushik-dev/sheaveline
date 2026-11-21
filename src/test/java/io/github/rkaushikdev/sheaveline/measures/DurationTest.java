package io.github.rkaushikdev.sheaveline.measures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Constructors;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationTest {

    @Test
    void secondsAreTheBaseUnit() {
        assertThat(Duration.ofMinutes(1).seconds()).isEqualTo(60);
        assertThat(Duration.ofHours(2).seconds()).isEqualTo(7200);
        assertThat(Duration.ofHms(1, 8, 30).seconds()).isEqualTo(4110);
    }

    @Test
    void facesReadBack() {
        Duration ride = Duration.ofHms(0, 8, 30);
        assertThat(ride.minutes()).isEqualByComparingTo("8.5");
        assertThat(Duration.ofHours(3).hours()).isEqualByComparingTo("3");
    }

    @Test
    void aDurationIsNeverNegative() {
        assertThatThrownBy(() -> new Duration(-1)).isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> Duration.ofMinutes(5).minus(Duration.ofMinutes(9)))
                .isInstanceOf(RangeError.class);
    }

    @Test
    void theConstructorWantsWholeSeconds() {
        Constructors.takeOnly(Duration.class, long.class);
        assertThatThrownBy(() -> Duration.ofMinutes("0.001"))
                .isInstanceOf(RangeError.class)
                .hasMessage("minutes 0.060 is outside a whole number of seconds to ");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "8:30|510",
                "0:45|45",
                "1:08:30|4110",
                "12:00:00|43200",
                "90 s|90",
                "90s|90",
                "12 min|720",
                "2.5 hours|9000",
                "'  3 hr '|10800"
            })
    void parseReadsTheShapesALogUses(String text, long seconds) {
        assertThat(Duration.parse(text).seconds()).isEqualTo(seconds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "half an hour", "8:75", "1:75:00", "30", "-5 min"})
    void parseRefusesWhatIsNotADuration(String text) {
        assertThatThrownBy(() -> Duration.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void clockFaceIsMinutesAndSeconds() {
        assertThat(Duration.ofHms(0, 8, 30).formatClock()).isEqualTo("8:30");
        assertThat(Duration.ofHms(1, 8, 30).formatClock()).isEqualTo("68:30");
        assertThat(Duration.ofSeconds(7).formatClock()).isEqualTo("0:07");
    }

    @Test
    void hmsFaceCarriesTheHours() {
        assertThat(Duration.ofHms(1, 8, 30).formatHms()).isEqualTo("1:08:30");
        assertThat(Duration.ofSeconds(45)).hasToString("0:00:45");
        assertThat(Duration.ofHms(2, 3, 4).splitHms()).isEqualTo(new Duration.Hms(2, 3, 4));
    }

    @Test
    void arithmeticAndOrdering() {
        Duration shorter = Duration.ofMinutes(8);
        Duration longer = Duration.ofMinutes(12);
        assertThat(shorter.plus(longer)).isEqualTo(Duration.ofMinutes(20));
        assertThat(longer.minus(shorter)).isEqualTo(Duration.ofMinutes(4));
        assertThat(shorter).isLessThan(longer);
        assertThat(longer).isGreaterThan(shorter);
        assertThat(shorter).isLessThanOrEqualTo(Duration.ofMinutes(8));
        assertThat(shorter).isGreaterThanOrEqualTo(Duration.ofMinutes(8));
        assertThat(shorter).isNotEqualTo(longer);
        assertThat(new HashSet<>(List.of(shorter, Duration.ofSeconds(480)))).hasSize(1);
    }

    @Test
    void aDurationNeverEqualsABareNumber() {
        assertThat((Object) Duration.ofMinutes(1)).isNotEqualTo(60L);
    }

    @Test
    void scalingRoundsToTheNearestSecond() {
        assertThat(Duration.ofMinutes(10).scaled("1.5")).isEqualTo(Duration.ofMinutes(15));
        assertThat(Duration.ofSeconds(7).scaled("0.5")).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void zeroAnswersForItself() {
        assertThat(Duration.ZERO.isZero()).isTrue();
        assertThat(Duration.ofSeconds(1).isZero()).isFalse();
        assertThat(Duration.ZERO.repr()).isEqualTo("Duration.ofSeconds(0)");
    }
}
