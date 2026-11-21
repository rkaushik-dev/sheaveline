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

class AngleTest {

    @Test
    void millidegreesAreTheBaseUnit() {
        assertThat(Angle.ofDegrees(3).millidegrees()).isEqualTo(3000);
        assertThat(Angle.ofDegrees("2.75").millidegrees()).isEqualTo(2750);
        assertThat(Angle.ofMillidegrees(1500).degrees()).isEqualByComparingTo("1.5");
    }

    @Test
    void aSlopeReadsAsAGradeOrAsDegrees() {
        Angle slope = Angle.ofPercentGrade(50);
        assertThat(slope.millidegrees()).isEqualTo(26565);
        assertThat(Decimals.quantize(slope.percentGrade(), 2)).isEqualByComparingTo("50.00");
    }

    @Test
    void riseOverRunIsHowASurveyHandsItOver() {
        Angle slope = Angle.ofRiseRun(Length.ofFeet(500), Length.ofFeet(2000));
        assertThat(Decimals.quantize(slope.percentGrade(), 1)).isEqualByComparingTo("25.0");
        assertThat(slope.isUphill()).isTrue();
    }

    @Test
    void aDownhillRunComesBackNegative() {
        Angle slope = Angle.ofRiseRun(Length.ofFeet(-120), Length.ofFeet(800));
        assertThat(slope.millidegrees()).isNegative();
        assertThat(slope.isUphill()).isFalse();
    }

    @Test
    void aZeroRunIsRefused() {
        assertThatThrownBy(() -> Angle.ofRiseRun(Length.ofFeet(10), Length.ofFeet(0)))
                .isInstanceOf(RangeError.class);
    }

    @Test
    void radiansRoundTrip() {
        for (String degrees : new String[] {"0", "15", "26.57", "45", "89"}) {
            Angle angle = Angle.ofDegrees(degrees);
            assertThat(Angle.ofRadians(angle.radians())).isEqualTo(angle);
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"3|3000", "3 deg|3000", "2.75 degrees|2750", "-1.5 d|-1500", "'  12  '|12000"})
    void parseReadsAnAngle(String text, long millidegrees) {
        assertThat(Angle.parse(text).millidegrees()).isEqualTo(millidegrees);
    }

    @Test
    void parseReadsAGradeWhenItSeesAPercent() {
        assertThat(Angle.parse("25%")).isEqualTo(Angle.ofPercentGrade(25));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "steep", "3 rad", "1.2.3"})
    void parseRefusesWhatIsNotAnAngle(String text) {
        assertThatThrownBy(() -> Angle.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void arithmeticAndOrdering() {
        Angle small = Angle.ofDegrees(2);
        Angle large = Angle.ofDegrees(9);
        assertThat(small.plus(large)).isEqualTo(Angle.ofDegrees(11));
        assertThat(large.minus(small)).isEqualTo(Angle.ofDegrees(7));
        assertThat(small.negated()).isEqualTo(Angle.ofDegrees(-2));
        assertThat(Angle.ofDegrees(-2).abs()).isEqualTo(small);
        assertThat(small).isLessThan(large);
        assertThat(large).isGreaterThan(small);
        assertThat(small).isLessThanOrEqualTo(Angle.ofDegrees(2));
        assertThat(small).isGreaterThanOrEqualTo(Angle.ofDegrees(2));
        assertThat(small).isNotEqualTo(large);
        assertThat((Object) small).isNotEqualTo(2L);
        assertThat(new HashSet<>(List.of(small, Angle.ofDegrees(2)))).hasSize(1);
    }

    @Test
    void aFlatAngleSaysSo() {
        assertThat(Angle.ZERO.isFlat()).isTrue();
        assertThat(Angle.ofDegrees(1).isFlat()).isFalse();
        assertThat(Angle.ZERO.percentGrade()).isEqualByComparingTo("0");
    }

    @Test
    void theConstructorRefusesAFloat() {
        Constructors.takeOnly(Angle.class, long.class);
        Constructors.takeOnly(Bearing.class, long.class);
    }

    @Test
    void formatting() {
        assertThat(Angle.ofDegrees("2.755").formatDegrees()).isEqualTo("2.76 deg");
        assertThat(Angle.ofPercentGrade(25).formatGrade()).isEqualTo("25.0%");
        assertThat(Angle.ofDegrees(3)).hasToString("3.00 deg");
        assertThat(Angle.ofDegrees(3).repr()).isEqualTo("Angle.ofMillidegrees(3000)");
    }

    @Test
    void aBearingWrapsIntoOneCircle() {
        assertThat(Bearing.ofDegrees(370)).isEqualTo(Bearing.ofDegrees(10));
        assertThat(Bearing.ofDegrees(-10)).isEqualTo(Bearing.ofDegrees(350));
        assertThat(Bearing.ofDegrees(360)).isEqualTo(Bearing.ofDegrees(0));
    }

    @Test
    void compassPointsReadBothWays() {
        assertThat(Bearing.ofPoint("SW").degrees()).isEqualByComparingTo("225");
        assertThat(Bearing.ofPoint("nnw").degrees()).isEqualByComparingTo("337.5");
        assertThat(Bearing.ofDegrees(226).nearestPoint()).isEqualTo("SW");
        assertThat(Bearing.ofDegrees(0).nearestPoint()).isEqualTo("N");
        assertThat(Bearing.ofDegrees(359).nearestPoint()).isEqualTo("N");
    }

    @Test
    void aBearingParsesFromEitherFace() {
        assertThat(Bearing.parse("270")).isEqualTo(Bearing.ofPoint("W"));
        assertThat(Bearing.parse(" wsw ")).isEqualTo(Bearing.ofDegrees("247.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "NNNE", "up the hill"})
    void aBearingRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> Bearing.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void theReciprocalIsTheOtherWayAlongTheLine() {
        assertThat(Bearing.ofDegrees(30).reciprocal()).isEqualTo(Bearing.ofDegrees(210));
        assertThat(Bearing.ofDegrees(210).reciprocal()).isEqualTo(Bearing.ofDegrees(30));
    }

    @Test
    void differenceTakesTheShortWayRound() {
        assertThat(Bearing.ofDegrees(10).difference(Bearing.ofDegrees(350))).isEqualTo(Angle.ofDegrees(-20));
        assertThat(Bearing.ofDegrees(350).difference(Bearing.ofDegrees(10))).isEqualTo(Angle.ofDegrees(20));
        assertThat(Bearing.ofDegrees(0).difference(Bearing.ofDegrees(180))).isEqualTo(Angle.ofDegrees(180));
    }

    @Test
    void separationIgnoresWhichWayRound() {
        Bearing across = Bearing.ofDegrees(90);
        assertThat(across.separation(Bearing.ofDegrees(70))).isEqualTo(Angle.ofDegrees(20));
        assertThat(across.separation(Bearing.ofDegrees(110))).isEqualTo(Angle.ofDegrees(20));
    }

    @Test
    void aBearingPrintsWithItsPoint() {
        assertThat(Bearing.ofDegrees(225)).hasToString("225 deg (SW)");
        assertThat(Bearing.ofDegrees(225).repr()).isEqualTo("new Bearing(225000)");
        assertThat(new HashSet<>(List.of(Bearing.ofDegrees(225), Bearing.ofPoint("SW"))))
                .hasSize(1);
        assertThat((Object) Bearing.ofDegrees(225)).isNotEqualTo(225L);
    }
}
