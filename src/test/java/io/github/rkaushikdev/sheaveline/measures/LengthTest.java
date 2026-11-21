package io.github.rkaushikdev.sheaveline.measures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Constructors;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LengthTest {

    @Test
    void milsAreTheBaseUnit() {
        assertThat(Length.ofInches(1).mils()).isEqualTo(1000);
        assertThat(Length.ofFeet(1).mils()).isEqualTo(12000);
        assertThat(Length.ofMils(1378).inches()).isEqualByComparingTo("1.378");
    }

    @Test
    void feetAndInchesCombine() {
        assertThat(Length.ofFeetInches(12, 6).mils()).isEqualTo(12 * 12000 + 6000);
        assertThat(Length.ofFeetInches(0, 3).inches()).isEqualByComparingTo("3");
    }

    @Test
    void negativeFeetCarryTheSignIntoTheInches() {
        Length length = Length.ofFeetInches(-4, 6);
        assertThat(length.mils()).isEqualTo(-(4 * 12000 + 6000));
        Length.FeetInches split = length.splitFeetInches();
        assertThat(split.feet()).isEqualTo(-4);
        assertThat(split.inches()).isEqualByComparingTo("-6");
    }

    @Test
    void ropeDiameterKeepsThreePlaces() {
        assertThat(Length.ofInches("1.378").mils()).isEqualTo(1378);
        assertThat(Length.ofInches(new BigDecimal("0.875")).mils()).isEqualTo(875);
    }

    @Test
    void roundingIsHalfAwayFromZero() {
        assertThat(Length.ofInches("0.0005").mils()).isEqualTo(1);
        assertThat(Length.ofInches("-0.0005").mils()).isEqualTo(-1);
        assertThat(Length.ofInches("0.00049").mils()).isZero();
    }

    static Stream<Arguments> drawingShapes() {
        return Stream.of(
                Arguments.of("148", 148000L),
                Arguments.of("148 ft", 148 * 12000L),
                Arguments.of("148ft", 148 * 12000L),
                Arguments.of("12'6\"", 12 * 12000L + 6000),
                Arguments.of("12' 6", 12 * 12000L + 6000),
                Arguments.of("1.378 in", 1378L),
                Arguments.of("6 feet 3 inches", 6 * 12000L + 3000),
                Arguments.of("  40'  ", 40 * 12000L),
                Arguments.of("4800.00 ft", 4800 * 12000L),
                Arguments.of("148.5 feet", 148 * 12000L + 6000));
    }

    @ParameterizedTest
    @MethodSource("drawingShapes")
    void parseReadsTheShapesADrawingUses(String text, long mils) {
        assertThat(Length.parse(text).mils()).isEqualTo(mils);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ft", "twelve feet", "12 ft 6 ft", "1.2.3", "12.5 ft 6 in"})
    void parseRefusesWhatIsNotALength(String text) {
        assertThatThrownBy(() -> Length.parse(text)).isInstanceOf(ParseError.class);
    }

    @Test
    void parseErrorSaysWhatItWanted() {
        assertThatThrownBy(() -> Length.parse("nine")).isInstanceOfSatisfying(ParseError.class, caught -> {
            assertThat(caught.wanted()).isEqualTo("a length");
            assertThat(caught.text()).isEqualTo("nine");
            assertThat(caught).hasMessage("cannot read 'nine' as a length");
        });
    }

    @Test
    void arithmeticStaysExact() {
        Length a = Length.ofFeet(148);
        Length b = Length.ofFeetInches(3, 6);
        assertThat(a.plus(b).mils()).isEqualTo(148 * 12000 + 42000);
        assertThat(a.minus(b).mils()).isEqualTo(148 * 12000 - 42000);
        assertThat(b.negated().mils()).isEqualTo(-42000);
        assertThat(b.negated().abs()).isEqualTo(b);
    }

    @Test
    void scalingRoundsToTheNearestMil() {
        assertThat(Length.ofInches(1).scaled("0.3333").mils()).isEqualTo(333);
        assertThat(Length.ofFeet(100).scaled(2)).isEqualTo(Length.ofFeet(200));
    }

    @Test
    void ratioIsDimensionless() {
        assertThat(Length.ofFeet(200).ratio(Length.ofFeet(100))).isEqualByComparingTo("2");
    }

    @Test
    void ratioRefusesAZeroDivisor() {
        assertThatThrownBy(() -> Length.ofFeet(1).ratio(Length.ZERO)).isInstanceOf(RangeError.class);
    }

    @Test
    void orderingAndEquality() {
        Length shorter = Length.ofFeet(10);
        Length longer = Length.ofFeet(11);
        assertThat(shorter).isLessThan(longer);
        assertThat(longer).isGreaterThan(shorter);
        assertThat(shorter).isLessThanOrEqualTo(Length.ofFeet(10));
        assertThat(shorter).isGreaterThanOrEqualTo(Length.ofFeet(10));
        assertThat(shorter).isEqualTo(Length.ofInches(120));
        assertThat(shorter).isNotEqualTo(longer);
        assertThat(new HashSet<>(List.of(shorter, Length.ofInches(120)))).hasSize(1);
    }

    @Test
    void aLengthNeverEqualsABareNumber() {
        assertThat((Object) Length.ofInches(1)).isNotEqualTo(1000L);
    }

    @Test
    void theConstructorRefusesAFloat() {
        Constructors.takeOnly(Length.class, long.class);
    }

    @Test
    void formattingReadsLikeADrawing() {
        assertThat(Length.ofFeetInches(12, 6).formatFeetInches()).isEqualTo("12' 6\"");
        assertThat(Length.ofFeet(148).formatFeetInches()).isEqualTo("148' 0\"");
        assertThat(Length.ofInches("1.378").formatInches()).isEqualTo("1.378\"");
        assertThat(Length.ofFeetInches(3, new BigDecimal("1.5"))).hasToString("3' 1.5\"");
    }

    @Test
    void reprRoundTrips() {
        Length length = Length.ofFeetInches(7, 9);
        assertThat(length.repr()).isEqualTo("Length.ofMils(" + length.mils() + ")");
        long read = Long.parseLong(length.repr().replaceAll("\\D", ""));
        assertThat(Length.ofMils(read)).isEqualTo(length);
    }

    @Test
    void zeroIsSharedAndAnswersForItself() {
        assertThat(Length.ZERO.isZero()).isTrue();
        assertThat(Length.ofMils(1).isZero()).isFalse();
        assertThat(Length.ZERO.feet()).isEqualByComparingTo("0");
    }
}
