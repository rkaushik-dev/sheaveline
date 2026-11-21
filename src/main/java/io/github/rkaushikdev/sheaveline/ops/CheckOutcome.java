package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What the morning walk found at one line of the sheet. */
public enum CheckOutcome implements Valued {
    /** The line was checked and is fine. */
    PASS("pass"),
    /** The line was checked and is not fine. */
    FAIL("fail"),
    /** The line was put off to the work order list with a note. */
    DEFERRED("deferred");

    private final String value;

    CheckOutcome(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the line needs nothing more doing.
     *
     * @return true only for a pass
     */
    public boolean isSatisfied() {
        return this == PASS;
    }
}
