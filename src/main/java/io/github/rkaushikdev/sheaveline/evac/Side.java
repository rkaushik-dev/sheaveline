package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** Which way a carrier is travelling, which decides which side of the line it hangs on. */
public enum Side implements Valued {
    /** Climbing toward the top terminal. */
    UP("up"),
    /** Coming back down. */
    DOWN("down");

    private final String value;

    Side(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
