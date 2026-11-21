package io.github.rkaushikdev.sheaveline.ops;

import java.util.Objects;

/**
 * What the morning walk recorded against one line of the sheet.
 *
 * @param item the line
 * @param outcome what was found
 * @param by who walked it
 * @param note what was wrong, or why it waits; empty for a pass
 */
public record CheckResult(CheckItem item, CheckOutcome outcome, String by, String note) {

    /**
     * A signed result.
     *
     * @throws OperationsError when it is unsigned, when anything but a pass carries no note, or when
     *     a critical line is deferred
     */
    public CheckResult {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(note, "note");
        if (by.strip().isEmpty()) {
            throw new OperationsError(item.code() + " is signed by whoever walked it");
        }
        if (outcome != CheckOutcome.PASS && note.strip().isEmpty()) {
            throw new OperationsError(item.code() + " did not pass and carries no note");
        }
        if (outcome == CheckOutcome.DEFERRED && item.critical()) {
            throw new OperationsError(item.code() + " is critical and cannot be deferred");
        }
    }

    /**
     * A result with no note, which only a pass may be.
     *
     * @param item the line
     * @param outcome what was found
     * @param by who walked it
     */
    public CheckResult(CheckItem item, CheckOutcome outcome, String by) {
        this(item, outcome, by, "");
    }

    /** Such as {@code B1 pass} or {@code E2 fail (blown over)}. */
    @Override
    public String toString() {
        String tail = note.isEmpty() ? "" : " (" + note + ")";
        return item.code() + " " + outcome.value() + tail;
    }
}
