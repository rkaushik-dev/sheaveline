package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.one;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.evac.Drill;
import io.github.rkaushikdev.sheaveline.evac.DrillKind;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Wind;
import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.people.Certificate;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.persistence.mappers.EvacMapper;
import io.github.rkaushikdev.sheaveline.persistence.mappers.OpsMapper;
import io.github.rkaushikdev.sheaveline.persistence.mappers.PeopleMapper;
import io.github.rkaushikdev.sheaveline.persistence.mappers.PlantMapper;
import io.github.rkaushikdev.sheaveline.persistence.mappers.RopeworkMapper;
import io.github.rkaushikdev.sheaveline.plant.AlignmentSurvey;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.plant.TowerShot;
import io.github.rkaushikdev.sheaveline.ropework.BreakObservation;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.Indication;
import io.github.rkaushikdev.sheaveline.ropework.LayReading;
import io.github.rkaushikdev.sheaveline.ropework.MagneticTest;
import io.github.rkaushikdev.sheaveline.ropework.Splice;
import io.github.rkaushikdev.sheaveline.ropework.SpliceRegister;
import io.github.rkaushikdev.sheaveline.ropework.TensionKind;
import io.github.rkaushikdev.sheaveline.ropework.TensionReading;
import io.github.rkaushikdev.sheaveline.ropework.TensionSystem;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import io.github.rkaushikdev.sheaveline.weather.Precipitation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every kind of row the department writes, written once and read back in a session of its own, so
 * that each row is seen to carry its generated key and to point back at the row it hangs off.
 */
class RowLinksTest {

    private static final Lift LIFT = Hill.johnPaul();
    private static final LiftCode JP = LIFT.code();
    private static final RopeSerial SERIAL = LIFT.rope().serial();
    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);
    private static final FilingNumber FILING = FilingNumber.parse("UT-2025-0001");

    private Database database;

    @BeforeEach
    void writeOneOfEverything() {
        database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(PlantMapper.liftToRow(LIFT));
            session.persist(PlantMapper.ropeToRow(LIFT.rope(), JP));
            writeTheDay(session);
            writeTheRopeFile(session);
            writeThePaperwork(session);
            session.commit();
        }
    }

    private static void writeTheDay(Session session) {
        List<CheckResult> results = new ArrayList<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(LIFT)) {
                results.add(new CheckResult(item, CheckOutcome.PASS, "R Skaggs"));
            }
        }
        DayLog day = DayLog.start(
                        JP,
                        DAY,
                        OperatingWindow.parse("09:00", "16:00", "16:30"),
                        new PreOpSheet(LIFT, DAY, results, "R Skaggs", "D Chin"))
                .note(TimeOfDay.parse("08:40"), "line ridden, all clear", "R Skaggs");
        session.persist(OpsMapper.dayLogToRow(day));
        session.persist(OpsMapper.stopToRow(new StopEvent(JP, StopKind.OPERATOR, DAY.atTime(11, 30), "D Chin")));
        session.persist(OpsMapper.holdToRow(new HoldRecord(JP, HoldReason.GUST, DAY.atTime(10, 0), "D Chin")));
        session.persist(
                OpsMapper.meterToRow(new MeterReading(JP, DAY, new BigDecimal("40120.5"), 1_402_000, "R Skaggs")));
        session.persist(PeopleMapper.assignmentToRow(
                JP, DAY, new Assignment("D Chin", Post.BOTTOM, TimeOfDay.parse("08:30"), TimeOfDay.parse("16:45"))));
        session.persist(PeopleMapper.certificateToRow(new Certificate(
                "D Chin",
                Endorsement.OPERATOR,
                LocalDate.of(2024, 11, 1),
                LocalDate.of(2025, 11, 1),
                "Utah tramway board",
                Set.of(LiftClass.DETACHABLE_CHAIR))));
        session.persist(EvacMapper.drillToRow(Drill.of(
                JP, DAY, DrillKind.FULL_LINE, List.of("alpha", "bravo"), 6, Duration.ofMinutes(48), "R Skaggs")));
    }

    private static void writeTheRopeFile(Session session) {
        session.persist(RopeworkMapper.wearToRow(WearSurvey.of(
                LIFT.rope(),
                DAY,
                "R Halvorsen",
                List.of(new DiameterReading(Length.ofFeet(0), Length.ofInches("1.376"), Length.ofInches("1.374"))),
                List.of(new LayReading(Length.ofFeet(0), Length.ofInches("9.10"))))));
        session.persist(RopeworkMapper.breaksToRow(BreakSurvey.of(
                LIFT.rope(), DAY, "R Halvorsen", List.of(new BreakObservation(Length.ofFeet(1200), 2, 3)))));
        session.persist(RopeworkMapper.magneticToRow(MagneticTest.of(
                LIFT.rope(),
                DAY,
                "Wire Rope Diagnostics",
                "LMA-250",
                List.of(Indication.loss(Length.ofFeet(1800), "7.4")))));
        Length longSplice = LIFT.rope().diameter().scaled(1200);
        RopeworkMapper.splicesToRows(SpliceRegister.of(
                        LIFT.rope(),
                        List.of(new Splice(
                                Length.ofFeet(0), LocalDate.of(2019, 10, 4), "B Kettner", 5, longSplice, true))))
                .forEach(session::persist);
        session.persist(RopeworkMapper.tensionToRow(
                new TensionSystem(TensionKind.HYDRAULIC, Load.ofPounds(34500), Length.ofFeet(16), Length.ofFeet(6)),
                JP));
        session.persist(RopeworkMapper.readingToRow(
                new TensionReading(DAY, Length.ofFeet(7), Load.ofPounds(34800), Temperature.ofDegreesF(14)), JP));
        Tower four = LIFT.towers().tower(4);
        session.persist(PlantMapper.alignmentToRow(AlignmentSurvey.of(
                LIFT.towers(),
                DAY,
                "H Ogilvie",
                List.of(new TowerShot(four.ref(), four.station(), four.crossarmElevation(), Length.ofInches(1))))));
    }

    private static void writeThePaperwork(Session session) {
        StationRow summit = new StationRow("summit", Length.ofFeet(7981));
        session.persist(summit);
        Wind wind = Wind.parse("W 18");
        ObservationRow reading = new ObservationRow(
                "summit",
                DAY.atTime(12, 0),
                wind.speed(),
                wind.bearing(),
                Temperature.ofDegreesF(8),
                Length.ofFeet(30000),
                Precipitation.SNOW);
        summit.addObservation(reading);
        session.persist(reading);
        InspectionRecordRow walked = new InspectionRecordRow(JP, "W1", DAY, "M Vetter");
        walked.addDefect(new DefectRow("JP-T4", DefectLevel.DEFECT, "sheave liner cracked", DAY));
        session.persist(walked);
        session.persist(
                new FilingRow(FILING, FilingKind.INCIDENT, JP, DAY.atTime(15, 0), "chair 34 off at T4", "K Ostler"));
        IncidentRow incident = new IncidentRow(
                JP, DAY.atTime(11, 30), IncidentKind.DEROPEMENT, InjuryLevel.NONE, 0, "chair 34 off at T4", "K Ostler");
        incident.setFilingNumber(FILING);
        session.persist(incident);
    }

    @Test
    void everyRowWithAGeneratedKeyCarriesItOnceWritten() {
        try (Session session = database.session()) {
            List<Class<?>> generated = List.of(
                    TerminalRow.class,
                    BrakeRow.class,
                    TowerRow.class,
                    CarrierRow.class,
                    SurveyShotRow.class,
                    RopeSpecRow.class,
                    DayLogRow.class,
                    LogEntryRow.class,
                    PreOpResultRow.class,
                    StopRow.class,
                    HoldRow.class,
                    MeterReadingRow.class,
                    AssignmentRow.class,
                    CertificateRow.class,
                    CertificateClassRow.class,
                    DrillRow.class,
                    DrillTeamRow.class,
                    WearSurveyRow.class,
                    DiameterReadingRow.class,
                    LayReadingRow.class,
                    BreakSurveyRow.class,
                    BreakObservationRow.class,
                    MagneticTestRow.class,
                    IndicationRow.class,
                    SpliceRow.class,
                    TensionReadingRow.class,
                    AlignmentSurveyRow.class,
                    TowerShotRow.class,
                    ObservationRow.class,
                    InspectionRecordRow.class,
                    DefectRow.class,
                    IncidentRow.class);
            for (Class<?> kind : generated) {
                List<?> rows = all(session, kind);
                assertThat(rows).as(kind.getSimpleName()).isNotEmpty();
                assertThat(rows)
                        .as(kind.getSimpleName())
                        .allSatisfy(row -> assertThat(row).extracting("id").isNotNull());
            }
        }
    }

    @Test
    void theMachinesPartsPointBackAtIt() {
        try (Session session = database.session()) {
            assertThat(all(session, TerminalRow.class)).allSatisfy(row -> {
                assertThat(row.getLiftCode()).isEqualTo(JP);
                assertThat(row.getLift().getCode()).isEqualTo(JP);
            });
            assertThat(all(session, BrakeRow.class))
                    .allSatisfy(
                            row -> assertThat(row.getTerminal().getDriveType()).isPresent());
            assertThat(all(session, TowerRow.class))
                    .allSatisfy(row -> assertThat(row.getLift().getName()).isEqualTo("John Paul"));
            assertThat(all(session, CarrierRow.class)).allSatisfy(row -> {
                assertThat(row.getLiftCode()).isEqualTo(JP);
                assertThat(row.getLift().getCode()).isEqualTo(JP);
            });
            assertThat(all(session, SurveyShotRow.class)).allSatisfy(row -> {
                assertThat(row.getLiftCode()).isEqualTo(JP);
                assertThat(row.getLift().getCode()).isEqualTo(JP);
            });
            AlignmentSurveyRow survey = one(session, AlignmentSurveyRow.class);
            assertThat(survey.getLift().getCode()).isEqualTo(JP);
            TowerShotRow shot = one(session, TowerShotRow.class);
            assertThat(shot.getSurvey().getTaken()).isEqualTo(DAY);
            assertThat(shot.getTowerRef()).isEqualTo(JP.tower(4));
        }
    }

    @Test
    void theDaysRowsPointBackAtTheirLogAndTheirMachine() {
        try (Session session = database.session()) {
            assertThat(one(session, LogEntryRow.class).getDayLog().getDay()).isEqualTo(DAY);
            assertThat(all(session, PreOpResultRow.class))
                    .allSatisfy(row -> assertThat(row.getDayLog().getLiftCode()).isEqualTo(JP));
            assertThat(one(session, StopRow.class).getLift().getCode()).isEqualTo(JP);
            assertThat(one(session, HoldRow.class).getLift().getCode()).isEqualTo(JP);
            assertThat(one(session, MeterReadingRow.class).getLift().getCode()).isEqualTo(JP);
            assertThat(one(session, AssignmentRow.class).getLift().getCode()).isEqualTo(JP);
            assertThat(one(session, CertificateClassRow.class).getCertificate().getPerson())
                    .isEqualTo("D Chin");
            assertThat(one(session, DrillRow.class).getLift().getCode()).isEqualTo(JP);
            assertThat(all(session, DrillTeamRow.class))
                    .allSatisfy(row -> assertThat(row.getDrill().getOn()).isEqualTo(DAY));
        }
    }

    @Test
    void theRopeFilePointsBackAtTheRope() {
        try (Session session = database.session()) {
            assertThat(one(session, WearSurveyRow.class).getRope().getSerial()).isEqualTo(SERIAL);
            assertThat(one(session, DiameterReadingRow.class).getSurvey().getRopeSerial())
                    .isEqualTo(SERIAL);
            assertThat(one(session, LayReadingRow.class).getSurvey().getRopeSerial())
                    .isEqualTo(SERIAL);
            assertThat(one(session, BreakSurveyRow.class).getRope().getSerial()).isEqualTo(SERIAL);
            assertThat(one(session, BreakObservationRow.class).getSurvey().getRopeSerial())
                    .isEqualTo(SERIAL);
            assertThat(one(session, MagneticTestRow.class).getRope().getSerial())
                    .isEqualTo(SERIAL);
            assertThat(one(session, IndicationRow.class).getTest().getInstrument())
                    .isEqualTo("LMA-250");
            assertThat(one(session, SpliceRow.class).getRope().getSerial()).isEqualTo(SERIAL);
            TensionReadingRow reading = one(session, TensionReadingRow.class);
            assertThat(reading.getLiftCode()).isEqualTo(JP);
            assertThat(reading.getSystem().getKind()).isSameAs(TensionKind.HYDRAULIC);
        }
    }

    @Test
    void thePaperworkPointsBackAtWhatItIsAbout() {
        try (Session session = database.session()) {
            ObservationRow reading = one(session, ObservationRow.class);
            assertThat(reading.getStationName()).isEqualTo("summit");
            assertThat(reading.getStation().getObservations()).containsExactly(reading);
            DefectRow defect = one(session, DefectRow.class);
            assertThat(defect.getRecord().getLift().getCode()).isEqualTo(JP);
            assertThat(defect.getRecord().getDefects()).containsExactly(defect);
            IncidentRow incident = one(session, IncidentRow.class);
            assertThat(incident.getLift().getCode()).isEqualTo(JP);
            assertThat(incident.getFiling()).hasValueSatisfying(filing -> {
                assertThat(filing.getNumber()).isEqualTo(FILING);
                assertThat(filing.getLift().getCode()).isEqualTo(JP);
                assertThat(filing.getIncidents()).containsExactly(incident);
            });
        }
    }
}
