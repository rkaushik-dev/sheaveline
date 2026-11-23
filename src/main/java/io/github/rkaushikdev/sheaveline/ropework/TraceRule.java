package io.github.rkaushikdev.sheaveline.ropework;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One reading off a magnetic test trace, with the figure that condemns the rope and the figure that
 * puts it on watch.
 *
 * <p>A trace reading has two thresholds rather than one row each, because the contractor's report
 * gives one figure per reading and the rope file prints one line per figure. A loss of area that
 * condemns the rope is not also a loss of area that watches it.
 *
 * @param name what is read off the trace, as the contractor's report names it
 * @param reading how the figure is taken off a test
 * @param condemnAbove a figure above this condemns the rope
 * @param watchAbove a figure above this, and not above the condemning one, puts it on watch
 * @param describe how the figure is printed in the rope file
 */
public record TraceRule(
        String name,
        Function<MagneticTest, BigDecimal> reading,
        BigDecimal condemnAbove,
        BigDecimal watchAbove,
        Function<BigDecimal, String> describe) {

    /**
     * A row.
     *
     * @throws RopeworkError when the watch figure is above the condemning one
     */
    public TraceRule {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(condemnAbove, "condemnAbove");
        Objects.requireNonNull(watchAbove, "watchAbove");
        Objects.requireNonNull(describe, "describe");
        if (watchAbove.compareTo(condemnAbove) > 0) {
            throw new RopeworkError("a trace is watched before it is condemned, not after");
        }
    }

    /**
     * What the figure costs, before it is printed.
     *
     * @param figure the figure read off a trace
     * @return condemned above the condemning figure, monitor above the watch figure, otherwise
     *     serviceable
     */
    public WearVerdict judge(BigDecimal figure) {
        if (figure.compareTo(condemnAbove) > 0) {
            return WearVerdict.CONDEMNED;
        }
        if (figure.compareTo(watchAbove) > 0) {
            return WearVerdict.MONITOR;
        }
        return WearVerdict.SERVICEABLE;
    }

    /**
     * Read the row against a test.
     *
     * @param test the test
     * @return one finding at the worse of the two levels the figure reaches, or empty when it
     *     reaches neither
     */
    public Optional<Finding> check(MagneticTest test) {
        BigDecimal figure = reading.apply(test);
        WearVerdict cost = judge(figure);
        if (cost == WearVerdict.SERVICEABLE) {
            return Optional.empty();
        }
        return Optional.of(new Finding("magnetic test", cost, describe.apply(figure)));
    }
}
