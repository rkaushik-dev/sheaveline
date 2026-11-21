package io.github.rkaushikdev.sheaveline.reports;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A run of rows under a run of headings.
 *
 * <p>Columns are separated by a {@link Forms#GUTTER} of spaces. A cell too wide for its column is
 * cut short rather than wrapped, because a table row that becomes two rows stops lining up with the
 * row above it, and the whole point of the form is that the eye can run down a column.
 *
 * @param columns the columns, left to right
 * @param rows the rows, each holding one value a column
 */
public record Table(List<Column> columns, List<List<Object>> rows) {

    private static final String GAP = " ".repeat(Forms.GUTTER);

    /**
     * A table that fits the page.
     *
     * @throws ReportError when there are no columns, the columns are wider than the page, or a row
     *     holds more or fewer values than there are columns
     */
    public Table {
        columns = List.copyOf(columns);
        rows = rows.stream().<List<Object>>map(List::copyOf).toList();
        if (columns.isEmpty()) {
            throw new ReportError("a table has columns");
        }
        int width = widthOf(columns);
        if (width > Forms.PAGE_WIDTH) {
            throw new ReportError("a table " + width + " wide does not fit the page");
        }
        for (List<Object> row : rows) {
            if (row.size() != columns.size()) {
                throw new ReportError("a row of " + row.size() + " does not fit " + columns.size() + " columns");
            }
        }
    }

    /**
     * A table with no rows yet.
     *
     * @param columns the columns, left to right
     * @return the table
     * @throws ReportError when the columns will not print
     */
    public static Table of(List<Column> columns) {
        return new Table(columns, List.of());
    }

    /**
     * A table of rows.
     *
     * @param columns the columns, left to right
     * @param rows the rows, each holding one value a column
     * @return the table
     * @throws ReportError when the columns or a row will not print
     */
    public static Table of(List<Column> columns, Collection<? extends List<?>> rows) {
        return new Table(columns, rows.stream().<List<Object>>map(List::copyOf).toList());
    }

    /**
     * How wide the table prints: its columns and the gutters between them.
     *
     * @return the width in characters
     */
    public int width() {
        return widthOf(columns);
    }

    /**
     * The headings, each placed in its column the way the column places its values.
     *
     * @return the heading line
     */
    public String headingLine() {
        return columns.stream()
                .map(column -> column.align().place(column.heading(), column.width()))
                .collect(Collectors.joining(GAP));
    }

    /**
     * A dashed rule under every column, broken at the gutters.
     *
     * @return the rule
     */
    public String rule() {
        return rule("-");
    }

    /**
     * A rule under every column, broken at the gutters.
     *
     * @param character what the rule is drawn with
     * @return the rule
     */
    public String rule(String character) {
        return columns.stream().map(column -> character.repeat(column.width())).collect(Collectors.joining(GAP));
    }

    /**
     * The table as printed: the headings, the rule, then a line a row.
     *
     * @return the lines, every one exactly {@link #width} wide
     */
    public List<String> lines() {
        List<String> out = new ArrayList<>(rows.size() + 2);
        out.add(headingLine());
        out.add(rule());
        for (List<Object> row : rows) {
            out.add(IntStream.range(0, columns.size())
                    .mapToObj(index -> columns.get(index).cell(row.get(index)))
                    .collect(Collectors.joining(GAP)));
        }
        return List.copyOf(out);
    }

    /**
     * The same table with one more row at the bottom.
     *
     * @param row the row, one value a column
     * @return the new table
     * @throws ReportError when the row does not fit the columns
     */
    public Table withRow(List<?> row) {
        List<List<?>> grown = new ArrayList<>(rows);
        grown.add(row);
        return of(columns, grown);
    }

    private static int widthOf(List<Column> columns) {
        return columns.stream().mapToInt(Column::width).sum() + Forms.GUTTER * (columns.size() - 1);
    }
}
