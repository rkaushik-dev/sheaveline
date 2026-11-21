package io.github.rkaushikdev.sheaveline.upkeep;

import java.util.Objects;

/**
 * One piece of evidence about a machine's fitness, and what it costs.
 *
 * @param source where the evidence comes from: {@code ladder}, {@code record book}, {@code rope
 *     file} or {@code line walk}
 * @param status the most the machine may do on this evidence alone
 * @param detail what the evidence says
 */
public record StatusReason(String source, ServiceStatus status, String detail) {

    /** A reason. */
    public StatusReason {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    /** Such as {@code rope file: rope condemned}. */
    @Override
    public String toString() {
        return source + ": " + detail;
    }
}
