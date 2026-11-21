package io.github.rkaushikdev.sheaveline.measures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Constructors;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpeedTest {

    @Test
    void feetPerMinuteIsTheFaceTheBoardAsksFor() {
        assertThat(Speed.ofFeetPerMinute(500).feetPerMinute()).isEqualByComparingTo("500");
        assertThat(Speed.ofFeetPerMinute(1000).milsPerMinute()).isEqualTo(12_000_000);
    }

    @Test
    void metresPerSecondIsTheFaceTheManualUses() {
        Speed quad = Speed.ofMetresPerSecond(5);
        assertThat(Decimals.quantize(quad.feetPerMinute(), 1)).isEqualByComparingTo("984.3");
        assertThat(Decimals.quantize(quad.metresPerSecond(), 3)).isEqualByComparingTo("5.000");
    }

    @Test
    void aRoundTripThroughEitherFaceComesBack() {
        for (long fpm : new long[] {220, 450, 500, 1000}) {
            Speed speed = Speed.ofFeetPerMinute(fpm);
            assertThat(Speed.ofMetresPerSecond(speed.metresPerSecond())).isEqualTo(speed);
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"500 fpm|500", "500fpm|500", "450 ft/min|450", "220 feet per minute|220"})
    void parseReadsTheCustomaryFace(String text, long fpm) {
        assertThat(Speed.parse(text).feetPerMinute()).isEqualByComparingTo(Decimals.of(fpm));
    }

    @Test
    void parseReadsTheMetricFace() {
        assertThat(Speed.parse("2.5 m/s")).isEqualTo(Speed.ofMetresPerSecond("2.5"));
        assertThat(Speed.parse("2.5 metres per second")).isEqualTo(Speed.ofMetresPerSecond("2.5"));
        assertThat(Speed.parse("2.5mps")).isEqualTo(Speed.ofMetresPerSecond("2.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fast", "500", "5 m/min", "500 knots"})
    void parseRefusesWhatIsNotASpeed(String text) {
        assertThatThrownBy(() -> Speed.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void travelAnswersHowFarTheRopeWent() {
        Speed speed = Speed.ofFeetPerMinute(500);
        assertThat(speed.travel(Duration.ofMinutes(2))).isEqualTo(Length.ofFeet(1000));
        assertThat(speed.travel(Duration.ofSeconds(30))).isEqualTo(Length.ofFeet(250));
    }

    @Test
    void timeToCoverAnswersTheRideTime() {
        Speed speed = Speed.ofFeetPerMinute(500);
        assertThat(speed.timeToCover(Length.ofFeet(4250))).isEqualTo(Duration.ofSeconds(510));
    }

    @Test
    void timeToCoverRefusesAStoppedRope() {
        assertThatThrownBy(() -> Speed.STOPPED.timeToCover(Length.ofFeet(100))).isInstanceOf(RangeError.class);
    }

    @Test
    void travelAndTimeToCoverAgree() {
        Speed speed = Speed.ofFeetPerMinute(450);
        Length distance = Length.ofFeet(3000);
        Duration elapsed = speed.timeToCover(distance);
        assertThat(Math.abs(speed.travel(elapsed).mils() - distance.mils())).isLessThan(12_000);
    }

    @Test
    void aSpeedIsNeverNegative() {
        assertThatThrownBy(() -> new Speed(-1)).isInstanceOf(RangeError.class);
        assertThatThrownBy(() -> Speed.ofFeetPerMinute(-100)).isInstanceOf(RangeError.class);
        Constructors.takeOnly(Speed.class, long.class);
    }

    @Test
    void orderingAndEquality() {
        Speed slow = Speed.ofFeetPerMinute(220);
        Speed fast = Speed.ofFeetPerMinute(1000);
        assertThat(slow).isLessThan(fast);
        assertThat(fast).isGreaterThan(slow);
        assertThat(slow).isLessThanOrEqualTo(Speed.ofFeetPerMinute(220));
        assertThat(slow).isGreaterThanOrEqualTo(Speed.ofFeetPerMinute(220));
        assertThat(slow).isNotEqualTo(fast);
        assertThat((Object) slow).isNotEqualTo(220L);
        assertThat(new HashSet<>(List.of(slow, Speed.ofFeetPerMinute(220)))).hasSize(1);
    }

    @Test
    void scalingIsHowAReducedSpeedIsWritten() {
        assertThat(Speed.ofFeetPerMinute(500).scaled("0.5")).isEqualTo(Speed.ofFeetPerMinute(250));
    }

    @Test
    void formatting() {
        assertThat(Speed.ofFeetPerMinute(500).formatFpm()).isEqualTo("500 fpm");
        assertThat(Speed.ofFeetPerMinute(1000)).hasToString("1000 fpm");
        assertThat(Speed.ofMetresPerSecond(5).formatMps()).isEqualTo("5.00 m/s");
    }

    @Test
    void stoppedAnswersForItself() {
        assertThat(Speed.STOPPED.isStopped()).isTrue();
        assertThat(Speed.ofFeetPerMinute(1).isStopped()).isFalse();
        assertThat(Speed.STOPPED.travel(Duration.ofHours(1)).isZero()).isTrue();
    }
}
