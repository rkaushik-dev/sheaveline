package io.github.rkaushikdev.sheaveline.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * What a database's schema holds, read back from its information schema, so two schemas built two
 * ways can be compared table by table.
 *
 * <p>Every name comes back in lower case. An index the database made for itself to back a key or a
 * constraint is left out of the indexes, because its name is the database's choice rather than the
 * schema's; the constraint it backs is compared on its own.
 */
final class SchemaCatalog {

    private static final String HISTORY_TABLE = "flyway_schema_history";

    private final DataSource source;

    private SchemaCatalog(DataSource source) {
        this.source = source;
    }

    /**
     * The catalog of one database.
     *
     * @param database the database
     * @return its catalog
     */
    static SchemaCatalog of(Database database) {
        return new SchemaCatalog(database.dataSource());
    }

    /** One column as declared: its type, its size, and whether it may be empty. */
    record Column(
            String type,
            String length,
            String precision,
            String scale,
            String timePrecision,
            boolean nullable,
            boolean identity) {}

    /** A named constraint or index and its columns, in key order. */
    record Keyed(String name, List<String> columns) {}

    /** A foreign key: its name, its columns, and the table it points at. */
    record Reference(String name, List<String> columns, String referredTable) {}

    /**
     * The tables, the migrations' own bookkeeping aside.
     *
     * @return the table names
     */
    SortedSet<String> tables() {
        SortedSet<String> names = new TreeSet<>(column("SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'"));
        names.remove(HISTORY_TABLE);
        return names;
    }

    /**
     * A table's columns, by name.
     *
     * @param table the table
     * @return the columns
     */
    Map<String, Column> columns(String table) {
        Map<String, Column> found = new TreeMap<>();
        for (List<String> row : rows(
                "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale,"
                        + " datetime_precision, is_nullable, is_identity FROM information_schema.columns"
                        + " WHERE table_schema = 'PUBLIC' AND table_name = ?",
                table)) {
            found.put(
                    row.get(0),
                    new Column(
                            row.get(1),
                            row.get(2),
                            row.get(3),
                            row.get(4),
                            row.get(5),
                            "yes".equals(row.get(6)),
                            "yes".equals(row.get(7))));
        }
        return found;
    }

    /**
     * A table's primary key columns, in key order.
     *
     * @param table the table
     * @return the columns, empty when it has no key
     */
    List<String> primaryKey(String table) {
        return keyed(table, "PRIMARY KEY").stream()
                .findFirst()
                .map(Keyed::columns)
                .orElse(List.of());
    }

    /**
     * A table's unique constraints.
     *
     * @param table the table
     * @return the constraints
     */
    Set<Keyed> uniques(String table) {
        return Set.copyOf(keyed(table, "UNIQUE"));
    }

    /**
     * A table's foreign keys.
     *
     * @param table the table
     * @return the keys
     */
    Set<Reference> foreignKeys(String table) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        Map<String, String> referred = new LinkedHashMap<>();
        for (List<String> row : rows(
                "SELECT c.constraint_name, k.column_name, u.table_name"
                        + " FROM information_schema.table_constraints c"
                        + " JOIN information_schema.key_column_usage k"
                        + " ON k.constraint_schema = c.constraint_schema AND k.constraint_name = c.constraint_name"
                        + " JOIN information_schema.referential_constraints r"
                        + " ON r.constraint_schema = c.constraint_schema AND r.constraint_name = c.constraint_name"
                        + " JOIN information_schema.table_constraints u"
                        + " ON u.constraint_schema = r.unique_constraint_schema"
                        + " AND u.constraint_name = r.unique_constraint_name"
                        + " WHERE c.table_schema = 'PUBLIC' AND c.table_name = ? AND c.constraint_type = 'FOREIGN KEY'"
                        + " ORDER BY c.constraint_name, k.ordinal_position",
                table)) {
            columns.computeIfAbsent(row.get(0), name -> new ArrayList<>()).add(row.get(1));
            referred.put(row.get(0), row.get(2));
        }
        return columns.entrySet().stream()
                .map(key -> new Reference(key.getKey(), List.copyOf(key.getValue()), referred.get(key.getKey())))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * A table's indexes that the schema declares, leaving out those the database made to back a key.
     *
     * @param table the table
     * @return the indexes
     */
    Set<Keyed> indexes(String table) {
        return Set.copyOf(grouped(rows(
                "SELECT i.index_name, c.column_name FROM information_schema.indexes i"
                        + " JOIN information_schema.index_columns c"
                        + " ON c.index_schema = i.index_schema AND c.index_name = i.index_name"
                        + " AND c.table_name = i.table_name"
                        + " WHERE i.table_schema = 'PUBLIC' AND i.table_name = ? AND i.is_generated = FALSE"
                        + " ORDER BY i.index_name, c.ordinal_position",
                table)));
    }

    /**
     * Every index the schema declares, on every table.
     *
     * @return the indexes
     */
    Set<Keyed> indexes() {
        return tables().stream().flatMap(table -> indexes(table).stream()).collect(Collectors.toUnmodifiableSet());
    }

    private List<Keyed> keyed(String table, String type) {
        return grouped(rows(
                "SELECT c.constraint_name, k.column_name FROM information_schema.table_constraints c"
                        + " JOIN information_schema.key_column_usage k"
                        + " ON k.constraint_schema = c.constraint_schema AND k.constraint_name = c.constraint_name"
                        + " WHERE c.table_schema = 'PUBLIC' AND c.table_name = ? AND c.constraint_type = '"
                        + type
                        + "' ORDER BY c.constraint_name, k.ordinal_position",
                table));
    }

    private static List<Keyed> grouped(List<List<String>> rows) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (List<String> row : rows) {
            columns.computeIfAbsent(row.get(0), name -> new ArrayList<>()).add(row.get(1));
        }
        return columns.entrySet().stream()
                .map(key -> new Keyed(key.getKey(), List.copyOf(key.getValue())))
                .toList();
    }

    private List<String> column(String sql) {
        return rows(sql, null).stream().map(row -> row.get(0)).toList();
    }

    private List<List<String>> rows(String sql, String table) {
        List<List<String>> found = new ArrayList<>();
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (table != null) {
                statement.setString(1, table.toUpperCase(Locale.ROOT));
            }
            try (ResultSet rows = statement.executeQuery()) {
                int width = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    List<String> row = new ArrayList<>();
                    for (int at = 1; at <= width; at++) {
                        String value = rows.getString(at);
                        row.add(value == null ? "" : value.toLowerCase(Locale.ROOT));
                    }
                    found.add(row);
                }
            }
        } catch (SQLException trouble) {
            throw new IllegalStateException(sql, trouble);
        }
        return found;
    }
}
