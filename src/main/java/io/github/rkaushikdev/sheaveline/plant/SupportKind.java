package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What holds the rope up at one end of a span. */
public enum SupportKind implements Valued {
    /** A terminal bullwheel. */
    TERMINAL("terminal"),
    /** A tower crossarm. */
    TOWER("tower");

    private final String value;

    SupportKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
