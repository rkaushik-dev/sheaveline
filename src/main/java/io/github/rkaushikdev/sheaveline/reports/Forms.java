package io.github.rkaushikdev.sheaveline.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed width printing, because the board's forms are eighty columns wide.
 *
 * <p>Everything the department sends out is printed on a dot matrix in the workshop or pasted into a
 * form the board has not changed since the nineteen eighties, and both of those want eighty columns
 * and no escape codes. So a report is a list of strings, each one no wider than the page, and nothing
 * here knows what a terminal is.
 *
 * <p>A column that will not fit is truncated rather than wrapped inside a {@link Table}, because a
 * table row that becomes two rows stops lining up with the row above it and the whole point of the
 * form is that the eye can run down a column. Prose is the only thing that wraps.
 */
public final class Forms {

    /** How wide a page is. */
    public static final int PAGE_WIDTH = 80;

    /** The spaces between two columns of a table. */
    public static final int GUTTER = 1;

    /** How wide the label of a {@link #field} is padded to unless asked otherwise. */
    public static final int LABEL_WIDTH = 24;

    private Forms() {}

    /**
     * Prose broken to the page.
     *
     * @param text the prose
     * @return the lines
     * @see #wrap(String, int)
     */
    public static List<String> wrap(String text) {
        return wrap(text, PAGE_WIDTH);
    }

    /**
     * Prose broken at white space, which is the only place a form may break it.
     *
     * <p>Runs of white space collapse to one space. A word longer than the line is cut to the line,
     * and the rest of it is dropped.
     *
     * @param text the prose
     * @param width the longest line
     * @return the lines, one empty line when there are no words at all
     * @throws ReportError when the width is under one
     */
    public static List<String> wrap(String text, int width) {
        if (width < 1) {
            throw new ReportError("nothing wraps to nothing");
        }
        List<String> words = Text.words(text);
        if (words.isEmpty()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        String current = Text.head(words.get(0), width);
        for (String word : words.subList(1, words.size())) {
            String piece = Text.head(word, width);
            if (Text.length(current) + 1 + Text.length(piece) <= width) {
                current = current + " " + piece;
            } else {
                lines.add(current);
                current = piece;
            }
        }
        lines.add(current);
        return List.copyOf(lines);
    }

    /**
     * A double rule across the page.
     *
     * @return eighty equals signs
     */
    public static String rule() {
        return rule("=");
    }

    /**
     * A rule across the page.
     *
     * @param character what the rule is drawn with
     * @return the rule
     */
    public static String rule(String character) {
        return rule(character, PAGE_WIDTH);
    }

    /**
     * A rule of a given width.
     *
     * @param character what the rule is drawn with
     * @param width how many times it is repeated; nothing is drawn for a width under one
     * @return the rule
     */
    public static String rule(String character, int width) {
        return character.repeat(Math.max(0, width));
    }

    /**
     * Text centred on the page.
     *
     * @param text the text
     * @return the centred text
     * @see #centre(String, int)
     */
    public static String centre(String text) {
        return centre(text, PAGE_WIDTH);
    }

    /**
     * Text centred in a width, with nothing printed after it.
     *
     * <p>When the spare columns do not halve, the odd one goes on the left in an odd width and on
     * the right in an even one. The trailing white space is then dropped, so the line ends where its
     * last character does.
     *
     * @param text the text
     * @param width the width to centre in
     * @return the spaces before the text and the text
     */
    public static String centre(String text, int width) {
        return Text.stripTrailing(Text.centre(text, width));
    }

    /**
     * One labelled value, with the label padded so a column of them lines up.
     *
     * @param label the label
     * @param value the value
     * @return the label padded to {@link #LABEL_WIDTH}, a space, and the value
     */
    public static String field(String label, Object value) {
        return field(label, value, LABEL_WIDTH);
    }

    /**
     * One labelled value, with the label padded so a column of them lines up.
     *
     * @param label the label
     * @param value the value; an empty {@link java.util.Optional} prints as nothing
     * @param width how wide the label is padded to
     * @return the padded label, a space, and the value
     */
    public static String field(String label, Object value, int width) {
        return Text.padRight(label, width) + " " + Text.of(value);
    }
}
