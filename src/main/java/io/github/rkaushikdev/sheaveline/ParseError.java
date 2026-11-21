package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.util.Quote;

/** Text did not read as the measure it was supposed to be. */
public class ParseError extends MeasureError {

    private final String text;
    private final String wanted;

    /**
     * Text that would not read.
     *
     * @param text what was given, exactly
     * @param wanted what it should have been, as a phrase such as {@code "a length"}
     */
    public ParseError(String text, String wanted) {
        super("cannot read " + Quote.of(text) + " as " + wanted);
        this.text = text;
        this.wanted = wanted;
    }

    /**
     * What was given.
     *
     * @return the text exactly as it arrived
     */
    public String text() {
        return text;
    }

    /**
     * What the text should have read as.
     *
     * @return the phrase naming the wanted measure
     */
    public String wanted() {
        return wanted;
    }
}
