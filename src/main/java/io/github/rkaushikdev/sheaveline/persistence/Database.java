package io.github.rkaushikdev.sheaveline.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.util.ClassUtils;

/**
 * The database floor: one connection pool, one schema built by the migrations, and the sessions
 * every piece of work runs in.
 *
 * <p>Sessions come from here and nowhere else: {@link #session()} for work that decides its own
 * commits, and {@link #inSession(Consumer)} for the small units of work nearly everything is. Every
 * write in this system has an obvious boundary, and the one thing that must never happen is a half
 * written day log, so the scope commits or rolls back and there is no third option.
 *
 * <p>The entity manager factory is built the first time a session is asked for, and it checks the
 * mapping against the schema when it is. A database nobody has built a schema in can still have one
 * built, and can still be dropped.
 */
public final class Database implements AutoCloseable {

    /** Where the entities and their column converters live. */
    public static final String MAPPED_PACKAGE = "io.github.rkaushikdev.sheaveline.persistence";

    /** Where the migrations live, one per revision of the schema. */
    public static final String MIGRATIONS = "classpath:db/migration";

    /**
     * Column names the schema uses that H2 otherwise reads as keywords. They are ordinary words in
     * the domain, so the url tells H2 so rather than the schema renaming them. {@code on} and {@code
     * where} cannot be told this way, because H2 would stop reading its own joins, so those two
     * columns are quoted wherever they are written.
     */
    static final String NON_KEYWORDS = "NON_KEYWORDS=DAY,YEAR";

    /**
     * An in-memory database belongs to the connections that have it open, and a pool closes and
     * opens connections as it likes. Without this the schema built on one connection is gone by the
     * next, which is the difference between a working program and one whose first request finds no
     * tables.
     */
    static final String KEEP_IN_MEMORY = "DB_CLOSE_DELAY=-1";

    private static final String HISTORY_TABLE = "flyway_schema_history";

    private static final AtomicLong MEMORY_DATABASES = new AtomicLong();

    private final String url;
    private final boolean echo;
    private final HikariDataSource pool;
    private LocalContainerEntityManagerFactoryBean factory;

    private Database(String url, boolean echo, HikariDataSource pool) {
        this.url = url;
        this.echo = echo;
        this.pool = pool;
    }

    /**
     * Open a database. Nothing connects until something is asked of it.
     *
     * @param url a JDBC url, such as {@code jdbc:h2:file:./sheaveline}
     * @return the database
     */
    public static Database open(String url) {
        return open(url, false);
    }

    /**
     * Open a database, optionally printing every statement sent to it.
     *
     * @param url a JDBC url
     * @param echo whether to print the statements
     * @return the database
     */
    public static Database open(String url, boolean echo) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(url));
        config.setPoolName("sheaveline");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setInitializationFailTimeout(-1);
        // A database that will not answer is reported within seconds, not after half a minute.
        config.setConnectionTimeout(3_000);
        return new Database(url, echo, new HikariDataSource(config));
    }

    /**
     * A database of its own, held in memory, that nothing else in the process can see.
     *
     * @return the database
     */
    public static Database inMemory() {
        return open("jdbc:h2:mem:sheaveline-" + MEMORY_DATABASES.incrementAndGet());
    }

    /**
     * The url as the driver gets it. H2 is told which column names are not keywords, and an
     * in-memory database is kept for as long as this program wants it rather than for as long as
     * one connection is open.
     *
     * @param configured the url as configured
     * @return the url to connect with
     */
    public static String jdbcUrl(String configured) {
        if (!configured.startsWith("jdbc:h2:")) {
            return configured;
        }
        String upper = configured.toUpperCase(Locale.ROOT);
        StringBuilder url = new StringBuilder(configured);
        if (configured.contains(":mem:") && !upper.contains("DB_CLOSE_DELAY")) {
            url.append(';').append(KEEP_IN_MEMORY);
        }
        if (!upper.contains("NON_KEYWORDS")) {
            url.append(';').append(NON_KEYWORDS);
        }
        return url.toString();
    }

    /**
     * Every table the entities map, sorted. A table no entity maps is a table nobody reads.
     *
     * @return the table names
     */
    public static List<String> tableNames() {
        ClassLoader loader = Database.class.getClassLoader();
        TreeSet<String> names = new TreeSet<>();
        for (String name : new PersistenceManagedTypesScanner(new DefaultResourceLoader(loader))
                .scan(MAPPED_PACKAGE)
                .getManagedClassNames()) {
            Class<?> type = ClassUtils.resolveClassName(name, loader);
            Table table = type.getAnnotation(Table.class);
            if (type.isAnnotationPresent(Entity.class) && table != null) {
                names.add(table.name());
            }
        }
        return List.copyOf(names);
    }

    /**
     * The entity manager factory every session is opened from.
     *
     * @param dataSource where the connections come from
     * @param echo whether to print every statement
     * @param ddl what Hibernate does to the schema when it starts: {@code validate} in service,
     *     {@code create} only to build a schema straight from the mapping for comparison
     * @return the factory bean, not yet initialised
     */
    public static LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(
            DataSource dataSource, boolean echo, String ddl) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPackagesToScan(MAPPED_PACKAGE);
        bean.setPersistenceUnitName("sheaveline");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddl);
        properties.put("hibernate.show_sql", String.valueOf(echo));
        properties.put("hibernate.jdbc.time_zone", "UTC");
        // A missing value is the database's to refuse, as every other broken rule is, so the
        // refusal looks the same whichever rule it was.
        properties.put("hibernate.check_nullability", "false");
        // Hibernate otherwise lays columns out by type size, composite keys included, and a schema
        // it builds stops matching the order the migrations declare.
        properties.put("hibernate.column_ordering_strategy", "legacy");
        bean.setJpaPropertyMap(properties);
        return bean;
    }

    /**
     * The url as configured.
     *
     * @return the url
     */
    public String url() {
        return url;
    }

    /**
     * Where the connections come from, for the few readers that talk to the database directly.
     *
     * @return the pool
     */
    public DataSource dataSource() {
        return pool;
    }

    /**
     * Whether the records vanish when this database is closed.
     *
     * @return true for an in-memory database
     */
    public boolean isInMemory() {
        return url.contains(":mem:");
    }

    /**
     * The factory, built and checked against the schema the first time it is asked for.
     *
     * @return the factory
     */
    public synchronized EntityManagerFactory entityManagerFactory() {
        if (factory == null) {
            LocalContainerEntityManagerFactoryBean built = entityManagerFactoryBean(pool, echo, "validate");
            built.afterPropertiesSet();
            factory = built;
        }
        return factory.getObject();
    }

    /**
     * A session with its transaction begun. It rolls back whatever is not committed when it closes.
     *
     * @return the session
     */
    public Session session() {
        return new Session(entityManagerFactory().createEntityManager());
    }

    /**
     * Run some work in a session that commits on the way out, rolls back on anything thrown, and
     * closes either way.
     *
     * @param work what to do in the session
     */
    public void inSession(Consumer<Session> work) {
        try (Session session = session()) {
            work.accept(session);
            session.commit();
        }
    }

    /** Build every table the program knows about, or bring an older schema up to date. */
    public void createSchema() {
        flyway().migrate();
    }

    /** Take the schema out. Everything in it goes with it. */
    public void dropSchema() {
        closeFactory();
        flyway().clean();
    }

    /**
     * The tables in the database as it stands, sorted, the migrations' own bookkeeping aside.
     *
     * @return the table names, lower case
     * @throws StorageError when the database cannot be read
     */
    public List<String> existingTables() {
        TreeSet<String> names = new TreeSet<>();
        try (Connection connection = pool.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    if (!"INFORMATION_SCHEMA".equalsIgnoreCase(tables.getString("TABLE_SCHEM"))) {
                        names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (SQLException trouble) {
            throw new StorageError("cannot read the tables in " + url, trouble);
        }
        names.remove(HISTORY_TABLE);
        return List.copyOf(names);
    }

    /**
     * Close the factory and the pool. A database held in memory is thrown away, as closing its last
     * connection would. Safe to call twice.
     */
    @Override
    public synchronized void close() {
        closeFactory();
        if (pool.isClosed()) {
            return;
        }
        pool.close();
        if (isInMemory()) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(url));
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException trouble) {
                throw new StorageError("cannot close " + url, trouble);
            }
        }
    }

    private synchronized void closeFactory() {
        if (factory != null) {
            factory.destroy();
            factory = null;
        }
    }

    private Flyway flyway() {
        return Flyway.configure(Database.class.getClassLoader())
                .dataSource(pool)
                .locations(MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }
}
