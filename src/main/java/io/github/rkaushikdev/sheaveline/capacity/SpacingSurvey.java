package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Gaps measured between carriers on the line, which are never quite even.
 *
 * @param nominal the spacing the line is set to
 * @param gaps the gaps measured, in the order walked
 */
public record SpacingSurvey(Length nominal, List<Length> gaps) {

    /** How far a gap on an even line may depart from nominal, either way, as a fraction. */
    public static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.05");

    /**
     * A survey of real gaps against a real spacing.
     *
     * @throws CapacityError when the nominal spacing or any gap is not positive, or there are no gaps
     */
    public SpacingSurvey {
        Objects.requireNonNull(nominal, "nominal");
        gaps = List.copyOf(gaps);
        if (nominal.mils() <= 0) {
            throw new CapacityError("a spacing survey is read against a nominal spacing");
        }
        if (gaps.isEmpty()) {
            throw new CapacityError("a spacing survey has gaps in it");
        }
        for (Length gap : gaps) {
            if (gap.mils() <= 0) {
                throw new CapacityError("a measured gap is a positive length");
            }
        }
    }

    /**
     * A survey of the gaps against a nominal spacing.
     *
     * @param nominal the spacing the line is set to
     * @param gaps the gaps measured
     * @return the survey
     */
    public static SpacingSurvey of(Length nominal, List<Length> gaps) {
        return new SpacingSurvey(nominal, gaps);
    }

    /**
     * The widest gap.
     *
     * @return the largest
     */
    public Length widest() {
        return gaps.stream().max(Comparator.naturalOrder()).orElseThrow();
    }

    /**
     * The tightest gap.
     *
     * @return the smallest
     */
    public Length tightest() {
        return gaps.stream().min(Comparator.naturalOrder()).orElseThrow();
    }

    /**
     * How far apart the widest and tightest gaps are.
     *
     * @return the widest less the tightest
     */
    public Length spread() {
        return widest().minus(tightest());
    }

    /**
     * The largest departure from nominal, either way, as a fraction.
     *
     * @return such as {@code 0.4} for a gap forty percent off
     */
    public BigDecimal worstError() {
        return gaps.stream().map(this::error).max(Comparator.naturalOrder()).orElseThrow();
    }

    /**
     * Whether every gap is within five percent of nominal.
     *
     * @return true inside the tolerance
     */
    public boolean isEven() {
        return isEven(DEFAULT_TOLERANCE);
    }

    /**
     * Whether every gap is within a tolerance of nominal.
     *
     * @param tolerance the fraction, such as {@code "0.01"}
     * @return true inside the tolerance
     */
    public boolean isEven(String tolerance) {
        return isEven(Decimals.of(tolerance));
    }

    /**
     * Whether every gap is within a tolerance of nominal.
     *
     * @param tolerance the fraction, its limit included
     * @return true inside the tolerance
     */
    public boolean isEven(BigDecimal tolerance) {
        return worstError().compareTo(tolerance) <= 0;
    }

    /**
     * The gaps more than five percent off nominal.
     *
     * @return those gaps, in the order walked
     */
    public List<Length> outOfTolerance() {
        return outOfTolerance(DEFAULT_TOLERANCE);
    }

    /**
     * The gaps more than a tolerance off nominal.
     *
     * @param tolerance the fraction, such as {@code "0.01"}
     * @return those gaps, in the order walked
     */
    public List<Length> outOfTolerance(String tolerance) {
        return outOfTolerance(Decimals.of(tolerance));
    }

    /**
     * The gaps more than a tolerance off nominal.
     *
     * @param tolerance the fraction
     * @return those gaps, in the order walked
     */
    public List<Length> outOfTolerance(BigDecimal tolerance) {
        return gaps.stream().filter(gap -> error(gap).compareTo(tolerance) > 0).toList();
    }

    private BigDecimal error(Length gap) {
        return gap.minus(nominal).ratio(nominal).abs();
    }
}
