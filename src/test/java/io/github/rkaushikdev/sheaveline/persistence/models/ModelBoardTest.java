package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.assertRefused;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.one;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.JP;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.evac.DrillKind;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelBoardTest {

    private static final LocalDateTime AT = LocalDateTime.of(2025, 1, 15, 11, 30);

    private Database database;

    @BeforeEach
    void johnPaulOnFile() {
        database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(johnPaul());
            session.commit();
        }
    }

    private static IncidentRow incidentRow(LocalDateTime at, int people) {
        return new IncidentRow(
                JP, at, IncidentKind.DEROPEMENT, InjuryLevel.NONE, people, "chair 34 off at T4", "K Ostler");
    }

    private static IncidentRow incidentRow() {
        return incidentRow(AT, 0);
    }

    private static FilingRow filingRow() {
        return new FilingRow(
                new FilingNumber("UT", 2025, 1),
                FilingKind.INCIDENT,
                JP,
                LocalDateTime.of(2025, 1, 15, 12, 30),
                "chair 34 off at T4",
                "H Okafor");
    }

    private static DrillRow drillRow(DrillKind kind, int carriersWorked, int elapsedSeconds) {
        return new DrillRow(JP, LocalDate.of(2024, 12, 5), kind, carriersWorked, elapsedSeconds, "H Okafor");
    }

    private static CertificateRow card(LocalDate issued, LocalDate expires) {
        return new CertificateRow("R Diaz", Endorsement.OPERATOR, issued, expires, "Utah tramway board");
    }

    private static CertificateRow card() {
        return card(LocalDate.of(2024, 11, 1), LocalDate.of(2025, 11, 1));
    }

    @Test
    void theSchemaCarriesEveryBoardTable() {
        assertThat(database.existingTables())
                .contains("filings", "incidents", "drills", "drill_teams", "certificates", "certificate_classes");
    }

    @Test
    void anIncidentStartsWithNoFilingAgainstIt() {
        try (Session session = database.session()) {
            session.persist(incidentRow());
            session.commit();
        }
        try (Session session = database.session()) {
            IncidentRow found = one(session, IncidentRow.class);
            assertThat(found.getFiling()).isEmpty();
            assertThat(found.getFilingNumber()).isEmpty();
        }
    }

    @Test
    void aFilingCanBeHungOnAnIncidentLater() {
        try (Session session = database.session()) {
            session.persist(incidentRow());
            session.persist(filingRow());
            session.commit();
            IncidentRow found = one(session, IncidentRow.class);
            found.setFilingNumber(new FilingNumber("UT", 2025, 1));
            session.commit();
        }
        try (Session session = database.session()) {
            IncidentRow found = one(session, IncidentRow.class);
            assertThat(found.getFiling())
                    .get()
                    .extracting(FilingRow::getFiledBy)
                    .isEqualTo("H Okafor");
        }
    }

    @Test
    void aFilingNumberThatDoesNotExistIsRefused() {
        try (Session session = database.session()) {
            IncidentRow incident = incidentRow();
            incident.setFilingNumber(new FilingNumber("UT", 2025, 9));
            assertRefused(() -> {
                session.persist(incident);
                session.commit();
            });
        }
    }

    @Test
    void twoIncidentsCannotShareAMomentOnOneMachine() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(incidentRow());
                session.persist(incidentRow());
                session.commit();
            });
        }
    }

    @Test
    void anIncidentCannotInvolveFewerThanNobody() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(incidentRow(AT, -1));
                session.commit();
            });
        }
    }

    @Test
    void oneFilingCanAnswerForMoreThanOneIncident() {
        try (Session session = database.session()) {
            session.persist(filingRow());
            IncidentRow first = incidentRow(LocalDateTime.of(2025, 1, 15, 11, 30), 0);
            IncidentRow second = incidentRow(LocalDateTime.of(2025, 1, 15, 14, 0), 0);
            first.setFilingNumber(new FilingNumber("UT", 2025, 1));
            second.setFilingNumber(new FilingNumber("UT", 2025, 1));
            session.persist(first);
            session.persist(second);
            session.commit();
        }
        try (Session session = database.session()) {
            FilingRow found = session.find(FilingRow.class, new FilingNumber("UT", 2025, 1))
                    .orElseThrow();
            assertThat(found.getIncidents()).hasSize(2);
        }
    }

    @Test
    void aDrillTakesItsTeamsWithIt() {
        try (Session session = database.session()) {
            DrillRow drill = drillRow(DrillKind.FULL_LINE, 8, 5700);
            drill.addTeam(new DrillTeamRow("alpha"));
            drill.addTeam(new DrillTeamRow("bravo"));
            session.persist(drill);
            session.commit();
        }
        try (Session session = database.session()) {
            DrillRow found = one(session, DrillRow.class);
            assertThat(found.getTeams()).extracting(DrillTeamRow::getTeam).containsExactlyInAnyOrder("alpha", "bravo");
        }
    }

    @Test
    void aTeamIsCountedOnceInADrill() {
        try (Session session = database.session()) {
            DrillRow drill = drillRow(DrillKind.FULL_LINE, 8, 5700);
            drill.addTeam(new DrillTeamRow("alpha"));
            drill.addTeam(new DrillTeamRow("alpha"));
            assertRefused(() -> {
                session.persist(drill);
                session.commit();
            });
        }
    }

    @Test
    void oneDrillAMachineADay() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                for (int twice = 0; twice < 2; twice++) {
                    session.persist(drillRow(DrillKind.PARTIAL, 4, 1800));
                }
                session.commit();
            });
        }
    }

    @Test
    void aCertificateCarriesTheClassesItCovers() {
        try (Session session = database.session()) {
            CertificateRow card = card();
            card.addClass(new CertificateClassRow("FIXED_CHAIR"));
            card.addClass(new CertificateClassRow("DETACHABLE_CHAIR"));
            session.persist(card);
            session.commit();
        }
        try (Session session = database.session()) {
            CertificateRow found = one(session, CertificateRow.class);
            assertThat(found.getClasses())
                    .extracting(CertificateClassRow::getLiftClass)
                    .containsExactlyInAnyOrder("FIXED_CHAIR", "DETACHABLE_CHAIR");
        }
    }

    @Test
    void aCardRunsForwards() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(card(LocalDate.of(2025, 11, 1), LocalDate.of(2024, 11, 1)));
                session.commit();
            });
        }
    }

    @Test
    void aClassIsListedOnceOnACard() {
        try (Session session = database.session()) {
            CertificateRow card = card();
            card.addClass(new CertificateClassRow("FIXED_CHAIR"));
            card.addClass(new CertificateClassRow("FIXED_CHAIR"));
            assertRefused(() -> {
                session.persist(card);
                session.commit();
            });
        }
    }

    @Test
    void deletingACardTakesItsClassesWithIt() {
        try (Session session = database.session()) {
            CertificateRow card = card();
            card.addClass(new CertificateClassRow("FIXED_CHAIR"));
            session.persist(card);
            session.commit();
            session.remove(card);
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, CertificateClassRow.class)).isEmpty();
        }
    }
}
