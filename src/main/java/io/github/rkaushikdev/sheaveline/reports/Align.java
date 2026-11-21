package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** Where a column puts its text when the text is narrower than the column. */
public enum Align implements Valued {
    /** Against the left edge, which is how words are printed. */
    LEFT("left"),
    /** Against the right edge, which is how numbers are printed, so their digits line up. */
    RIGHT("right"),
    /** In the middle. */
    CENTRE("centre");

    private final String value;

    Align(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * The text padded out to the width with spaces on the side this alignment leaves open. Text
     * already as wide as the width comes back as it is.
     *
     * @param text the text
     * @param width the columns to fill
     * @return the placed text
     */
    public String place(String text, int width) {
        return switch (this) {
            case LEFT -> Text.padRight(text, width);
            case RIGHT -> Text.padLeft(text, width);
            case CENTRE -> Text.centre(text, width);
        };
    }
}
