package io.github.rkaushikdev.sheaveline.weather;

import java.util.Objects;

/**
 * One reason a machine is not turning.
 *
 * @param reason what is holding it
 * @param detail the figures behind it, for the operator on the phone
 */
public record Hold(HoldReason reason, String detail) {

    /** A reason and its figures. */
    public Hold {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
    }

    /** Such as {@code gust: 50 mph against a limit of 45 mph}. */
    @Override
    public String toString() {
        return reason.value() + ": " + detail;
    }
}
