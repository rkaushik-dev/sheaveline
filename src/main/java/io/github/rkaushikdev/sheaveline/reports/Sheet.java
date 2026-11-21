package io.github.rkaushikdev.sheaveline.reports;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One export: its columns, and the comma separated text its rows are written as.
 *
 * <p>The text is what a spreadsheet loads without being told anything. Cells are separated by
 * commas and every row, the heading row included, ends with a single line feed. A cell holding a
 * comma, a double quote, a carriage return or a line feed is put in double quotes, with each double
 * quote inside it doubled; every other cell is written as it is. A row of one empty cell is written
 * as a pair of double quotes, so that it is not read back as no row at all.
 *
 * @param name what the sheet is asked for by
 * @param columns the column headings, in order
 */
public record Sheet(String name, List<String> columns) {

    private static final String LINE_END = "\n";

    /** A sheet. */
    public Sheet {
        Objects.requireNonNull(name, "name");
        columns = List.copyOf(columns);
    }

    /**
     * Write the heading row and then every row, straight to the destination as they come.
     *
     * <p>Rows are written one at a time, so a refused row leaves the rows before it already
     * written.
     *
     * @param out where the text goes
     * @param rows the rows, one value a column; an empty {@link Optional} is an empty cell
     * @return how many rows were written, not counting the heading
     * @throws IllegalArgumentException when a row holds more or fewer values than there are columns
     * @throws IOException when the destination cannot be written
     */
    public int write(Appendable out, Iterable<? extends List<?>> rows) throws IOException {
        writeRow(out, columns);
        int written = 0;
        for (List<?> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException(name + " has " + columns.size() + " columns and got " + row.size());
            }
            writeRow(out, row);
            written += 1;
        }
        return written;
    }

    private static void writeRow(Appendable out, List<?> row) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                line.append(',');
            }
            line.append(quoted(Text.of(row.get(index))));
        }
        if (row.size() == 1 && line.isEmpty()) {
            line.append("\"\"");
        }
        out.append(line).append(LINE_END);
    }

    private static String quoted(String cell) {
        boolean needed =
                cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\r') >= 0 || cell.indexOf('\n') >= 0;
        return needed ? '"' + cell.replace("\"", "\"\"") + '"' : cell;
    }
}
