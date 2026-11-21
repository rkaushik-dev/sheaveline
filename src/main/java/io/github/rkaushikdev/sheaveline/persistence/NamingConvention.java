package io.github.rkaushikdev.sheaveline.persistence;

/**
 * How every constraint and index in the schema is named.
 *
 * <p>The rule is not decoration. A migration drops a constraint by its name, and each database
 * names an anonymous constraint its own way, so without one rule a migration that works on the
 * office server fails on the laptop the mechanic carries. The migrations and the entities both spell
 * their names out by it, before any table exists.
 *
 * <p>A unique constraint or an index declared with a name of its own, such as {@code
 * one_label_a_lift}, keeps that name. A check constraint's own name goes after its table's, so
 * {@code speed_positive} on {@code lifts} is {@code ck_lifts_speed_positive}.
 */
public final class NamingConvention {

    private NamingConvention() {}

    /**
     * The primary key of a table.
     *
     * @param table the table
     * @return {@code pk_<table>}
     */
    public static String primaryKey(String table) {
        return "pk_" + table;
    }

    /**
     * A foreign key, named for the column that holds it and the table it points at.
     *
     * @param table the table holding the key
     * @param column its first column
     * @param referredTable the table it points at
     * @return {@code fk_<table>_<column>_<referred table>}
     */
    public static String foreignKey(String table, String column, String referredTable) {
        return "fk_" + table + "_" + column + "_" + referredTable;
    }

    /**
     * A check constraint.
     *
     * @param table the table
     * @param name the constraint's own name
     * @return {@code ck_<table>_<name>}
     */
    public static String check(String table, String name) {
        return "ck_" + table + "_" + name;
    }

    /**
     * A unique constraint declared without a name of its own.
     *
     * @param table the table
     * @param column its first column
     * @return {@code uq_<table>_<column>}
     */
    public static String unique(String table, String column) {
        return "uq_" + table + "_" + column;
    }

    /**
     * An index declared without a name of its own.
     *
     * @param table the table
     * @param column its first column
     * @return {@code ix_<table>_<column>}
     */
    public static String index(String table, String column) {
        return "ix_" + table + "_" + column;
    }
}
