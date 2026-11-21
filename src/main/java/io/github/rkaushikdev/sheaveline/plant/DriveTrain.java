package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The prime mover, what it drives through, and how it is stopped.
 *
 * @param driveType what turns the bullwheel
 * @param ratedHorsepower the rating
 * @param gearboxRatio motor revolutions per wheel revolution
 * @param brakes one brake of each kind fitted, a service brake always among them
 * @param auxiliary the standby drive, when one is fitted
 */
public record DriveTrain(
        DriveType driveType,
        int ratedHorsepower,
        BigDecimal gearboxRatio,
        List<Brake> brakes,
        Optional<DriveType> auxiliary) {

    /**
     * A drive train that can be stopped.
     *
     * @throws PlantError when it has no rating, a ratio that is not positive, two brakes of one
     *     kind, or no service brake
     */
    public DriveTrain {
        Objects.requireNonNull(driveType, "driveType");
        Objects.requireNonNull(gearboxRatio, "gearboxRatio");
        Objects.requireNonNull(auxiliary, "auxiliary");
        brakes = List.copyOf(brakes);
        if (ratedHorsepower <= 0) {
            throw new PlantError("a drive train has a rating");
        }
        if (gearboxRatio.signum() <= 0) {
            throw new PlantError("a gearbox ratio is positive");
        }
        if (brakes.stream().map(Brake::kind).distinct().count() != brakes.size()) {
            throw new PlantError("a drive train carries one brake of each kind");
        }
        if (brakes.stream().noneMatch(brake -> brake.kind() == BrakeKind.SERVICE)) {
            throw new PlantError("a drive train carries a service brake");
        }
    }

    /**
     * Whether a brake of that kind is fitted.
     *
     * @param kind the kind
     * @return true when it is
     */
    public boolean hasBrake(BrakeKind kind) {
        return brakes.stream().anyMatch(brake -> brake.kind() == kind);
    }

    /**
     * The brake of that kind.
     *
     * @param kind the kind
     * @return the brake
     * @throws PlantError when none is fitted
     */
    public Brake brake(BrakeKind kind) {
        return brakes.stream()
                .filter(brake -> brake.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new PlantError("there is no " + kind.value() + " brake on this drive"));
    }

    /**
     * Whether the lift can run without the grid: either a separate auxiliary drive or a prime mover
     * that is its own.
     *
     * @return true when it can
     */
    public boolean hasStandbyPower() {
        return auxiliary.isPresent() || driveType.isPrimeMover();
    }

    /**
     * How fast the motor turns for a wheel speed.
     *
     * @param wheelRpm the wheel's revolutions a minute
     * @return the motor's
     */
    public BigDecimal motorSpeedFor(BigDecimal wheelRpm) {
        return Decimals.multiply(wheelRpm, gearboxRatio);
    }

    /**
     * Whether the drive suits the class. An aerial lift needs an emergency brake and a way to run
     * without the grid; a surface lift is held to neither.
     *
     * @param liftClass the class
     * @return true when it suits
     */
    public boolean suits(LiftClass liftClass) {
        if (!liftClass.isAerial()) {
            return true;
        }
        return hasBrake(BrakeKind.EMERGENCY) && hasStandbyPower();
    }
}
