package io.github.rkaushikdev.sheaveline.reports;

import java.util.Objects;
import java.util.Optional;

/**
 * One column of a table, with the width it is printed at.
 *
 * @param heading the words printed above it
 * @param width how many characters wide it is
 * @param align where its text sits
 */
public record Column(String heading, int width, Align align) {

    private static final String ELLIPSIS = "…";

    /**
     * A column its heading fits in.
     *
     * @throws ReportError when the width is under one or the heading is wider than the column
     */
    public Column {
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(align, "align");
        if (width < 1) {
            throw new ReportError("column " + heading + " has no width");
        }
        if (Text.length(heading) > width) {
            throw new ReportError("the heading " + heading + " does not fit in " + width);
        }
    }

    /**
     * A left aligned column.
     *
     * @param heading the words printed above it
     * @param width how many characters wide it is
     */
    public Column(String heading, int width) {
        this(heading, width, Align.LEFT);
    }

    /**
     * A value printed to exactly this column's width.
     *
     * <p>A value too wide is cut short and ends in an ellipsis, rather than wrapped, so the row stays
     * one line and keeps lining up with the rows around it. A column one wide keeps only the first
     * character.
     *
     * @param value what goes in the cell; an empty {@link Optional} prints as an empty cell
     * @return the cell, exactly {@link #width} characters wide
     */
    public String cell(Object value) {
        String text = Text.of(value);
        if (Text.length(text) > width) {
            text = width > 1 ? Text.head(text, width - 1) + ELLIPSIS : Text.head(text, 1);
        }
        return align.place(text, width);
    }
}
