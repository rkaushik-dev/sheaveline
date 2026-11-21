package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.assertRefused;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.one;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.JP;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.johnPaul;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.spec;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.ropework.IndicationKind;
import io.github.rkaushikdev.sheaveline.ropework.TensionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelRopeworkTest {

    private static final RopeSerial SERIAL = RopeSerial.parse("FAT-2019-04412");
    private static final LocalDate TAKEN = LocalDate.of(2025, 1, 15);

    private Database database;

    @BeforeEach
    void johnPaulAndHisRope() {
        database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(johnPaul());
            HaulRopeRow rope = new HaulRopeRow(SERIAL, spec(), LocalDate.of(2019, 10, 4), Length.ofFeet(10132));
            rope.setLiftCode(JP);
            session.persist(rope);
            session.commit();
        }
    }

    private static SpliceRow splice(int tucks) {
        return new SpliceRow(
                SERIAL, Length.ofFeet(0), LocalDate.of(2019, 10, 4), "B Kettner", tucks, Length.ofFeet(138));
    }

    @Test
    void theSchemaCarriesEveryRopeTable() {
        assertThat(database.existingTables())
                .contains(
                        "wear_surveys",
                        "diameter_readings",
                        "lay_readings",
                        "break_surveys",
                        "break_observations",
                        "magnetic_tests",
                        "indications",
                        "splices",
                        "tension_systems",
                        "tension_readings");
    }

    @Test
    void aWearSurveyTakesItsReadingsWithIt() {
        try (Session session = database.session()) {
            WearSurveyRow survey = new WearSurveyRow(SERIAL, TAKEN, "R Halvorsen");
            survey.addDiameter(
                    new DiameterReadingRow(Length.ofFeet(1200), Length.ofInches("1.352"), Length.ofInches("1.348")));
            survey.addLay(new LayReadingRow(Length.ofFeet(1200), Length.ofInches("9.20")));
            session.persist(survey);
            session.commit();
        }
        try (Session session = database.session()) {
            WearSurveyRow found = one(session, WearSurveyRow.class);
            assertThat(found.getDiameters()).hasSize(1);
            assertThat(found.getLays()).hasSize(1);
            assertThat(found.getDiameters().get(0).getAcrossCrowns()).isEqualTo(Length.ofInches("1.352"));
            assertThat(found.getRope().getSerial()).isEqualTo(SERIAL);
        }
    }

    @Test
    void oneWearSurveyARopeADay() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                for (int twice = 0; twice < 2; twice++) {
                    session.persist(new WearSurveyRow(SERIAL, TAKEN, "R Halvorsen"));
                }
                session.commit();
            });
        }
    }

    @Test
    void aSurveyOfARopeThatIsNotThereIsRefused() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(new WearSurveyRow(RopeSerial.parse("BRI-2011-01887"), TAKEN, "R Halvorsen"));
                session.commit();
            });
        }
    }

    @Test
    void aBreakCountCannotHoldMoreInOneLayThanInSix() {
        try (Session session = database.session()) {
            BreakSurveyRow survey = new BreakSurveyRow(SERIAL, TAKEN, "R Halvorsen");
            survey.addObservation(new BreakObservationRow(Length.ofFeet(0), 7, 3));
            assertRefused(() -> {
                session.persist(survey);
                session.commit();
            });
        }
    }

    @Test
    void valleyBreaksAreCountedInsideTheOneLayTotal() {
        try (Session session = database.session()) {
            BreakSurveyRow survey = new BreakSurveyRow(SERIAL, TAKEN, "R Halvorsen");
            BreakObservationRow seen = new BreakObservationRow(Length.ofFeet(0), 2, 4);
            seen.setValleyBreaks(3);
            survey.addObservation(seen);
            assertRefused(() -> {
                session.persist(survey);
                session.commit();
            });
        }
    }

    @Test
    void aMagneticTestTakesItsIndicationsWithIt() {
        try (Session session = database.session()) {
            MagneticTestRow test = new MagneticTestRow(SERIAL, TAKEN, "Wire Rope Diagnostics", "LMA-250");
            test.addIndication(
                    new IndicationRow(Length.ofFeet(1800), IndicationKind.LOSS_OF_AREA, new BigDecimal("7.4")));
            session.persist(test);
            session.commit();
        }
        try (Session session = database.session()) {
            MagneticTestRow found = one(session, MagneticTestRow.class);
            assertThat(found.getIndications().get(0).getMagnitude()).isEqualByComparingTo("7.4");
            assertThat(found.getIndications().get(0).getKind()).isSameAs(IndicationKind.LOSS_OF_AREA);
        }
    }

    @Test
    void twoSplicesCannotShareAStationOnOneRope() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                for (int twice = 0; twice < 2; twice++) {
                    SpliceRow splice = splice(5);
                    splice.setMill(true);
                    session.persist(splice);
                }
                session.commit();
            });
        }
    }

    @Test
    void aSpliceWithTooFewTucksIsRefused() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(splice(2));
                session.commit();
            });
        }
    }

    @Test
    void aTensionSystemBelongsToTheLiftAndKeepsItsReadings() {
        try (Session session = database.session()) {
            TensionSystemRow system = new TensionSystemRow(
                    JP, TensionKind.HYDRAULIC, Load.ofPounds(34500), Length.ofFeet(16), Length.ofFeet(6));
            system.addReading(new TensionReadingRow(
                    LocalDateTime.of(2025, 1, 15, 7, 30),
                    Length.ofFeet(6),
                    Load.ofPounds(34800),
                    Temperature.ofDegreesF(14)));
            session.persist(system);
            session.commit();
        }
        try (Session session = database.session()) {
            TensionSystemRow found = session.find(TensionSystemRow.class, JP).orElseThrow();
            assertThat(found.getKind()).isSameAs(TensionKind.HYDRAULIC);
            assertThat(found.getReadings()).hasSize(1);
            assertThat(found.getReadings().get(0).getMeasured()).isEqualTo(Load.ofPounds(34800));
            assertThat(found.getLift().getName()).isEqualTo("John Paul");
        }
    }

    @Test
    void aTensionSystemWithNoTravelIsRefused() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(new TensionSystemRow(
                        JP, TensionKind.HYDRAULIC, Load.ofPounds(34500), Length.ofFeet(0), Length.ofFeet(0)));
                session.commit();
            });
        }
    }

    @Test
    void deletingASurveyTakesItsReadingsWithIt() {
        try (Session session = database.session()) {
            WearSurveyRow survey = new WearSurveyRow(SERIAL, TAKEN, "R Halvorsen");
            survey.addDiameter(
                    new DiameterReadingRow(Length.ofFeet(0), Length.ofInches("1.376"), Length.ofInches("1.376")));
            session.persist(survey);
            session.commit();
            session.remove(survey);
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, DiameterReadingRow.class)).isEmpty();
        }
    }

    @Test
    void theRopeFileFollowsTheRopeAndNotTheMachine() {
        try (Session session = database.session()) {
            session.persist(new WearSurveyRow(SERIAL, TAKEN, "R Halvorsen"));
            session.commit();
            HaulRopeRow rope = session.find(HaulRopeRow.class, SERIAL).orElseThrow();
            rope.setLiftCode(null);
            session.commit();
        }
        try (Session session = database.session()) {
            WearSurveyRow found = one(session, WearSurveyRow.class);
            assertThat(found.getRope().getLift()).isEmpty();
            assertThat(found.getRopeSerial()).isEqualTo(SERIAL);
        }
    }
}
