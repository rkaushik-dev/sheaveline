package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.assertRefused;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.one;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.JP;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelUpkeepTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);

    private Database database;

    @BeforeEach
    void johnPaulOnFile() {
        database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(johnPaul());
            session.commit();
        }
    }

    private static InspectionRecordRow record() {
        return record("W1", DAY);
    }

    private static InspectionRecordRow record(String code, LocalDate done) {
        return new InspectionRecordRow(JP, code, done, "M Vetter");
    }

    private static DefectRow defect(DefectLevel level) {
        return new DefectRow("JP-T4", level, "sheave liner cracked", DAY);
    }

    private static DefectRow clearedOn(LocalDate cleared) {
        DefectRow defect = defect(DefectLevel.DEFECT);
        defect.setCleared(cleared);
        defect.setClearedBy("M Vetter");
        return defect;
    }

    private static WorkOrderRow order(int number, String detail, Priority priority) {
        return new WorkOrderRow(number, JP, DAY, "M Vetter", detail, priority);
    }

    @Test
    void theSchemaCarriesEveryUpkeepTable() {
        assertThat(database.existingTables()).contains("inspection_records", "defects", "work_orders");
    }

    @Test
    void aRecordTakesItsDefectsWithIt() {
        try (Session session = database.session()) {
            InspectionRecordRow walked = record();
            walked.addDefect(defect(DefectLevel.DEFECT));
            session.persist(walked);
            session.commit();
        }
        try (Session session = database.session()) {
            InspectionRecordRow found = one(session, InspectionRecordRow.class);
            assertThat(found.getDefects()).hasSize(1);
            assertThat(found.getDefects().get(0).getLevel()).isSameAs(DefectLevel.DEFECT);
            assertThat(found.getLift().getName()).isEqualTo("John Paul");
        }
    }

    @Test
    void oneRecordAJobADay() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(record());
                session.persist(record());
                session.commit();
            });
        }
    }

    @Test
    void theSameJobCanBeDoneOnTwoDays() {
        try (Session session = database.session()) {
            session.persist(record("W1", LocalDate.of(2025, 1, 8)));
            session.persist(record("W1", LocalDate.of(2025, 1, 15)));
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, InspectionRecordRow.class)).hasSize(2);
        }
    }

    @Test
    void aRecordKeepsItsMeterReadings() {
        try (Session session = database.session()) {
            InspectionRecordRow walked = record("H1", DAY);
            walked.setHours(new BigDecimal("42000.0"));
            walked.setCycles(1_000_000);
            session.persist(walked);
            session.commit();
        }
        try (Session session = database.session()) {
            InspectionRecordRow found = one(session, InspectionRecordRow.class);
            assertThat(found.getHours())
                    .hasValueSatisfying(hours -> assertThat(hours).isEqualByComparingTo("42000.0"));
            assertThat(found.getCycles()).contains(1_000_000);
        }
    }

    @Test
    void aDefectCannotBeClearedBeforeItWasRaised() {
        try (Session session = database.session()) {
            InspectionRecordRow walked = record();
            walked.addDefect(clearedOn(LocalDate.of(2025, 1, 1)));
            assertRefused(() -> {
                session.persist(walked);
                session.commit();
            });
        }
    }

    @Test
    void aDefectNeedsARecordToHangOff() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(defect(DefectLevel.DEFECT));
                session.commit();
            });
        }
    }

    @Test
    void deletingARecordTakesItsDefectsWithIt() {
        try (Session session = database.session()) {
            InspectionRecordRow walked = record();
            walked.addDefect(defect(DefectLevel.DEFECT));
            session.persist(walked);
            session.commit();
            session.remove(walked);
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, DefectRow.class)).isEmpty();
        }
    }

    @Test
    void aWorkOrderCarriesItsOwnPlaceAndPriority() {
        try (Session session = database.session()) {
            WorkOrderRow order = order(1, "sheave liner cracked", Priority.SCHEDULED);
            order.setWhere("JP-T4");
            session.persist(order);
            session.commit();
        }
        try (Session session = database.session()) {
            WorkOrderRow found = session.find(WorkOrderRow.class, 1).orElseThrow();
            assertThat(found.getPriority()).isSameAs(Priority.SCHEDULED);
            assertThat(found.getWhere()).contains("JP-T4");
            assertThat(found.getClosed()).isEmpty();
        }
    }

    /** Downgrading a defect later must not rewrite what the department decided. */
    @Test
    void aWorkOrderDoesNotReadThroughToTheDefect() {
        try (Session session = database.session()) {
            InspectionRecordRow walked = record();
            walked.addDefect(defect(DefectLevel.SERIOUS));
            session.persist(walked);
            WorkOrderRow order = order(1, "sheave liner cracked", Priority.IMMEDIATE);
            order.setWhere("JP-T4");
            session.persist(order);
            session.commit();
            DefectRow found = one(session, DefectRow.class);
            found.setLevel(DefectLevel.OBSERVATION);
            session.commit();
        }
        try (Session session = database.session()) {
            WorkOrderRow order = session.find(WorkOrderRow.class, 1).orElseThrow();
            assertThat(order.getPriority()).isSameAs(Priority.IMMEDIATE);
        }
    }

    @Test
    void aWorkOrderCannotCloseBeforeItWasRaised() {
        try (Session session = database.session()) {
            WorkOrderRow order = order(1, "a thing", Priority.ROUTINE);
            order.setClosed(LocalDate.of(2025, 1, 1));
            order.setClosedBy("M Vetter");
            assertRefused(() -> {
                session.persist(order);
                session.commit();
            });
        }
    }

    @Test
    void workOrdersAreNumberedFromOne() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(order(0, "a thing", Priority.ROUTINE));
                session.commit();
            });
        }
    }
}
