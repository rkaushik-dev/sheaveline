package io.github.rkaushikdev.sheaveline.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Exact decimal arithmetic in the one context the whole program computes in.
 *
 * <p>Every inexact quotient and product is carried to 28 significant digits with ties going to the
 * even digit. Exact results keep the scale they naturally have, so one thousand five hundred mils
 * over a thousand reads back as {@code 1.5} and not as {@code 1.500}. Printed figures and stored
 * ratios depend on that, so every measure divides through here rather than choosing its own scale.
 */
public final class Decimals {

    /** Twenty eight significant digits, ties to even. */
    public static final MathContext CONTEXT = new MathContext(28, RoundingMode.HALF_EVEN);

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private Decimals() {}

    /**
     * Read a decimal from text, ignoring white space around it.
     *
     * @param text the digits, such as {@code "1.378"}
     * @return the exact value written
     * @throws NumberFormatException when the text is not a decimal number
     */
    public static BigDecimal of(String text) {
        return new BigDecimal(text.strip());
    }

    /**
     * A whole number as a decimal.
     *
     * @param value the number
     * @return the same number, scale zero
     */
    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value);
    }

    /**
     * A binary floating point number as the shortest decimal that reads back as the same double.
     *
     * @param value a finite double
     * @return the decimal its shortest printed form spells
     */
    public static BigDecimal of(double value) {
        return new BigDecimal(Double.toString(value));
    }

    /**
     * Divide in the shared context.
     *
     * @param dividend the number divided
     * @param divisor the number to divide by, never zero
     * @return the quotient, exact where it can be and 28 digits where it cannot
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, CONTEXT);
    }

    /**
     * Divide one whole number by another in the shared context.
     *
     * @param dividend the number divided
     * @param divisor the number to divide by, never zero
     * @return the quotient
     */
    public static BigDecimal divide(long dividend, long divisor) {
        return divide(of(dividend), of(divisor));
    }

    /**
     * Divide a decimal by a whole number in the shared context.
     *
     * @param dividend the number divided
     * @param divisor the number to divide by, never zero
     * @return the quotient
     */
    public static BigDecimal divide(BigDecimal dividend, long divisor) {
        return divide(dividend, of(divisor));
    }

    /**
     * Multiply in the shared context.
     *
     * @param left one factor
     * @param right the other
     * @return the product, rounded only if it runs past 28 digits
     */
    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return left.multiply(right, CONTEXT);
    }

    /**
     * Multiply a decimal by a whole number in the shared context.
     *
     * @param left one factor
     * @param right the other
     * @return the product
     */
    public static BigDecimal multiply(BigDecimal left, long right) {
        return multiply(left, of(right));
    }

    /**
     * Round to the nearest whole number, a half going away from zero, the way a shop rounds.
     *
     * @param value the number
     * @return the nearest whole number
     */
    public static long roundHalfAway(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Round to the nearest whole number with a half going up toward positive infinity: the floor
     * of the value plus one half.
     *
     * @param value the number
     * @return the nearest whole number
     */
    public static long roundHalfUpward(BigDecimal value) {
        return value.add(HALF, CONTEXT).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /**
     * Round to a number of decimal places with ties going to the even digit, which is how every
     * printed figure is cut.
     *
     * @param value the number
     * @param places the digits wanted after the point, negative for tens and hundreds
     * @return the value at exactly that scale
     */
    public static BigDecimal quantize(BigDecimal value, int places) {
        return value.setScale(places, RoundingMode.HALF_EVEN);
    }

    /**
     * A decimal printed to a fixed number of places, ties going to the even digit.
     *
     * @param value the number
     * @param places digits after the point
     * @return such as {@code 148} or {@code 0.2500000000}, never in exponent form
     */
    public static String fixed(BigDecimal value, int places) {
        return quantize(value, places).toPlainString();
    }

    /**
     * A double printed to a fixed number of places from its exact binary value, ties going to the
     * even digit. {@code String.format("%.2f", 0.125)} gives {@code 0.13}; this gives {@code 0.12},
     * which is what the exact value calls for.
     *
     * @param value a finite double
     * @param places digits after the point
     * @return the fixed point text
     */
    public static String fixed(double value, int places) {
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * Whether the value is a whole number, whatever its scale.
     *
     * @param value the number
     * @return true for {@code 4}, {@code 4.0} and {@code 4E+2}
     */
    public static boolean isWhole(BigDecimal value) {
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    /**
     * The number with no trailing zeros and no exponent: {@code 3.120} prints as {@code 3.12} and
     * {@code 2.0} as {@code 2}.
     *
     * @param value the number
     * @return its shortest plain spelling
     */
    public static String trim(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
