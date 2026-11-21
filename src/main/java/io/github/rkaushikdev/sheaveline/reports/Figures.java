package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;

/** The faces the pages print their figures in, so a figure reads the same on every page. */
final class Figures {

    private Figures() {}

    /**
     * A fraction as a percentage to one place.
     *
     * @param fraction the fraction, such as {@code 0.857}
     * @return such as {@code 85.7%}
     */
    static String percent(BigDecimal fraction) {
        return Decimals.fixed(Decimals.multiply(fraction, 100), 1) + "%";
    }

    /**
     * A duration in hours.
     *
     * @param duration the duration
     * @param places digits after the point
     * @return such as {@code 7 h} or {@code 0.5 h}
     */
    static String hours(Duration duration, int places) {
        return Decimals.fixed(duration.hours(), places) + " h";
    }

    /**
     * A length in whole feet.
     *
     * @param length the length
     * @return such as {@code 1200 ft}
     */
    static String feet(Length length) {
        return Decimals.fixed(length.feet(), 0) + " ft";
    }
}
