package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.DriveTrain;
import io.github.rkaushikdev.sheaveline.plant.DriveType;
import java.util.List;
import java.util.Optional;

/**
 * The drive train in a driving terminal.
 *
 * @param driveType what turns the bullwheel
 * @param ratedHorsepower the motor's rating
 * @param auxiliary the standby drive, when one is fitted
 * @param brakes the kinds of brake fitted
 * @param hasStandbyPower whether the machine can be run off something other than the mains
 */
public record DriveOut(
        String driveType,
        int ratedHorsepower,
        Optional<String> auxiliary,
        List<String> brakes,
        boolean hasStandbyPower) {

    /** A drive train's shape. */
    public DriveOut {
        brakes = List.copyOf(brakes);
    }

    /**
     * The drive train as the tablet shows it.
     *
     * @param drive the drive train
     * @return its shape
     */
    public static DriveOut of(DriveTrain drive) {
        return new DriveOut(
                drive.driveType().value(),
                drive.ratedHorsepower(),
                drive.auxiliary().map(DriveType::value),
                drive.brakes().stream().map(brake -> brake.kind().value()).toList(),
                drive.hasStandbyPower());
    }
}
