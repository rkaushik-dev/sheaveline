package io.github.rkaushikdev.sheaveline.persistence.mappers;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.persistence.models.LiftRow;
import io.github.rkaushikdev.sheaveline.persistence.models.SurveyShotRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TerminalRow;
import io.github.rkaushikdev.sheaveline.plant.Brake;
import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.DriveTrain;
import io.github.rkaushikdev.sheaveline.plant.GroundPoint;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.RopeSpec;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MapperPlantTest {

    private static Lift saveAndLoad(Lift lift) {
        Database database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(PlantMapper.liftToRow(lift));
            session.persist(PlantMapper.ropeToRow(lift.rope(), lift.code()));
            session.commit();
        }
        try (Session session = database.session()) {
            LiftRow row = session.find(LiftRow.class, lift.code()).orElseThrow();
            HaulRope rope = PlantMapper.ropeFromRow(row.getRopes().get(0));
            return PlantMapper.liftFromRow(row, rope);
        }
    }

    @Test
    void aRopeSpecRoundTrips() {
        RopeSpec spec = johnPaul().rope().spec();
        assertThat(PlantMapper.specFromRow(PlantMapper.specToRow(spec))).isEqualTo(spec);
    }

    @Test
    void aRopeRoundTrips() {
        HaulRope rope = johnPaul().rope();
        assertThat(PlantMapper.ropeFromRow(PlantMapper.ropeToRow(rope))).isEqualTo(rope);
    }

    @Test
    void theQuadComesBackTheWayItWentIn() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        assertThat(after.code()).isEqualTo(before.code());
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.liftClass()).isSameAs(before.liftClass());
        assertThat(after.bearing()).isEqualTo(before.bearing());
        assertThat(after.designSpeed()).isEqualTo(before.designSpeed());
        assertThat(after.commissioned()).isEqualTo(before.commissioned());
    }

    @Test
    void theTerminalsComeBackTheRightWayUp() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        assertThat(after.bottom().label()).isEqualTo(before.bottom().label());
        assertThat(after.top().label()).isEqualTo(before.top().label());
        assertThat(after.bottom().role()).isSameAs(before.bottom().role());
        assertThat(after.top().tensionTravel()).isEqualTo(before.top().tensionTravel());
    }

    @Test
    void theDriveTrainComesBackWithItsBrakes() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        DriveTrain drive = after.driveTerminal().driveTrain();
        DriveTrain original = before.driveTerminal().driveTrain();
        assertThat(drive.ratedHorsepower()).isEqualTo(original.ratedHorsepower());
        assertThat(drive.gearboxRatio()).isEqualByComparingTo(original.gearboxRatio());
        assertThat(drive.auxiliary()).isEqualTo(original.auxiliary());
        assertThat(drive.brakes().stream().map(Brake::kind).collect(Collectors.toSet()))
                .isEqualTo(original.brakes().stream().map(Brake::kind).collect(Collectors.toSet()));
        Brake first = original.brakes().get(0);
        assertThat(drive.brake(first.kind()).torque()).isEqualTo(first.torque());
    }

    @Test
    void theReturnTerminalComesBackWithNoDrive() {
        Lift after = saveAndLoad(johnPaul());
        assertThat(after.top().drive()).isEmpty();
    }

    @Test
    void everyTowerComesBackWithBothAssemblies() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        List<Tower> was = before.towers().towers();
        List<Tower> now = after.towers().towers();
        assertThat(now).hasSameSizeAs(was);
        for (int at = 0; at < was.size(); at++) {
            assertThat(now.get(at).ref()).isEqualTo(was.get(at).ref());
            assertThat(now.get(at).station()).isEqualTo(was.get(at).station());
            assertThat(now.get(at).height()).isEqualTo(was.get(at).height());
            assertThat(now.get(at).uphill()).isEqualTo(was.get(at).uphill());
            assertThat(now.get(at).downhill()).isEqualTo(was.get(at).downhill());
        }
    }

    @Test
    void everyCarrierComesBackWithItsGrip() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        assertThat(after.carriers().size()).isEqualTo(before.carriers().size());
        Carrier was = before.carriers().carriers().get(7);
        Carrier now = after.carriers().carriers().get(7);
        assertThat(now).isEqualTo(was);
        assertThat(now.gripOf().ratedSlip()).isEqualTo(was.gripOf().ratedSlip());
    }

    @Test
    void aSurfaceCarrierComesBackWithNoGrip() {
        Lift after = saveAndLoad(wildcat());
        assertThat(after.carriers().carriers())
                .allSatisfy(carrier -> assertThat(carrier.grip()).isEmpty());
    }

    @Test
    void theSurveyComesBackShotForShot() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        assertThat(after.ground().points().stream().map(GroundPoint::station).toList())
                .isEqualTo(before.ground().points().stream()
                        .map(GroundPoint::station)
                        .toList());
        GroundPoint fourth = before.ground().points().get(3);
        assertThat(after.ground().elevationAt(fourth.station())).isEqualTo(fourth.elevation());
    }

    @Test
    void theReloadedLiftStillAnswersItsOwnQuestions() {
        Lift before = johnPaul();
        Lift after = saveAndLoad(before);
        assertThat(after.slopeLength()).isEqualTo(before.slopeLength());
        assertThat(after.expectedRopeLength()).isEqualTo(before.expectedRopeLength());
        assertThat(after.ropeLengthAgrees()).isTrue();
        assertThat(after.spans().size()).isEqualTo(before.spans().size());
    }

    @Test
    void theTripleAndTheTowRoundTripAsWell() {
        for (Lift lift : List.of(strawberry(), wildcat())) {
            Lift after = saveAndLoad(lift);
            assertThat(after.code()).isEqualTo(lift.code());
            assertThat(after.liftClass()).isSameAs(lift.liftClass());
            assertThat(after.carriersOnLine()).isEqualTo(lift.carriersOnLine());
        }
    }

    @Test
    void aLiftRowWithOneTerminalIsRefused() {
        Lift lift = johnPaul();
        LiftRow row = new LiftRow(
                lift.code(), lift.name(), lift.liftClass(), lift.bearing(), lift.designSpeed(), lift.commissioned());
        row.addTerminal(PlantMapper.terminalToRow(lift.bottom()));
        lift.towers().forEach(tower -> row.addTower(PlantMapper.towerToRow(tower)));
        lift.carriers().forEach(carrier -> row.addCarrier(PlantMapper.carrierToRow(carrier)));
        lift.ground().points().forEach(point -> row.addShot(new SurveyShotRow(point.station(), point.elevation())));
        assertThatThrownBy(() -> PlantMapper.liftFromRow(row, lift.rope()))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP has 1 terminals, not two");
    }

    @Test
    void aCarrierRowWithHalfAGripIsRefused() {
        Lift lift = johnPaul();
        LiftRow row = PlantMapper.liftToRow(lift);
        row.getCarriers().get(0).setGripModel(null);
        assertThatThrownBy(() -> PlantMapper.liftFromRow(row, lift.rope()))
                .isInstanceOf(StorageError.class)
                .hasMessage("carrier 1 has half a grip");
    }

    @Test
    void aTerminalRowWithADriveTypeAndNoRatingIsRefused() {
        Lift lift = johnPaul();
        LiftRow row = PlantMapper.liftToRow(lift);
        for (TerminalRow terminal : row.getTerminals()) {
            if (terminal.getDriveType().isPresent()) {
                terminal.setRatedHorsepower(null);
            }
        }
        assertThatThrownBy(() -> PlantMapper.liftFromRow(row, lift.rope()))
                .isInstanceOf(StorageError.class)
                .hasMessageEndingWith("has a drive type and no rating");
    }
}
