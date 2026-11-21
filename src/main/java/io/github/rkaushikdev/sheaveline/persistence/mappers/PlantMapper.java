package io.github.rkaushikdev.sheaveline.persistence.mappers;

import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.models.AlignmentSurveyRow;
import io.github.rkaushikdev.sheaveline.persistence.models.BrakeRow;
import io.github.rkaushikdev.sheaveline.persistence.models.CarrierRow;
import io.github.rkaushikdev.sheaveline.persistence.models.HaulRopeRow;
import io.github.rkaushikdev.sheaveline.persistence.models.LiftRow;
import io.github.rkaushikdev.sheaveline.persistence.models.RopeSpecRow;
import io.github.rkaushikdev.sheaveline.persistence.models.SurveyShotRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TerminalRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TowerRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TowerShotRow;
import io.github.rkaushikdev.sheaveline.plant.AlignmentSurvey;
import io.github.rkaushikdev.sheaveline.plant.Brake;
import io.github.rkaushikdev.sheaveline.plant.Bullwheel;
import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import io.github.rkaushikdev.sheaveline.plant.DriveTrain;
import io.github.rkaushikdev.sheaveline.plant.Grip;
import io.github.rkaushikdev.sheaveline.plant.GroundPoint;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.LineProfile;
import io.github.rkaushikdev.sheaveline.plant.RopeSpec;
import io.github.rkaushikdev.sheaveline.plant.SheaveAssembly;
import io.github.rkaushikdev.sheaveline.plant.Terminal;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.plant.TowerLine;
import io.github.rkaushikdev.sheaveline.plant.TowerShot;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turning the installation into rows and back again.
 *
 * <p>Both directions are written here, next to each other, because the only way this stays honest
 * is if the two are read together. A field added to the domain and not to the reader is a field that
 * silently becomes a default the next time anything is loaded, and that shows up months later as a
 * tower whose deviation is nought.
 *
 * <p>The rope is mapped separately from the lift on purpose. It has its own life, it outlives the
 * machine it is on, and the loader takes it as an argument rather than digging it out of the lift
 * row.
 */
public final class PlantMapper {

    private PlantMapper() {}

    /**
     * A rope specification as a row.
     *
     * @param spec the specification
     * @return a new row
     */
    public static RopeSpecRow specToRow(RopeSpec spec) {
        return new RopeSpecRow(
                spec.construction(),
                spec.grade(),
                spec.lay(),
                spec.nominalDiameter(),
                spec.breakingStrength(),
                spec.weight());
    }

    /**
     * A rope specification back out of its row.
     *
     * @param row the row
     * @return the specification
     */
    public static RopeSpec specFromRow(RopeSpecRow row) {
        return new RopeSpec(
                row.getConstruction(),
                row.getGrade(),
                row.getLay(),
                row.getNominalDiameter(),
                row.getBreakingStrength(),
                row.getWeight());
    }

    /**
     * A spare rope as a row, on no machine.
     *
     * @param rope the rope
     * @return a new row, with a new specification row
     */
    public static HaulRopeRow ropeToRow(HaulRope rope) {
        HaulRopeRow row = new HaulRopeRow(rope.serial(), specToRow(rope.spec()), rope.installed(), rope.length());
        row.setSplices(rope.splices());
        return row;
    }

    /**
     * A rope as a row on a machine.
     *
     * @param rope the rope
     * @param liftCode the machine it is on
     * @return a new row, with a new specification row
     */
    public static HaulRopeRow ropeToRow(HaulRope rope, LiftCode liftCode) {
        HaulRopeRow row = ropeToRow(rope);
        row.setLiftCode(liftCode);
        return row;
    }

    /**
     * A rope back out of its row.
     *
     * @param row the row
     * @return the rope
     */
    public static HaulRope ropeFromRow(HaulRopeRow row) {
        return new HaulRope(
                row.getSerial(), specFromRow(row.getSpec()), row.getInstalled(), row.getLength(), row.getSplices());
    }

    /**
     * A terminal as a row, with a row for each brake on its drive. The machine fills in its code
     * when the terminal is added to it.
     *
     * @param terminal the terminal
     * @return a new row
     */
    public static TerminalRow terminalToRow(Terminal terminal) {
        TerminalRow row = new TerminalRow(
                terminal.role(),
                terminal.label(),
                terminal.station(),
                terminal.ropeElevation(),
                terminal.bullwheel().diameter(),
                terminal.bullwheel().liner());
        terminal.drive().ifPresent(drive -> {
            row.setDriveType(drive.driveType());
            row.setRatedHorsepower(drive.ratedHorsepower());
            row.setGearboxRatio(drive.gearboxRatio());
            row.setAuxiliary(drive.auxiliary().orElse(null));
            for (Brake brake : drive.brakes()) {
                BrakeRow fitted = new BrakeRow(brake.kind(), brake.torque());
                fitted.setHydraulic(brake.hydraulic());
                row.addBrake(fitted);
            }
        });
        row.setTensionTravel(terminal.tensionTravel().orElse(null));
        return row;
    }

    /**
     * A terminal back out of its row, its brakes in the order of their kinds.
     *
     * @param row the row
     * @return the terminal
     * @throws StorageError when the row names a drive and gives it no rating or no gearbox
     */
    public static Terminal terminalFromRow(TerminalRow row) {
        Optional<DriveTrain> drive = Optional.empty();
        if (row.getDriveType().isPresent()) {
            if (row.getRatedHorsepower().isEmpty() || row.getGearboxRatio().isEmpty()) {
                throw new StorageError(row.getLabel() + " has a drive type and no rating");
            }
            List<Brake> brakes = row.getBrakes().stream()
                    .sorted(Comparator.comparing(brake -> brake.getKind().name()))
                    .map(brake -> new Brake(brake.getKind(), brake.getTorque(), brake.isHydraulic()))
                    .toList();
            drive = Optional.of(new DriveTrain(
                    row.getDriveType().get(),
                    row.getRatedHorsepower().get(),
                    row.getGearboxRatio().get(),
                    brakes,
                    row.getAuxiliary()));
        }
        return new Terminal(
                row.getRole(),
                row.getLabel(),
                row.getStation(),
                row.getRopeElevation(),
                new Bullwheel(row.getBullwheelDiameter(), row.getBullwheelLiner()),
                drive,
                row.getTensionTravel());
    }

    /**
     * A tower as a row. The machine fills in its code when the tower is added to it.
     *
     * @param tower the tower
     * @return a new row
     */
    public static TowerRow towerToRow(Tower tower) {
        return new TowerRow(
                tower.number(),
                tower.station(),
                tower.baseElevation(),
                tower.height(),
                tower.uphill().kind(),
                tower.uphill().sheaves(),
                tower.uphill().deviation(),
                tower.downhill().kind(),
                tower.downhill().sheaves(),
                tower.downhill().deviation());
    }

    /**
     * A tower back out of its row.
     *
     * @param row the row
     * @return the tower
     */
    public static Tower towerFromRow(TowerRow row) {
        return new Tower(
                row.getLiftCode().tower(row.getNumber()),
                row.getStation(),
                row.getBaseElevation(),
                row.getHeight(),
                new SheaveAssembly(row.getUphillKind(), row.getUphillSheaves(), row.getUphillDeviation()),
                new SheaveAssembly(row.getDownhillKind(), row.getDownhillSheaves(), row.getDownhillDeviation()));
    }

    /**
     * A carrier as a row. The machine fills in its code when the carrier is added to it.
     *
     * @param carrier the carrier
     * @return a new row
     */
    public static CarrierRow carrierToRow(Carrier carrier) {
        CarrierRow row = new CarrierRow(carrier.number(), carrier.kind(), carrier.tare(), carrier.hanger());
        row.setRestrainingBar(carrier.restrainingBar());
        row.setInService(carrier.inService());
        carrier.grip().ifPresent(grip -> {
            row.setGripKind(grip.kind());
            row.setGripModel(grip.model());
            row.setGripJaws(grip.jaws());
            row.setGripSpringPack(grip.springPack());
            row.setGripRatedSlip(grip.ratedSlip());
            row.setGripInstalled(grip.installed());
        });
        return row;
    }

    /**
     * A carrier back out of its row.
     *
     * @param row the row
     * @return the carrier
     * @throws StorageError when the row names a grip and leaves part of it out
     */
    public static Carrier carrierFromRow(CarrierRow row) {
        Optional<Grip> grip = Optional.empty();
        if (row.getGripKind().isPresent()) {
            if (row.getGripModel().isEmpty()
                    || row.getGripJaws().isEmpty()
                    || row.getGripRatedSlip().isEmpty()
                    || row.getGripSpringPack().isEmpty()
                    || row.getGripInstalled().isEmpty()) {
                throw new StorageError("carrier " + row.getNumber() + " has half a grip");
            }
            grip = Optional.of(new Grip(
                    row.getGripKind().get(),
                    row.getGripModel().get(),
                    row.getGripJaws().get(),
                    row.getGripSpringPack().get(),
                    row.getGripRatedSlip().get(),
                    row.getGripInstalled().get()));
        }
        return new Carrier(
                row.getNumber(),
                row.getKind(),
                row.getTare(),
                row.getHanger(),
                grip,
                row.isRestrainingBar(),
                row.isInService());
    }

    /**
     * A machine as a row, with its terminals, towers, carriers and ground shots. The rope is not
     * part of it: it goes on file through {@link #ropeToRow(HaulRope, LiftCode)}.
     *
     * @param lift the machine
     * @return a new row
     */
    public static LiftRow liftToRow(Lift lift) {
        LiftRow row = new LiftRow(
                lift.code(), lift.name(), lift.liftClass(), lift.bearing(), lift.designSpeed(), lift.commissioned());
        row.addTerminal(terminalToRow(lift.bottom()));
        row.addTerminal(terminalToRow(lift.top()));
        for (Tower tower : lift.towers()) {
            row.addTower(towerToRow(tower));
        }
        for (Carrier carrier : lift.carriers()) {
            row.addCarrier(carrierToRow(carrier));
        }
        for (GroundPoint point : lift.ground().points()) {
            row.addShot(new SurveyShotRow(point.station(), point.elevation()));
        }
        return row;
    }

    /**
     * A machine back out of its row, with the rope it carries handed in.
     *
     * @param row the row
     * @param rope the rope on it
     * @return the machine
     * @throws StorageError when the row does not have two terminals, or a terminal or carrier on it
     *     is incomplete
     */
    public static Lift liftFromRow(LiftRow row, HaulRope rope) {
        List<Terminal> terminals = terminalsOf(row);
        return new Lift(
                row.getCode(),
                row.getName(),
                row.getLiftClass(),
                row.getBearing(),
                terminals.get(0),
                terminals.get(1),
                new TowerLine(
                        row.getTowers().stream().map(PlantMapper::towerFromRow).toList()),
                new CarrierSet(row.getCarriers().stream()
                        .map(PlantMapper::carrierFromRow)
                        .toList()),
                rope,
                new LineProfile(row.getShots().stream()
                        .map(shot -> new GroundPoint(shot.getStation(), shot.getElevation()))
                        .toList()),
                row.getDesignSpeed(),
                row.getCommissioned());
    }

    private static List<Terminal> terminalsOf(LiftRow row) {
        if (row.getTerminals().size() != 2) {
            throw new StorageError(row.getCode() + " has " + row.getTerminals().size() + " terminals, not two");
        }
        return row.getTerminals().stream()
                .sorted(Comparator.comparingLong(one -> one.getStation().mils()))
                .map(PlantMapper::terminalFromRow)
                .toList();
    }

    /**
     * An alignment survey as a row, with a row for each tower shot.
     *
     * @param survey the survey
     * @return a new row
     */
    public static AlignmentSurveyRow alignmentToRow(AlignmentSurvey survey) {
        AlignmentSurveyRow row = new AlignmentSurveyRow(survey.line().lift(), survey.taken(), survey.surveyor());
        for (TowerShot shot : survey.shots()) {
            row.addShot(new TowerShotRow(shot.ref(), shot.station(), shot.crossarmElevation(), shot.offsetAtTop()));
        }
        return row;
    }

    /**
     * The shots against the line they were shot on, which the row does not carry.
     *
     * <p>A shot knows a tower reference and nothing about how tall that tower is, and the whole
     * reading depends on the height. So the line is handed in, the same way a wear survey is handed
     * the rope it was taken against.
     *
     * @param row the row
     * @param line the machine's towers
     * @return the survey
     * @throws StorageError when the survey is of another machine
     */
    public static AlignmentSurvey alignmentFromRow(AlignmentSurveyRow row, TowerLine line) {
        if (!row.getLiftCode().equals(line.lift())) {
            throw new StorageError("that survey is on " + row.getLiftCode() + " and not " + line.lift());
        }
        return AlignmentSurvey.of(
                line,
                row.getTaken(),
                row.getSurveyor(),
                row.getShots().stream()
                        .map(one -> new TowerShot(
                                one.getTowerRef(), one.getStation(), one.getCrossarmElevation(), one.getOffsetAtTop()))
                        .toList());
    }
}
