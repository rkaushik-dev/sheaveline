package io.github.rkaushikdev.sheaveline.ropework;

import java.util.Objects;

/**
 * One row of the rope file that fired, with the reading that fired it.
 *
 * @param source what was read, such as {@code diameter} or {@code broken wires}
 * @param verdict what breaching the row costs
 * @param detail the reading, as the rope file prints it
 */
public record Finding(String source, WearVerdict verdict, String detail) {

    /** A finding. */
    public Finding {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(detail, "detail");
    }

    /** Such as {@code diameter: 92.2% of nominal}. */
    @Override
    public String toString() {
        return source + ": " + detail;
    }
}
