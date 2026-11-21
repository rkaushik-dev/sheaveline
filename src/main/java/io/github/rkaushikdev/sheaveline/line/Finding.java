package io.github.rkaushikdev.sheaveline.line;

import java.util.Objects;

/**
 * One thing wrong with the line, where it is and what it costs.
 *
 * @param where the span or tower, such as {@code span 3} or {@code JP-T4}
 * @param severity what it costs
 * @param detail what was found
 */
public record Finding(String where, Severity severity, String detail) {

    /** A finding, nothing left null. */
    public Finding {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(detail, "detail");
    }

    /** Where and what, such as {@code span 3: clearance 4.0 ft over open ground}. */
    @Override
    public String toString() {
        return where + ": " + detail;
    }
}
