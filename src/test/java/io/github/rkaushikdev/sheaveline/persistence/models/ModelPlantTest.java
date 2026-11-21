package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.assertRefused;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.JP;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.johnPaul;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.spec;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.NamedEnum;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
import io.github.rkaushikdev.sheaveline.plant.BrakeKind;
import io.github.rkaushikdev.sheaveline.plant.CarrierType;
import io.github.rkaushikdev.sheaveline.plant.DriveType;
import io.github.rkaushikdev.sheaveline.plant.GripType;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.RopeConstruction;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.TerminalRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelPlantTest {

    private Database database;

    @BeforeEach
    void freshSchema() {
        database = TestDatabase.fresh();
    }

    private static TerminalRow terminalRow() {
        TerminalRow row = new TerminalRow(
                TerminalRole.DRIVE,
                "base",
                Length.ofFeet(0),
                Length.ofFeet(6425),
                Length.ofFeetInches(11, 6),
                "polyurethane");
        row.setDriveType(DriveType.AC_DRIVE);
        row.setRatedHorsepower(700);
        row.setGearboxRatio(new BigDecimal("18.5"));
        row.setAuxiliary(DriveType.DIESEL_HYDRAULIC);
        return row;
    }

    private static TowerRow towerRow(int number) {
        return new TowerRow(
                number,
                Length.ofFeet(600L * number),
                Length.ofFeet(6496),
                Length.ofFeet(55),
                AssemblyKind.SUPPORT,
                4,
                Angle.ofDegrees("0.7"),
                AssemblyKind.SUPPORT,
                4,
                Angle.ofDegrees("0.7"));
    }

    private static CarrierRow carrierRow(int number) {
        CarrierRow row = new CarrierRow(number, CarrierType.CHAIR_4, Load.ofPounds(410), Length.ofFeetInches(6, 4));
        row.setRestrainingBar(true);
        row.setInService(true);
        row.setGripKind(GripType.DETACHABLE);
        row.setGripModel("AK6");
        row.setGripJaws(1);
        row.setGripSpringPack("blue");
        row.setGripRatedSlip(Load.ofPounds(2600));
        row.setGripInstalled(LocalDate.of(2019, 10, 4));
        return row;
    }

    @Test
    void theSchemaCarriesEveryPlantTable() {
        assertThat(database.existingTables())
                .contains(
                        "lifts",
                        "terminals",
                        "brakes",
                        "towers",
                        "carriers",
                        "survey_shots",
                        "rope_specs",
                        "haul_ropes");
    }

    @Test
    void aLiftAndItsChildrenGoInTogether() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            lift.addTerminal(terminalRow());
            lift.addTower(towerRow(1));
            lift.addCarrier(carrierRow(1));
            lift.addShot(new SurveyShotRow(Length.ofFeet(0), Length.ofFeet(6400)));
            session.persist(lift);
            session.commit();
        }
        try (Session session = database.session()) {
            LiftRow found = session.find(LiftRow.class, JP).orElseThrow();
            assertThat(found.getTerminals()).hasSize(1);
            assertThat(found.getTowers()).hasSize(1);
            assertThat(found.getCarriers()).hasSize(1);
            assertThat(found.getShots()).hasSize(1);
        }
    }

    @Test
    void theMeasuresComeBackAsMeasures() {
        try (Session session = database.session()) {
            session.persist(johnPaul());
            session.commit();
        }
        try (Session session = database.session()) {
            LiftRow found = session.find(LiftRow.class, JP).orElseThrow();
            assertThat(found.getDesignSpeed()).isEqualTo(Speed.ofFeetPerMinute(1000));
            assertThat(found.getBearing()).isEqualTo(Bearing.ofDegrees(72));
            assertThat(found.getCommissioned()).isEqualTo(LocalDate.of(2004, 12, 3));
        }
    }

    @Test
    void aTowerWithoutALiftIsRefusedByTheDatabase() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(towerRow(1));
                session.commit();
            });
        }
    }

    @Test
    void aLiftAndATowerAddedTogetherInsertInTheRightOrder() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            TowerRow tower = towerRow(1);
            lift.addTower(tower);
            session.persist(tower);
            session.persist(lift);
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(session.entityManager()
                            .createQuery("select t from TowerRow t where t.number = 1", TowerRow.class)
                            .getResultList())
                    .hasSize(1);
        }
    }

    @Test
    void twoTowersCannotShareANumberOnOneLine() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            lift.addTower(towerRow(3));
            lift.addTower(towerRow(3));
            assertRefused(() -> {
                session.persist(lift);
                session.commit();
            });
        }
    }

    @Test
    void twoCarriersCannotShareANumberOnOneFleet() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            lift.addCarrier(carrierRow(7));
            lift.addCarrier(carrierRow(7));
            assertRefused(() -> {
                session.persist(lift);
                session.commit();
            });
        }
    }

    @Test
    void aTerminalCarriesOneBrakeOfEachKind() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            TerminalRow terminal = terminalRow();
            lift.addTerminal(terminal);
            terminal.addBrake(new BrakeRow(BrakeKind.SERVICE, Torque.ofPoundFeet(900)));
            terminal.addBrake(new BrakeRow(BrakeKind.SERVICE, Torque.ofPoundFeet(700)));
            assertRefused(() -> {
                session.persist(lift);
                session.commit();
            });
        }
    }

    @Test
    void aRopeHangsOffASpecAndMayHangOffALift() {
        try (Session session = database.session()) {
            HaulRopeRow rope = new HaulRopeRow(
                    RopeSerial.parse("FAT-2019-04412"), spec(), LocalDate.of(2019, 10, 4), Length.ofFeet(10132));
            rope.setLift(johnPaul());
            rope.setSplices(1);
            session.persist(rope);
            session.commit();
        }
        try (Session session = database.session()) {
            HaulRopeRow found = session.find(HaulRopeRow.class, RopeSerial.parse("FAT-2019-04412"))
                    .orElseThrow();
            assertThat(found.getSpec().getConstruction()).isSameAs(RopeConstruction.SIX_BY_TWENTY_FIVE);
            assertThat(found.getLift()).get().extracting(LiftRow::getCode).isEqualTo(JP);
        }
    }

    @Test
    void aSpareRopeSitsOnNoLift() {
        try (Session session = database.session()) {
            session.persist(new HaulRopeRow(
                    RopeSerial.parse("BRI-2021-00500"), spec(), LocalDate.of(2021, 9, 1), Length.ofFeet(4113)));
            session.commit();
        }
        try (Session session = database.session()) {
            HaulRopeRow found = session.find(HaulRopeRow.class, RopeSerial.parse("BRI-2021-00500"))
                    .orElseThrow();
            assertThat(found.getLift()).isEmpty();
        }
    }

    @Test
    void deletingALiftTakesItsTowersWithIt() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            lift.addTower(towerRow(1));
            session.persist(lift);
            session.commit();
        }
        try (Session session = database.session()) {
            session.remove(session.find(LiftRow.class, JP).orElseThrow());
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, TowerRow.class)).isEmpty();
        }
    }

    @Test
    void twoShotsCannotShareAStation() {
        try (Session session = database.session()) {
            LiftRow lift = johnPaul();
            lift.addShot(new SurveyShotRow(Length.ofFeet(600), Length.ofFeet(6496)));
            lift.addShot(new SurveyShotRow(Length.ofFeet(600), Length.ofFeet(6500)));
            assertRefused(() -> {
                session.persist(lift);
                session.commit();
            });
        }
    }

    @Test
    void enumsAreStoredByNameRatherThanBySentence() {
        try (Session session = database.session()) {
            session.persist(johnPaul());
            session.commit();
            Object stored = session.entityManager()
                    .createNativeQuery("select lift_class from lifts")
                    .getSingleResult();
            assertThat(stored).isEqualTo("DETACHABLE_CHAIR");
        }
    }

    @Test
    void theEnumConverterNamesTheTypeItStoresInPlainText() {
        NamedEnum<LiftClass> made = new NamedEnum.LiftClasses();
        assertThat(made.type()).isEqualTo(LiftClass.class);
        assertThat(made.convertToDatabaseColumn(LiftClass.DETACHABLE_CHAIR)).isEqualTo("DETACHABLE_CHAIR");
        assertThat(made.convertToEntityAttribute("DETACHABLE_CHAIR")).isSameAs(LiftClass.DETACHABLE_CHAIR);
        try (Session session = database.session()) {
            Object[] column = (Object[]) session.entityManager()
                    .createNativeQuery("select data_type, character_maximum_length from information_schema.columns"
                            + " where table_name = 'LIFTS' and column_name = 'LIFT_CLASS'")
                    .getSingleResult();
            assertThat(column).containsExactly("CHARACTER VARYING", 32L);
        }
    }
}
