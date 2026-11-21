package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One end of the lift, and what it is responsible for.
 *
 * <p>A terminal is not just a building. It carries a bullwheel the rope wraps around, and depending
 * on its role it may also carry the drive train, the tension system, or both. The rules about which
 * is which are enforced because a lift with two drive terminals or none is a data entry mistake, not
 * a machine.
 *
 * @param role what it does
 * @param label what the crew calls it
 * @param station where it stands along the line
 * @param ropeElevation the elevation of the rope at the bullwheel
 * @param bullwheel its bullwheel
 * @param drive its drive train, when it drives
 * @param tensionTravel the tension carriage travel, when it tensions
 */
public record Terminal(
        TerminalRole role,
        String label,
        Length station,
        Length ropeElevation,
        Bullwheel bullwheel,
        Optional<DriveTrain> drive,
        Optional<Length> tensionTravel) {

    /**
     * A terminal that carries what its role needs and nothing else.
     *
     * @throws PlantError when a driving terminal has no drive train or another has one, or a
     *     tensioning terminal has no travel or another has some
     */
    public Terminal {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(ropeElevation, "ropeElevation");
        Objects.requireNonNull(bullwheel, "bullwheel");
        Objects.requireNonNull(drive, "drive");
        Objects.requireNonNull(tensionTravel, "tensionTravel");
        if (role.drives() && drive.isEmpty()) {
            throw new PlantError(label + " drives the rope and has no drive train");
        }
        if (!role.drives() && drive.isPresent()) {
            throw new PlantError(label + " does not drive the rope and carries a drive train");
        }
        if (role.tensions() && tensionTravel.isEmpty()) {
            throw new PlantError(label + " tensions the rope and has no travel");
        }
        if (!role.tensions() && tensionTravel.isPresent()) {
            throw new PlantError(label + " does not tension the rope and has travel");
        }
    }

    /**
     * Check a lift's two terminals together: one drives, one tensions, and the lower stands below
     * the upper.
     *
     * @param lower the bottom terminal
     * @param upper the top terminal
     * @throws PlantError when the pair is not a machine
     */
    public static void checkPair(Terminal lower, Terminal upper) {
        long drives = Stream.of(lower, upper).filter(one -> one.role.drives()).count();
        long tensions =
                Stream.of(lower, upper).filter(one -> one.role.tensions()).count();
        if (drives != 1) {
            throw new PlantError("a lift drives from one terminal, found " + drives);
        }
        if (tensions != 1) {
            throw new PlantError("a lift tensions at one terminal, found " + tensions);
        }
        if (lower.station.compareTo(upper.station) >= 0) {
            throw new PlantError("the lower terminal stands below the upper one");
        }
    }

    /**
     * The drive train, for a terminal that must have one.
     *
     * @return the drive train
     * @throws PlantError when this terminal does not drive
     */
    public DriveTrain driveTrain() {
        return drive.orElseThrow(() -> new PlantError(label + " has no drive train"));
    }

    /**
     * The tension carriage travel, for a terminal that must have one.
     *
     * @return the travel
     * @throws PlantError when this terminal does not tension
     */
    public Length travel() {
        return tensionTravel.orElseThrow(() -> new PlantError(label + " does not tension the rope"));
    }

    /** Such as {@code bottom (drive)}. */
    @Override
    public String toString() {
        return label + " (" + role.value() + ")";
    }
}
