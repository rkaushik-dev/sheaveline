package io.github.rkaushikdev.sheaveline.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.util.ClassUtils;

/**
 * The questions the office asks most, and what the planner does with them.
 *
 * <p>An index nobody uses is a write that costs and a read that does not benefit, and a missing index
 * only shows up when the table is big enough to hurt, which on a lift department is about four
 * seasons in. So the questions are written out here as the repositories ask them and the plan is
 * read back.
 *
 * <p>The plan names the index it walks and the condition it searches it on, and says {@code
 * tableScan} when it reads the whole table instead. That one word is the difference between reading
 * a hundred rows and reading forty thousand.
 *
 * <p>Where a question fixes an index's first column, the planner ranks that index level with the
 * one the database built for the foreign key on the same column, and walks whichever is older. So
 * the index written for a question is proved to serve it by pointing the planner at it, and the
 * planner's own choice is held to an index searched on that same first column.
 */
class IndexesTest {

    private static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("what is stopped right now", "select * from stops where restarted is null order by stopped");
        QUESTIONS.put(
                "what stops touched a day",
                "select * from stops where lift_code = 'JP' and stopped <= '2025-01-15'"
                        + " and (restarted is null or restarted >= '2025-01-15') order by stopped");
        QUESTIONS.put("what holds are on a machine", "select * from holds where lift_code = 'JP' order by started");
        QUESTIONS.put("what defects are open", "select * from defects where cleared is null");
        QUESTIONS.put(
                "what work is open on a machine",
                "select * from work_orders where lift_code = 'JP' and closed is null");
        QUESTIONS.put("what has not been filed", "select * from incidents where filing_number is null order by at");
        QUESTIONS.put(
                "what has been filed on a machine", "select * from filings where lift_code = 'JP' order by filed");
        QUESTIONS.put(
                "what the weather has been since",
                "select * from observations where taken >= '2025-01-01' order by taken");
        QUESTIONS.put(
                "what a machine was inspected for",
                "select * from inspection_records where lift_code = 'JP' order by done");
        QUESTIONS.put(
                "what a rope has been walked for",
                "select * from wear_surveys where rope_serial = 'FAT-2019-04412' order by taken");
        QUESTIONS.put(
                "what days a machine was written up", "select * from day_logs where lift_code = 'JP' order by day");
        QUESTIONS.put("what the meters have read", "select * from meter_readings where lift_code = 'JP' order by day");
    }

    /** An index walked on a condition, as the plan prints it: {@code /* PUBLIC.NAME: CONDITION}. */
    private static final Pattern SEARCHED = Pattern.compile("/\\* PUBLIC\\.\\w+: ");

    /** The table a question reads, which is where the planner is told which index to walk. */
    private static final Pattern FROM = Pattern.compile("from (\\w+) ");

    private static Database database;

    @BeforeAll
    static void migrate() {
        database = Database.inMemory();
        database.createSchema();
    }

    @AfterAll
    static void close() {
        database.close();
    }

    static Stream<String> questions() {
        return QUESTIONS.keySet().stream().sorted();
    }

    private static String planFor(Database where, String query) {
        try (Connection connection = where.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet plan = statement.executeQuery("explain " + query)) {
            StringBuilder text = new StringBuilder();
            while (plan.next()) {
                text.append(plan.getString(1)).append(' ');
            }
            return text.toString();
        } catch (SQLException trouble) {
            throw new IllegalStateException(query, trouble);
        }
    }

    private static Set<SchemaCatalog.Keyed> declaredIndexes() {
        ClassLoader loader = IndexesTest.class.getClassLoader();
        Set<SchemaCatalog.Keyed> declared = new HashSet<>();
        for (String name : new PersistenceManagedTypesScanner(new DefaultResourceLoader(loader))
                .scan(Database.MAPPED_PACKAGE)
                .getManagedClassNames()) {
            Class<?> type = ClassUtils.resolveClassName(name, loader);
            Table table = type.getAnnotation(Table.class);
            if (type.isAnnotationPresent(Entity.class) && table != null) {
                for (Index index : table.indexes()) {
                    List<String> columns = Arrays.stream(index.columnList().split(","))
                            .map(column -> column.strip().toLowerCase(Locale.ROOT))
                            .toList();
                    declared.add(new SchemaCatalog.Keyed(index.name().toLowerCase(Locale.ROOT), columns));
                }
            }
        }
        return declared;
    }

    @ParameterizedTest
    @MethodSource("questions")
    void thePlannerWalksAnIndexRatherThanTheTable(String question) {
        String plan = planFor(database, QUESTIONS.get(question));
        assertThat(plan).as(question).doesNotContain("tableScan").containsPattern(SEARCHED);
    }

    @Test
    void theIndexAQuestionUsesIsTheOneItWasWrittenFor() {
        Map<String, String> wanted = new LinkedHashMap<>();
        wanted.put("what is stopped right now", "ix_stops_still_open");
        wanted.put("what defects are open", "ix_defects_still_open");
        wanted.put("what work is open on a machine", "ix_work_orders_open_by_machine");
        wanted.put("what has not been filed", "ix_incidents_not_yet_filed");
        wanted.put("what has been filed on a machine", "ix_filings_by_machine");
        wanted.put("what the weather has been since", "ix_observations_by_moment");
        wanted.put("what holds are on a machine", "ix_holds_by_machine");
        Map<String, String> leading = new LinkedHashMap<>();
        for (SchemaCatalog.Keyed index : declaredIndexes()) {
            leading.put(index.name(), index.columns().get(0));
        }
        try (Database made = Database.inMemory()) {
            made.createSchema();
            for (Map.Entry<String, String> question : wanted.entrySet()) {
                String query = QUESTIONS.get(question.getKey());
                String index = question.getValue().toUpperCase(Locale.ROOT);
                String pointed = FROM.matcher(query).replaceFirst("from $1 use index (" + index + ") ");
                assertThat(planFor(made, pointed)).as(question.getKey()).contains("/* PUBLIC." + index + ": ");
                String first = leading.get(question.getValue()).toUpperCase(Locale.ROOT);
                assertThat(planFor(made, query))
                        .as(question.getKey())
                        .containsPattern("/\\* PUBLIC\\.\\w+: " + first + " ");
            }
        }
    }

    @Test
    void everyIndexThisPackageDeclaresReachesTheDatabase() {
        Set<SchemaCatalog.Keyed> declared = declaredIndexes();
        Set<SchemaCatalog.Keyed> built;
        try (Database made = Database.inMemory()) {
            made.createSchema();
            built = SchemaCatalog.of(made).indexes();
        }
        assertThat(declared).isNotEmpty();
        assertThat(built).containsAll(declared);
        assertThat(built).isEqualTo(declared);
    }
}
