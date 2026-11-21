package io.github.rkaushikdev.sheaveline.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The readings the migration comparison rests on, proved able to tell two schemas apart.
 *
 * <p>A comparison whose query reads nothing finds every table equal to every other. So each reading
 * is shown to find something, and then a second migrated database has a column, an index and a
 * unique constraint planted in it, a foreign key and a primary key taken out of it, and a column
 * widened and loosened, and each reading has to see the change while a table nobody touched still
 * reads the same.
 */
class SchemaCatalogTest {

    private static Database clean;
    private static Database planted;

    @BeforeAll
    static void migrateTwoAndPlantOne() throws SQLException {
        clean = Database.inMemory();
        clean.createSchema();
        planted = Database.inMemory();
        planted.createSchema();
        try (Connection connection = planted.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE lifts ADD COLUMN planted INTEGER");
            statement.execute("ALTER TABLE towers ALTER COLUMN height SET NULL");
            statement.execute("ALTER TABLE carriers ALTER COLUMN grip_model VARCHAR(48)");
            statement.execute("CREATE INDEX ix_towers_planted ON towers (height)");
            statement.execute("ALTER TABLE carriers ADD CONSTRAINT uq_carriers_hanger UNIQUE (hanger)");
            statement.execute("ALTER TABLE terminals DROP CONSTRAINT fk_terminals_lift_code_lifts");
            statement.execute("ALTER TABLE survey_shots DROP CONSTRAINT pk_survey_shots");
        }
    }

    @AfterAll
    static void closeBoth() {
        clean.close();
        planted.close();
    }

    @Test
    void everyReadingFindsSomething() {
        SchemaCatalog read = SchemaCatalog.of(clean);
        assertThat(read.tables()).contains("lifts", "towers").doesNotContain("flyway_schema_history");
        assertThat(read.columns("towers")).containsKeys("lift_code", "number", "height");
        assertThat(read.columns("towers").get("height").nullable()).isFalse();
        assertThat(read.columns("towers").get("id").identity()).isTrue();
        assertThat(read.columns("carriers").get("grip_model").length()).isEqualTo("32");
        assertThat(read.columns("stops").get("stopped").timePrecision()).isEqualTo("6");
        assertThat(read.primaryKey("survey_shots")).containsExactly("id");
        assertThat(read.uniques("towers"))
                .containsExactly(new SchemaCatalog.Keyed("one_number_a_line", List.of("lift_code", "number")));
        assertThat(read.foreignKeys("terminals"))
                .containsExactly(
                        new SchemaCatalog.Reference("fk_terminals_lift_code_lifts", List.of("lift_code"), "lifts"));
        assertThat(read.indexes("stops"))
                .containsExactly(new SchemaCatalog.Keyed("ix_stops_still_open", List.of("restarted", "stopped")));
        assertThat(read.indexes()).hasSizeGreaterThan(5);
    }

    @Test
    void aPlantedDifferenceIsSeenByTheReadingThatLooksForIt() {
        SchemaCatalog before = SchemaCatalog.of(clean);
        SchemaCatalog after = SchemaCatalog.of(planted);
        assertThat(after.tables()).isEqualTo(before.tables());
        assertThat(after.columns("lifts")).isNotEqualTo(before.columns("lifts"));
        assertThat(after.columns("towers")).isNotEqualTo(before.columns("towers"));
        assertThat(after.columns("carriers")).isNotEqualTo(before.columns("carriers"));
        assertThat(after.indexes("towers")).isNotEqualTo(before.indexes("towers"));
        assertThat(after.uniques("carriers")).isNotEqualTo(before.uniques("carriers"));
        assertThat(after.foreignKeys("terminals")).isNotEqualTo(before.foreignKeys("terminals"));
        assertThat(after.primaryKey("survey_shots")).isNotEqualTo(before.primaryKey("survey_shots"));
    }

    @Test
    void aTableNobodyTouchedStillReadsTheSame() {
        SchemaCatalog before = SchemaCatalog.of(clean);
        SchemaCatalog after = SchemaCatalog.of(planted);
        assertThat(after.columns("stops")).isEqualTo(before.columns("stops"));
        assertThat(after.primaryKey("stops")).isEqualTo(before.primaryKey("stops"));
        assertThat(after.uniques("stops")).isEqualTo(before.uniques("stops"));
        assertThat(after.foreignKeys("stops")).isEqualTo(before.foreignKeys("stops"));
        assertThat(after.indexes("stops")).isEqualTo(before.indexes("stops"));
    }
}
