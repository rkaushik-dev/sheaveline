package io.github.rkaushikdev.sheaveline.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalsTest {

    @Test
    void anExactQuotientKeepsItsNaturalScale() {
        assertThat(Decimals.divide(1500, 1000)).hasToString("1.5");
        assertThat(Decimals.divide(2000, 1000)).hasToString("2");
        assertThat(Decimals.divide(1, 8)).hasToString("0.125");
    }

    @Test
    void anInexactQuotientRunsToTwentyEightDigits() {
        assertThat(Decimals.divide(1, 3)).hasToString("0.3333333333333333333333333333");
        assertThat(Decimals.divide(2, 3)).hasToString("0.6666666666666666666666666667");
    }

    @Test
    void theTwoRoundingsDifferOnlyOnANegativeHalf() {
        assertThat(Decimals.roundHalfAway(new BigDecimal("2.5"))).isEqualTo(3);
        assertThat(Decimals.roundHalfUpward(new BigDecimal("2.5"))).isEqualTo(3);
        assertThat(Decimals.roundHalfAway(new BigDecimal("-2.5"))).isEqualTo(-3);
        assertThat(Decimals.roundHalfUpward(new BigDecimal("-2.5"))).isEqualTo(-2);
        assertThat(Decimals.roundHalfAway(new BigDecimal("-2.4"))).isEqualTo(-2);
    }

    @Test
    void quantizeTakesATieToTheEvenDigit() {
        assertThat(Decimals.quantize(new BigDecimal("2.5"), 0)).hasToString("2");
        assertThat(Decimals.quantize(new BigDecimal("3.5"), 0)).hasToString("4");
        assertThat(Decimals.quantize(new BigDecimal("1.005"), 2)).hasToString("1.00");
    }

    @Test
    void fixedReadsTheExactValueOfADouble() {
        assertThat(Decimals.fixed(0.125, 2)).isEqualTo("0.12");
        assertThat(Decimals.fixed(0.375, 2)).isEqualTo("0.38");
        assertThat(Decimals.fixed(2.675, 2)).isEqualTo("2.67");
        assertThat(Decimals.fixed(0.25, 10)).isEqualTo("0.2500000000");
        assertThat(Decimals.fixed(new BigDecimal("148.5"), 0)).isEqualTo("148");
    }

    @Test
    void trimDropsTrailingZerosAndNeverPrintsAnExponent() {
        assertThat(Decimals.trim(new BigDecimal("3.120"))).isEqualTo("3.12");
        assertThat(Decimals.trim(new BigDecimal("2.0"))).isEqualTo("2");
        assertThat(Decimals.trim(new BigDecimal("1500"))).isEqualTo("1500");
        assertThat(Decimals.trim(new BigDecimal("0.000"))).isEqualTo("0");
    }

    @Test
    void wholeNumbersAreWholeAtAnyScale() {
        assertThat(Decimals.isWhole(new BigDecimal("4.000"))).isTrue();
        assertThat(Decimals.isWhole(new BigDecimal("4E+2"))).isTrue();
        assertThat(Decimals.isWhole(BigDecimal.ZERO)).isTrue();
        assertThat(Decimals.isWhole(new BigDecimal("4.001"))).isFalse();
    }

    @Test
    void aDoubleReadsAsItsShortestSpelling() {
        assertThat(Decimals.of(0.1)).isEqualByComparingTo("0.1");
        assertThat(Decimals.of(" 1.378 ")).hasToString("1.378");
        assertThat(Decimals.multiply(new BigDecimal("1.5"), 2)).hasToString("3.0");
    }
}
