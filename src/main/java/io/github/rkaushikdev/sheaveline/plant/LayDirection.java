package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** Which way the strands wind, which decides how wear shows up. */
public enum LayDirection implements Valued {
    /** Right hand regular lay. */
    RIGHT_REGULAR("right regular lay"),
    /** Left hand regular lay. */
    LEFT_REGULAR("left regular lay"),
    /** Right hand lang lay. */
    RIGHT_LANG("right lang lay"),
    /** Left hand lang lay. */
    LEFT_LANG("left lang lay");

    private final String value;

    LayDirection(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether this is a lang lay, which wears flatter across the crowns and hides breaks better.
     *
     * @return true for either lang lay
     */
    public boolean isLang() {
        return this == RIGHT_LANG || this == LEFT_LANG;
    }
}
