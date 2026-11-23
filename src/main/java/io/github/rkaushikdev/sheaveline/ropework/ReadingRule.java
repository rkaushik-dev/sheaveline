package io.github.rkaushikdev.sheaveline.ropework;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One figure read off a survey or a test, with the figure that condemns the rope and the figure
 * that puts it on watch.
 *
 * <p>A reading is one line in the rope file whichever level it reaches. A rope at ninety two percent
 * of nominal is a condemned rope, and printing that it is also a rope to watch is a second line the
 * inspector has to read past. So each reading carries both of its thresholds and finds once, at the
 * worse level it reaches, and a reading that is only ever watched has no condemning figure at all.
 *
 * @param <S> what the figure is read off: a wear survey or a magnetic test
 * @param source what the rope file calls the finding, such as {@code diameter} or {@code magnetic
 *     test}
 * @param name the reading, as the rule table names it
 * @param reading how the figure is taken off what was read
 * @param condemnPast a figure past this condemns the rope, when the reading ever does
 * @param watchPast a figure past this puts the rope on watch
 * @param worseBelow true when a smaller figure is the worse one, as for diameter
 * @param describe how the figure is printed in the rope file
 */
public record ReadingRule<S>(
        String source,
        String name,
        Function<S, BigDecimal> reading,
        Optional<BigDecimal> condemnPast,
        BigDecimal watchPast,
        boolean worseBelow,
        Function<BigDecimal, String> describe) {

    /**
     * A row.
     *
     * @throws RopeworkError when the condemning figure is on the good side of the watch figure
     */
    public ReadingRule {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(condemnPast, "condemnPast");
        Objects.requireNonNull(watchPast, "watchPast");
        Objects.requireNonNull(describe, "describe");
        if (condemnPast.isPresent() && worse(watchPast, condemnPast.get(), worseBelow)) {
            throw new RopeworkError("a " + name + " reading is watched before it is condemned, not after");
        }
    }

    /**
     * A reading that is worse the higher it goes.
     *
     * @param <S> what it is read off
     * @param source what the finding is called
     * @param reading how the figure is taken
     * @param condemnAbove the condemning figure
     * @param watchAbove the watch figure
     * @param describe how it is printed
     * @return the row, named after its source
     */
    public static <S> ReadingRule<S> above(
            String source,
            Function<S, BigDecimal> reading,
            BigDecimal condemnAbove,
            BigDecimal watchAbove,
            Function<BigDecimal, String> describe) {
        return new ReadingRule<>(source, source, reading, Optional.of(condemnAbove), watchAbove, false, describe);
    }

    /**
     * A reading that is worse the lower it goes.
     *
     * @param <S> what it is read off
     * @param source what the finding is called
     * @param reading how the figure is taken
     * @param condemnBelow the condemning figure
     * @param watchBelow the watch figure
     * @param describe how it is printed
     * @return the row, named after its source
     */
    public static <S> ReadingRule<S> below(
            String source,
            Function<S, BigDecimal> reading,
            BigDecimal condemnBelow,
            BigDecimal watchBelow,
            Function<BigDecimal, String> describe) {
        return new ReadingRule<>(source, source, reading, Optional.of(condemnBelow), watchBelow, true, describe);
    }

    /**
     * A reading that is only ever watched, worse the higher it goes.
     *
     * @param <S> what it is read off
     * @param source what the finding is called
     * @param reading how the figure is taken
     * @param watchAbove the watch figure
     * @param describe how it is printed
     * @return the row, named after its source
     */
    public static <S> ReadingRule<S> watchedAbove(
            String source,
            Function<S, BigDecimal> reading,
            BigDecimal watchAbove,
            Function<BigDecimal, String> describe) {
        return new ReadingRule<>(source, source, reading, Optional.empty(), watchAbove, false, describe);
    }

    /**
     * The same row under another name, keeping the source the rope file prints.
     *
     * @param called the name
     * @return the renamed row
     */
    public ReadingRule<S> named(String called) {
        return new ReadingRule<>(source, called, reading, condemnPast, watchPast, worseBelow, describe);
    }

    /**
     * What a figure costs, before it is printed.
     *
     * @param figure the figure
     * @return condemned past the condemning figure, monitor past the watch figure, otherwise
     *     serviceable
     */
    public WearVerdict judge(BigDecimal figure) {
        if (condemnPast.isPresent() && worse(figure, condemnPast.get(), worseBelow)) {
            return WearVerdict.CONDEMNED;
        }
        if (worse(figure, watchPast, worseBelow)) {
            return WearVerdict.MONITOR;
        }
        return WearVerdict.SERVICEABLE;
    }

    /**
     * Read the row against what was read.
     *
     * @param read the survey or the test
     * @return one finding at the worse of the levels the figure reaches, or empty when it reaches
     *     neither
     */
    public Optional<Finding> check(S read) {
        BigDecimal figure = reading.apply(read);
        WearVerdict cost = judge(figure);
        if (cost == WearVerdict.SERVICEABLE) {
            return Optional.empty();
        }
        return Optional.of(new Finding(source, cost, describe.apply(figure)));
    }

    private static boolean worse(BigDecimal figure, BigDecimal threshold, boolean worseBelow) {
        int against = figure.compareTo(threshold);
        return worseBelow ? against < 0 : against > 0;
    }
}
