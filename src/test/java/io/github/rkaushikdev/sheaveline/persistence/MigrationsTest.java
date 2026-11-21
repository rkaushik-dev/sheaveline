package io.github.rkaushikdev.sheaveline.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Configuration;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * The migrations and the entities have to agree, and only a run proves it.
 *
 * <p>A migration says what the schema was when somebody wrote it, and the moment a column is added
 * to an entity and nobody writes the migration for it, the office server and the laptop are two
 * different databases. So this runs the migrations onto an empty database and compares the result
 * against the schema the entities alone would build, table by table and column by column. The
 * readings themselves are proved against planted differences in {@link SchemaCatalogTest}.
 */
class MigrationsTest {

    private static Database migrated;
    private static Database built;

    @BeforeAll
    static void buildTheSchemaBothWays() {
        migrated = Database.inMemory();
        migrated.createSchema();
        built = Database.inMemory();
        LocalContainerEntityManagerFactoryBean export =
                Database.entityManagerFactoryBean(built.dataSource(), false, "create");
        export.afterPropertiesSet();
        export.destroy();
    }

    @AfterAll
    static void closeBoth() {
        migrated.close();
        built.close();
    }

    private static Flyway flyway(Database database) {
        return Flyway.configure(MigrationsTest.class.getClassLoader())
                .dataSource(database.dataSource())
                .locations(Database.MIGRATIONS)
                .load();
    }

    private static SchemaCatalog migratedCatalog() {
        return SchemaCatalog.of(migrated);
    }

    private static SchemaCatalog builtCatalog() {
        return SchemaCatalog.of(built);
    }

    private static List<String> versions(MigrationInfo[] migrations) {
        return Arrays.stream(migrations)
                .map(one -> one.getVersion().getVersion())
                .toList();
    }

    @Test
    void theChainRunsFromTheBaselineToOneHead() {
        try (Database empty = Database.inMemory()) {
            MigrationInfo[] pending = flyway(empty).info().pending();
            assertThat(pending).isNotEmpty();
            assertThat(versions(pending))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, pending.length)
                            .mapToObj(String::valueOf)
                            .toList());
            assertThat(pending[0].getDescription()).isEqualTo("baseline");
            empty.createSchema();
            MigrationInfoService after = flyway(empty).info();
            assertThat(after.pending()).isEmpty();
            assertThat(after.current().getVersion().getVersion()).isEqualTo(String.valueOf(pending.length));
        }
    }

    @Test
    void theMigrationBuildsEveryTableTheModelsDescribe() {
        assertThat(builtCatalog().tables()).containsExactlyElementsOf(Database.tableNames());
        assertThat(migratedCatalog().tables()).isEqualTo(builtCatalog().tables());
    }

    @Test
    void everyTableHasTheSameColumnsEitherWay() {
        for (String table : builtCatalog().tables()) {
            assertThat(migratedCatalog().columns(table))
                    .as(table)
                    .isNotEmpty()
                    .isEqualTo(builtCatalog().columns(table));
        }
    }

    @Test
    void thePrimaryKeysAgree() {
        for (String table : builtCatalog().tables()) {
            assertThat(migratedCatalog().primaryKey(table))
                    .as(table)
                    .isNotEmpty()
                    .isEqualTo(builtCatalog().primaryKey(table));
        }
    }

    @Test
    void theForeignKeysAgree() {
        for (String table : builtCatalog().tables()) {
            assertThat(migratedCatalog().foreignKeys(table))
                    .as(table)
                    .isEqualTo(builtCatalog().foreignKeys(table));
        }
    }

    @Test
    void theUniqueConstraintsAgree() {
        for (String table : builtCatalog().tables()) {
            assertThat(migratedCatalog().uniques(table))
                    .as(table)
                    .isEqualTo(builtCatalog().uniques(table));
        }
    }

    @Test
    void theMigrationGoesBackDownAgain() {
        try (Database down = Database.inMemory()) {
            down.createSchema();
            assertThat(down.existingTables()).contains("lifts");
            down.dropSchema();
            assertThat(down.existingTables()).isEmpty();
        }
    }

    @Test
    void theEnvironmentVariableWinsOverTheDefault(@TempDir Path folder) {
        String url = "jdbc:h2:file:" + folder.resolve("fromenv").toAbsolutePath();
        Configuration configured = Configuration.fromEnvironment(Map.of(Configuration.DATABASE, url));
        assertThat(configured.database()).isEqualTo(url).isNotEqualTo(Configuration.DEFAULT_DATABASE);
        try (Database database = Database.open(configured.database())) {
            database.createSchema();
            assertThat(database.existingTables()).contains("lifts");
            // A second run finds nothing left to do.
            int applied = flyway(database).info().applied().length;
            database.createSchema();
            assertThat(flyway(database).info().applied()).hasSize(applied);
        }
    }
}
