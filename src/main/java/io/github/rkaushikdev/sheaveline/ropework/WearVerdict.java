package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What the rope file says about a rope after a survey. */
public enum WearVerdict implements Valued {
    /** Nothing against it. */
    SERVICEABLE("serviceable"),
    /** It may run, and somebody goes back to look at it. */
    MONITOR("monitor"),
    /** It comes off. */
    CONDEMNED("condemned");

    private final String value;

    WearVerdict(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the rope may carry passengers under this verdict.
     *
     * @return false only when condemned
     */
    public boolean mayRun() {
        return this != CONDEMNED;
    }

    /**
     * Whether the verdict sends somebody back to look again.
     *
     * @return true when on watch
     */
    public boolean needsAReturnVisit() {
        return this == MONITOR;
    }
}
