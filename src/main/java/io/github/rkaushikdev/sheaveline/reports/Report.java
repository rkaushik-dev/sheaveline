package io.github.rkaushikdev.sheaveline.reports;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A page being built, one line at a time.
 *
 * <p>Every method that adds to the page hands the page back, so calls chain. No line is ever wider
 * than the page: a line that would be is refused rather than wrapped or cut, because the caller is
 * the only one who knows which of those it should have been.
 */
public final class Report {

    private static final int NARROWEST = 20;

    private final int width;
    private final List<String> lines = new ArrayList<>();

    /** A page eighty columns wide. */
    public Report() {
        this(Forms.PAGE_WIDTH);
    }

    /**
     * A page of a given width.
     *
     * @param width how wide the page is
     * @throws ReportError when it is narrower than twenty columns
     */
    public Report(int width) {
        if (width < NARROWEST) {
            throw new ReportError("a page narrower than twenty columns is not a page");
        }
        this.width = width;
    }

    /**
     * How wide the page is.
     *
     * @return the width in characters
     */
    public int width() {
        return width;
    }

    /**
     * One line, exactly as given.
     *
     * @param text the line
     * @return this page
     * @throws ReportError when the line is wider than the page
     */
    public Report line(String text) {
        int length = Text.length(text);
        if (length > width) {
            throw new ReportError("a line of " + length + " does not fit " + width + " columns");
        }
        lines.add(text);
        return this;
    }

    /**
     * An empty line.
     *
     * @return this page
     */
    public Report blank() {
        return line("");
    }

    /**
     * The page's title: the text in capitals, centred, with a double rule under it.
     *
     * @param text the title
     * @return this page
     */
    public Report heading(String text) {
        line(Forms.centre(text.toUpperCase(Locale.ROOT), width));
        return line(Forms.rule("=", width));
    }

    /**
     * A section: an empty line, the heading, and a rule as long as the heading and no longer.
     *
     * @param text the section heading
     * @return this page
     */
    public Report section(String text) {
        blank();
        line(text);
        return line(Forms.rule("-", Text.length(text)));
    }

    /**
     * One labelled value, the label padded to {@link Forms#LABEL_WIDTH}.
     *
     * @param label the label
     * @param value the value
     * @return this page
     */
    public Report field(String label, Object value) {
        return field(label, value, Forms.LABEL_WIDTH);
    }

    /**
     * One labelled value.
     *
     * @param label the label
     * @param value the value
     * @param labelWidth how wide the label is padded to
     * @return this page
     */
    public Report field(String label, Object value, int labelWidth) {
        return line(Forms.field(label, value, labelWidth));
    }

    /**
     * Prose, wrapped to the page.
     *
     * @param text the prose
     * @return this page
     */
    public Report prose(String text) {
        Forms.wrap(text, width).forEach(this::line);
        return this;
    }

    /**
     * A table, headings and rule and rows.
     *
     * @param table the table
     * @return this page
     * @throws ReportError when the table is wider than this page
     */
    public Report table(Table table) {
        table.lines().forEach(this::line);
        return this;
    }

    /**
     * The page so far.
     *
     * @return a copy of the lines, in order
     */
    public List<String> lines() {
        return List.copyOf(lines);
    }

    /**
     * The page as one piece of text, every line ended by a newline.
     *
     * @return the text
     */
    public String text() {
        return String.join("\n", lines) + "\n";
    }

    /**
     * How many lines the page has.
     *
     * @return the line count
     */
    public int size() {
        return lines.size();
    }
}
