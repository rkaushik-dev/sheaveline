package io.github.rkaushikdev.sheaveline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.TowerRef;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import jakarta.persistence.AttributeConverter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The column types, run against a scratch table of their own: each one is the converter and the
 * plain column it writes, so a value goes in through the converter, sits in the column as the
 * integer or the text it is, and comes back out through the converter.
 */
class DbTypesTest {

    private static final Scratch<Length, Integer> LENGTH =
            new Scratch<>("length", "INTEGER", new ColumnTypes.LengthColumn(), Integer.class);
    private static final Scratch<Load, Integer> LOAD =
            new Scratch<>("load", "INTEGER", new ColumnTypes.LoadColumn(), Integer.class);
    private static final Scratch<LoadPerFoot, Integer> PER_FOOT =
            new Scratch<>("per_foot", "INTEGER", new ColumnTypes.LoadPerFootColumn(), Integer.class);
    private static final Scratch<Speed, Integer> SPEED =
            new Scratch<>("speed", "INTEGER", new ColumnTypes.SpeedColumn(), Integer.class);
    private static final Scratch<Duration, Integer> ELAPSED =
            new Scratch<>("elapsed", "INTEGER", new ColumnTypes.DurationColumn(), Integer.class);
    private static final Scratch<Angle, Integer> ANGLE =
            new Scratch<>("angle", "INTEGER", new ColumnTypes.AngleColumn(), Integer.class);
    private static final Scratch<Bearing, Integer> BEARING =
            new Scratch<>("bearing", "INTEGER", new ColumnTypes.BearingColumn(), Integer.class);
    private static final Scratch<Temperature, Integer> TEMPERATURE =
            new Scratch<>("temperature", "INTEGER", new ColumnTypes.TemperatureColumn(), Integer.class);
    private static final Scratch<Torque, Integer> TORQUE =
            new Scratch<>("torque", "INTEGER", new ColumnTypes.TorqueColumn(), Integer.class);
    private static final Scratch<TimeOfDay, Integer> CLOCK =
            new Scratch<>("clock", "INTEGER", new ColumnTypes.TimeOfDayColumn(), Integer.class);
    private static final Scratch<BigDecimal, Integer> RATIO =
            new Scratch<>("ratio", "INTEGER", new ColumnTypes.DecimalColumn(), Integer.class);
    private static final Scratch<LiftCode, String> LIFT =
            new Scratch<>("lift", "VARCHAR(8)", new ColumnTypes.LiftCodeColumn(), String.class);
    private static final Scratch<TowerRef, String> TOWER =
            new Scratch<>("tower", "VARCHAR(16)", new ColumnTypes.TowerRefColumn(), String.class);
    private static final Scratch<RopeSerial, String> ROPE =
            new Scratch<>("rope", "VARCHAR(20)", new ColumnTypes.RopeSerialColumn(), String.class);
    private static final Scratch<FilingNumber, String> FILING =
            new Scratch<>("filing", "VARCHAR(16)", new ColumnTypes.FilingNumberColumn(), String.class);

    private static final List<Scratch<?, ?>> COLUMNS = List.of(
            LENGTH,
            LOAD,
            PER_FOOT,
            SPEED,
            ELAPSED,
            ANGLE,
            BEARING,
            TEMPERATURE,
            TORQUE,
            CLOCK,
            RATIO,
            LIFT,
            TOWER,
            ROPE,
            FILING);

    private Database database;

    @BeforeEach
    void createTheScratchTable() {
        database = TestDatabase.fresh();
        String columns = COLUMNS.stream()
                .map(column -> column.name() + " " + column.sqlType())
                .collect(Collectors.joining(", "));
        execute("CREATE TABLE scratch_measures (id INTEGER NOT NULL, " + columns
                + ", note VARCHAR(20), CONSTRAINT pk_scratch_measures PRIMARY KEY (id))");
    }

    @AfterEach
    void dropTheScratchTable() {
        execute("DROP TABLE scratch_measures");
    }

    @Test
    void everyMeasureComesBackAsTheTypeItWentInAs() {
        Stored row = roundTrip(new Insert(1)
                .with(LENGTH, Length.ofFeet(4800))
                .with(LOAD, Load.ofPounds(34500))
                .with(PER_FOOT, LoadPerFoot.ofPoundsPerFoot("3.12"))
                .with(SPEED, Speed.ofFeetPerMinute(1000))
                .with(ELAPSED, Duration.ofMinutes(8))
                .with(ANGLE, Angle.ofDegrees("1.75"))
                .with(BEARING, Bearing.ofDegrees(72))
                .with(TEMPERATURE, Temperature.ofDegreesF(-12))
                .with(TORQUE, Torque.ofPoundFeet(900))
                .with(CLOCK, TimeOfDay.at(15, 45))
                .with(RATIO, new BigDecimal("1.125"))
                .with(LIFT, new LiftCode("JP"))
                .with(TOWER, TowerRef.parse("JP-T4"))
                .with(ROPE, RopeSerial.parse("FAT-2019-04412"))
                .with(FILING, new FilingNumber("UT", 2025, 147)));
        assertThat(row.get(LENGTH)).isEqualTo(Length.ofFeet(4800));
        assertThat(row.get(LOAD)).isEqualTo(Load.ofPounds(34500));
        assertThat(row.get(PER_FOOT)).isEqualTo(LoadPerFoot.ofPoundsPerFoot("3.12"));
        assertThat(row.get(SPEED)).isEqualTo(Speed.ofFeetPerMinute(1000));
        assertThat(row.get(ELAPSED)).isEqualTo(Duration.ofMinutes(8));
        assertThat(row.get(ANGLE)).isEqualTo(Angle.ofDegrees("1.75"));
        assertThat(row.get(BEARING)).isEqualTo(Bearing.ofDegrees(72));
        assertThat(row.get(TEMPERATURE)).isEqualTo(Temperature.ofDegreesF(-12));
        assertThat(row.get(TORQUE)).isEqualTo(Torque.ofPoundFeet(900));
        assertThat(row.get(CLOCK)).isEqualTo(TimeOfDay.at(15, 45));
        assertThat(row.get(RATIO)).isEqualByComparingTo("1.125");
        assertThat(row.get(LIFT)).isEqualTo(new LiftCode("JP"));
        assertThat(row.get(TOWER)).isEqualTo(TowerRef.parse("JP-T4"));
        assertThat(row.get(ROPE)).isEqualTo(RopeSerial.parse("FAT-2019-04412"));
        assertThat(row.get(FILING)).isEqualTo(new FilingNumber("UT", 2025, 147));
    }

    @Test
    void nothingStoredComesBackAsNothing() {
        Stored row = roundTrip(new Insert(1).note("empty"));
        assertThat(row.raw()).containsEntry("note", "empty");
        for (Scratch<?, ?> column : COLUMNS) {
            assertThat(row.get(column)).as(column.name()).isNull();
        }
    }

    @Test
    void aLengthIsStoredAsTheIntegerItAlreadyIs() {
        Stored row = roundTrip(new Insert(2).with(LENGTH, Length.ofFeet(1)));
        assertThat(row.raw()).containsEntry("length", 12000);
        assertThat(row.get(LENGTH)).isEqualTo(Length.ofMils(12000));
    }

    @Test
    void lengthsCompareInTheDatabaseTheWayTheyCompareInMemory() throws SQLException {
        int id = 10;
        for (long feet : new long[] {100, 4800, 250}) {
            new Insert(id++).with(LENGTH, Length.ofFeet(feet)).into(database);
        }
        List<Length> found = new ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT length FROM scratch_measures WHERE length > ? ORDER BY length")) {
            query.setObject(1, LENGTH.converter().convertToDatabaseColumn(Length.ofFeet(200)));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    found.add(LENGTH.read(rows));
                }
            }
        }
        assertThat(found).containsExactly(Length.ofFeet(250), Length.ofFeet(4800));
    }

    @Test
    void aNegativeMeasureSurvivesTheTrip() {
        Stored row = roundTrip(
                new Insert(1).with(TEMPERATURE, Temperature.ofDegreesF(-24)).with(ANGLE, Angle.ofDegrees("-1.2")));
        assertThat(row.get(TEMPERATURE)).isEqualTo(Temperature.ofDegreesF(-24));
        assertThat(row.get(ANGLE)).isEqualTo(Angle.ofDegrees("-1.2"));
    }

    @Test
    void aBearingWrapsOnTheWayBackOut() {
        assertThat(roundTrip(new Insert(1).with(BEARING, Bearing.ofDegrees(370)))
                        .get(BEARING))
                .isEqualTo(Bearing.ofDegrees(10));
        execute("INSERT INTO scratch_measures (id, bearing) VALUES (2, 370000)");
        assertThat(read(2).get(BEARING)).isEqualTo(Bearing.ofDegrees(10));
    }

    @Test
    void theNamingConventionNamesEveryConstraintInTheSchema() {
        assertThat(NamingConvention.primaryKey("lifts")).isEqualTo("pk_lifts");
        assertThat(NamingConvention.foreignKey("towers", "lift_code", "lifts")).isEqualTo("fk_towers_lift_code_lifts");
        assertThat(NamingConvention.check("lifts", "speed_positive")).isEqualTo("ck_lifts_speed_positive");
        assertThat(NamingConvention.unique("lifts", "name")).isEqualTo("uq_lifts_name");
        assertThat(NamingConvention.index("holds", "started")).isEqualTo("ix_holds_started");

        List<List<String>> keys = query("SELECT table_name, constraint_name FROM information_schema.table_constraints"
                + " WHERE table_schema = 'PUBLIC' AND constraint_type = 'PRIMARY KEY'"
                + " AND table_name <> 'flyway_schema_history'");
        assertThat(keys).hasSizeGreaterThan(30);
        for (List<String> key : keys) {
            assertThat(key.get(1)).isEqualTo(NamingConvention.primaryKey(key.get(0)));
        }
        List<List<String>> references = query("SELECT k.table_name, k.column_name, u.table_name, r.constraint_name"
                + " FROM information_schema.referential_constraints r"
                + " JOIN information_schema.key_column_usage k ON k.constraint_name = r.constraint_name"
                + " JOIN information_schema.key_column_usage u ON u.constraint_name = r.unique_constraint_name"
                + " WHERE r.constraint_schema = 'PUBLIC'");
        assertThat(references).hasSizeGreaterThan(30);
        for (List<String> reference : references) {
            assertThat(reference.get(3))
                    .isEqualTo(NamingConvention.foreignKey(reference.get(0), reference.get(1), reference.get(2)));
        }
        List<List<String>> checks = query("SELECT table_name, constraint_name FROM information_schema.table_constraints"
                + " WHERE table_schema = 'PUBLIC' AND constraint_type = 'CHECK'");
        assertThat(checks).hasSizeGreaterThan(20);
        for (List<String> check : checks) {
            assertThat(check.get(1)).startsWith(NamingConvention.check(check.get(0), ""));
        }
    }

    @Test
    void theSchemaCanBeBuiltAndDropped() {
        try (Database made = Database.inMemory()) {
            made.createSchema();
            assertThat(made.existingTables()).containsAll(Database.tableNames());
            made.dropSchema();
            assertThat(made.existingTables()).isEmpty();
        }
        assertThat(Database.tableNames()).contains("filing_counters");
    }

    @Test
    void aScopeCommitsOnTheWayOut() {
        database.inSession(session -> session.entityManager()
                .createNativeQuery("INSERT INTO scratch_measures (id, note) VALUES (3, 'kept')")
                .executeUpdate());
        assertThat(read(3).raw()).containsEntry("note", "kept");
    }

    @Test
    void aScopeRollsBackOnAnythingRaised() {
        assertThatThrownBy(() -> database.inSession(session -> {
                    session.entityManager()
                            .createNativeQuery("INSERT INTO scratch_measures (id, note) VALUES (4, 'dropped')")
                            .executeUpdate();
                    throw new IllegalStateException("something went wrong halfway");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("something went wrong halfway");
        assertThat(find(4)).isEmpty();
    }

    @Test
    void theDatabaseEnforcesItsForeignKeys() {
        assertThatThrownBy(() -> run("INSERT INTO survey_shots (lift_code, station, elevation) VALUES ('ZZZ', 0, 0)"))
                .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                .hasMessageContaining("FK_SURVEY_SHOTS_LIFT_CODE_LIFTS");
        execute("INSERT INTO lifts (code, name, lift_class, bearing, design_speed, commissioned)"
                + " VALUES ('ZZZ', 'Planted', 'FIXED_CHAIR', 0, 1, DATE '2004-12-03')");
        execute("INSERT INTO survey_shots (lift_code, station, elevation) VALUES ('ZZZ', 0, 0)");
        assertThat(query("SELECT COUNT(*) FROM survey_shots")).containsExactly(List.of("1"));
    }

    @Test
    void aDecimalKeepsThreePlacesAndRoundsTheRest() {
        Stored row = roundTrip(new Insert(1).with(RATIO, new BigDecimal("0.8567")));
        assertThat(row.get(RATIO)).isEqualByComparingTo("0.857");
        assertThat(row.raw()).containsEntry("ratio", 857);
    }

    private Stored roundTrip(Insert insert) {
        try {
            insert.into(database);
        } catch (SQLException trouble) {
            throw new IllegalStateException(trouble);
        }
        return read(insert.id());
    }

    private Stored read(int id) {
        return find(id).orElseThrow();
    }

    private Optional<Stored> find(int id) {
        try (Connection connection = database.dataSource().getConnection();
                PreparedStatement query = connection.prepareStatement("SELECT * FROM scratch_measures WHERE id = ?")) {
            query.setInt(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                Map<String, Object> raw = new LinkedHashMap<>();
                int width = rows.getMetaData().getColumnCount();
                for (int column = 1; column <= width; column++) {
                    raw.put(rows.getMetaData().getColumnLabel(column).toLowerCase(Locale.ROOT), rows.getObject(column));
                }
                return Optional.of(new Stored(Collections.unmodifiableMap(raw)));
            }
        } catch (SQLException trouble) {
            throw new IllegalStateException(trouble);
        }
    }

    private List<List<String>> query(String sql) {
        List<List<String>> found = new ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            int width = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= width; column++) {
                    row.add(rows.getString(column).toLowerCase(Locale.ROOT));
                }
                found.add(row);
            }
        } catch (SQLException trouble) {
            throw new IllegalStateException(sql, trouble);
        }
        return found;
    }

    private void execute(String sql) {
        try {
            run(sql);
        } catch (SQLException trouble) {
            throw new IllegalStateException(sql, trouble);
        }
    }

    private void run(String sql) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * One scratch column: its name, its type in the database, and the converter that writes and
     * reads it.
     */
    private record Scratch<T, Y>(String name, String sqlType, AttributeConverter<T, Y> converter, Class<Y> stored) {

        T read(ResultSet rows) throws SQLException {
            return converter.convertToEntityAttribute(rows.getObject(name, stored));
        }
    }

    /** One scratch row as the database holds it, read back through the converters on request. */
    private record Stored(Map<String, Object> raw) {

        <T, Y> T get(Scratch<T, Y> column) {
            return column.converter().convertToEntityAttribute(column.stored().cast(raw.get(column.name())));
        }
    }

    /** One scratch row to write, each value put through its column's converter on the way. */
    private static final class Insert {

        private final int id;
        private final Map<String, Object> values = new LinkedHashMap<>();

        Insert(int id) {
            this.id = id;
            values.put("id", id);
        }

        int id() {
            return id;
        }

        <T> Insert with(Scratch<T, ?> column, T value) {
            values.put(column.name(), column.converter().convertToDatabaseColumn(value));
            return this;
        }

        Insert note(String note) {
            values.put("note", note);
            return this;
        }

        void into(Database database) throws SQLException {
            String names = String.join(", ", values.keySet());
            String marks = String.join(", ", Collections.nCopies(values.size(), "?"));
            try (Connection connection = database.dataSource().getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO scratch_measures (" + names + ") VALUES (" + marks + ")")) {
                int index = 1;
                for (Object value : values.values()) {
                    insert.setObject(index++, value);
                }
                insert.executeUpdate();
            }
        }
    }
}
