package io.github.rkaushikdev.sheaveline.persistence.mappers;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.one;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.persistence.models.BreakSurveyRow;
import io.github.rkaushikdev.sheaveline.persistence.models.MagneticTestRow;
import io.github.rkaushikdev.sheaveline.persistence.models.SpliceRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TensionReadingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TensionSystemRow;
import io.github.rkaushikdev.sheaveline.persistence.models.WearSurveyRow;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
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
import io.github.rkaushikdev.sheaveline.ropework.Verdict;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapperRopeworkTest {

    private static final Lift LIFT = Hill.johnPaul();
    private static final HaulRope ROPE = LIFT.rope();
    private static final LocalDate TAKEN = LocalDate.of(2025, 1, 15);

    private Database database;

    @BeforeEach
    void johnPaulAndHisRope() {
        database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(PlantMapper.liftToRow(LIFT));
            session.persist(PlantMapper.ropeToRow(ROPE, LIFT.code()));
            session.commit();
        }
    }

    private static HaulRope otherRope() {
        return new HaulRope(RopeSerial.parse("BRI-2011-01887"), ROPE.spec(), ROPE.installed(), ROPE.length(), 1);
    }

    private static WearSurvey wearSurvey() {
        return WearSurvey.of(
                ROPE,
                TAKEN,
                "R Halvorsen",
                List.of(
                        new DiameterReading(Length.ofFeet(0), Length.ofInches("1.376"), Length.ofInches("1.374")),
                        new DiameterReading(Length.ofFeet(1200), Length.ofInches("1.352"), Length.ofInches("1.348"))),
                List.of(new LayReading(Length.ofFeet(1200), Length.ofInches("9.10"))));
    }

    private static List<Splice> splices(boolean both) {
        Length longSplice = ROPE.diameter().scaled(1200);
        Splice mill = new Splice(Length.ofFeet(0), LocalDate.of(2019, 10, 4), "B Kettner", 5, longSplice, true);
        Splice field = new Splice(Length.ofFeet(4000), LocalDate.of(2022, 6, 1), "B Kettner", 5, longSplice, false);
        return both ? List.of(mill, field) : List.of(mill);
    }

    private void write(Object row) {
        try (Session session = database.session()) {
            session.persist(row);
            session.commit();
        }
    }

    @Test
    void aWearSurveyRoundTripsThroughTheDatabase() {
        WearSurvey before = wearSurvey();
        write(RopeworkMapper.wearToRow(before));
        WearSurvey after;
        try (Session session = database.session()) {
            after = RopeworkMapper.wearFromRow(one(session, WearSurveyRow.class), ROPE);
        }
        assertThat(after.taken()).isEqualTo(before.taken());
        assertThat(after.inspector()).isEqualTo(before.inspector());
        assertThat(after.diameters()).hasSize(2);
        assertThat(after.lays()).hasSize(1);
        assertThat(after.smallest().mean()).isEqualTo(before.smallest().mean());
        assertThat(Verdict.assessWear(after)).isSameAs(Verdict.assessWear(before));
    }

    @Test
    void aWearSurveyWillNotLoadAgainstTheWrongRope() {
        write(RopeworkMapper.wearToRow(wearSurvey()));
        try (Session session = database.session()) {
            WearSurveyRow row = one(session, WearSurveyRow.class);
            assertThatThrownBy(() -> RopeworkMapper.wearFromRow(row, otherRope()))
                    .isInstanceOf(StorageError.class)
                    .hasMessage("that row is against FAT-2019-04412, not BRI-2011-01887");
        }
    }

    @Test
    void aBreakSurveyRoundTrips() {
        BreakSurvey before = BreakSurvey.of(
                ROPE,
                TAKEN,
                "R Halvorsen",
                List.of(
                        new BreakObservation(Length.ofFeet(1200), 4, 7, 1, false),
                        new BreakObservation(Length.ofFeet(2400), 2, 3)));
        write(RopeworkMapper.breaksToRow(before));
        BreakSurvey after;
        try (Session session = database.session()) {
            after = RopeworkMapper.breaksFromRow(one(session, BreakSurveyRow.class), ROPE);
        }
        assertThat(after.totalBreaks()).isEqualTo(before.totalBreaks());
        assertThat(Verdict.assessBreaks(after)).isSameAs(Verdict.assessBreaks(before));
        assertThat(after.observations().get(0).valleyBreaks()).isEqualTo(1);
    }

    @Test
    void aMagneticTestRoundTrips() {
        MagneticTest before = MagneticTest.of(
                ROPE,
                TAKEN,
                "Wire Rope Diagnostics",
                "LMA-250",
                List.of(Indication.loss(Length.ofFeet(1800), "7.4"), Indication.fault(Length.ofFeet(2400), 3)));
        write(RopeworkMapper.magneticToRow(before));
        MagneticTest after;
        try (Session session = database.session()) {
            after = RopeworkMapper.magneticFromRow(one(session, MagneticTestRow.class), ROPE);
        }
        assertThat(after.contractor()).isEqualTo(before.contractor());
        assertThat(after.instrument()).isEqualTo(before.instrument());
        assertThat(after.peakLoss()).isEqualByComparingTo(before.peakLoss());
        assertThat(after.largestFault()).isEqualTo(before.largestFault());
    }

    @Test
    void aMagneticTestWillNotLoadAgainstTheWrongRope() {
        write(RopeworkMapper.magneticToRow(MagneticTest.of(ROPE, TAKEN, "Wire Rope Diagnostics", "LMA-250")));
        try (Session session = database.session()) {
            MagneticTestRow row = one(session, MagneticTestRow.class);
            assertThatThrownBy(() -> RopeworkMapper.magneticFromRow(row, otherRope()))
                    .isInstanceOf(StorageError.class);
        }
    }

    @Test
    void aSpliceRegisterRoundTrips() {
        SpliceRegister before = SpliceRegister.of(ROPE, splices(true));
        try (Session session = database.session()) {
            RopeworkMapper.splicesToRows(before).forEach(session::persist);
            session.commit();
        }
        SpliceRegister after;
        try (Session session = database.session()) {
            after = RopeworkMapper.splicesFromRows(all(session, SpliceRow.class), ROPE);
        }
        assertThat(after.count()).isEqualTo(2);
        assertThat(after.fieldSplices()).hasSize(1);
        assertThat(after.isAcceptable()).isEqualTo(before.isAcceptable());
    }

    @Test
    void aSpliceRegisterWillNotLoadAgainstTheWrongRope() {
        SpliceRegister before = SpliceRegister.of(ROPE, splices(false));
        try (Session session = database.session()) {
            RopeworkMapper.splicesToRows(before).forEach(session::persist);
            session.commit();
        }
        try (Session session = database.session()) {
            List<SpliceRow> rows = all(session, SpliceRow.class);
            assertThatThrownBy(() -> RopeworkMapper.splicesFromRows(rows, otherRope()))
                    .isInstanceOf(StorageError.class);
        }
    }

    @Test
    void aTensionSystemAndItsReadingsRoundTrip() {
        TensionSystem system =
                new TensionSystem(TensionKind.HYDRAULIC, Load.ofPounds(34500), Length.ofFeet(16), Length.ofFeet(6));
        TensionReading reading =
                new TensionReading(TAKEN, Length.ofFeet(7), Load.ofPounds(34800), Temperature.ofDegreesF(14));
        try (Session session = database.session()) {
            session.persist(RopeworkMapper.tensionToRow(system, LIFT.code()));
            session.persist(RopeworkMapper.readingToRow(reading, LIFT.code()));
            session.commit();
        }
        try (Session session = database.session()) {
            TensionSystemRow systemRow =
                    session.find(TensionSystemRow.class, LIFT.code()).orElseThrow();
            assertThat(RopeworkMapper.tensionFromRow(systemRow)).isEqualTo(system);
            TensionReadingRow readingRow = one(session, TensionReadingRow.class);
            assertThat(RopeworkMapper.readingFromRow(readingRow)).isEqualTo(reading);
        }
    }

    @Test
    void theReadingsHangOffTheSystemTheyBelongTo() {
        TensionSystem system =
                new TensionSystem(TensionKind.COUNTERWEIGHT, Load.ofPounds(34500), Length.ofFeet(16), Length.ofFeet(6));
        try (Session session = database.session()) {
            session.persist(RopeworkMapper.tensionToRow(system, LIFT.code()));
            session.persist(RopeworkMapper.readingToRow(
                    new TensionReading(TAKEN, Length.ofFeet(7), Load.ofPounds(34400), Temperature.ofDegreesF(14)),
                    LIFT.code()));
            session.commit();
        }
        try (Session session = database.session()) {
            TensionSystemRow found =
                    session.find(TensionSystemRow.class, LIFT.code()).orElseThrow();
            assertThat(found.getReadings()).hasSize(1);
            assertThat(RopeworkMapper.tensionFromRow(found).kind()).isSameAs(TensionKind.COUNTERWEIGHT);
        }
    }
}
