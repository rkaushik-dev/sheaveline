package io.github.rkaushikdev.sheaveline.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * The suite's database: one H2 in memory, migrated once for the whole run and emptied before each
 * test that asks for it.
 *
 * <p>Building a Hibernate factory per test costs more than the tests do, so this keeps one database
 * and one factory and gives every test a database of its own in effect: the schema, nothing in it,
 * and the generated keys counting from one.
 */
public final class TestDatabase {

    /** Where the suite's records live. */
    public static final String URL = "jdbc:h2:mem:sheaveline-tests";

    private static Database shared;

    private TestDatabase() {}

    /**
     * The shared database, emptied.
     *
     * @return the database
     */
    public static synchronized Database fresh() {
        if (shared == null) {
            shared = Database.open(URL);
            shared.createSchema();
        }
        empty(shared);
        return shared;
    }

    /**
     * Every row of one kind the session can see.
     *
     * @param session the session
     * @param type the row's class
     * @param <T> the row's class
     * @return the rows
     */
    public static <T> List<T> all(Session session, Class<T> type) {
        return session.entityManager()
                .createQuery("select row from " + type.getSimpleName() + " row", type)
                .getResultList();
    }

    /**
     * Assert that the database refuses some work for breaking one of its rules: a key, a reference,
     * a check, or a missing value.
     *
     * <p>A row whose key the database generates is written when it is added and the others when the
     * session commits, so the work passed in is both.
     *
     * @param work the writes and the commit
     */
    public static void assertRefused(ThrowingCallable work) {
        assertThatThrownBy(work)
                .isInstanceOf(PersistenceException.class)
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    private static void empty(Database database) {
        List<String> tables = database.existingTables();
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : tables) {
                statement.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (SQLException trouble) {
            throw new IllegalStateException("could not empty the test database", trouble);
        }
    }
}
