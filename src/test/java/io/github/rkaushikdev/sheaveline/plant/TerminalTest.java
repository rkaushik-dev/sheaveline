package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerminalTest {

    private static final Bullwheel WHEEL = new Bullwheel(Length.ofFeetInches(11, 6), "polyurethane");
    private static final Brake SERVICE = new Brake(BrakeKind.SERVICE, Torque.ofPoundFeet(900));
    private static final Brake EMERGENCY = new Brake(BrakeKind.EMERGENCY, Torque.ofPoundFeet(2400));
    private static final Brake BACKSTOP = new Brake(BrakeKind.BACKSTOP, Torque.ofPoundFeet(1800), false);

    private static DriveTrain driveTrain(
            DriveType type, int horsepower, BigDecimal ratio, List<Brake> brakes, Optional<DriveType> auxiliary) {
        return new DriveTrain(type, horsepower, ratio, brakes, auxiliary);
    }

    private static DriveTrain driveTrain(List<Brake> brakes, Optional<DriveType> auxiliary) {
        return driveTrain(DriveType.AC_DRIVE, 700, new BigDecimal("18.5"), brakes, auxiliary);
    }

    private static DriveTrain driveTrain() {
        return driveTrain(List.of(SERVICE, EMERGENCY), Optional.of(DriveType.DIESEL_HYDRAULIC));
    }

    private static Terminal terminal(
            TerminalRole role, String label, Length station, Optional<DriveTrain> drive, Optional<Length> travel) {
        return new Terminal(role, label, station, Length.ofFeet(6412), WHEEL, drive, travel);
    }

    private static Optional<DriveTrain> driveFor(TerminalRole role) {
        return role.drives() ? Optional.of(driveTrain()) : Optional.empty();
    }

    private static Optional<Length> travelFor(TerminalRole role) {
        return role.tensions() ? Optional.of(Length.ofFeet(14)) : Optional.empty();
    }

    private static Terminal terminal(TerminalRole role, String label, Length station) {
        return terminal(role, label, station, driveFor(role), travelFor(role));
    }

    private static Terminal terminal(TerminalRole role) {
        return terminal(role, "bottom", Length.ofFeet(0));
    }

    @Test
    void aBrakeNeedsTorque() {
        assertThatThrownBy(() -> new Brake(BrakeKind.SERVICE, new Torque(0)))
                .isInstanceOf(PlantError.class)
                .hasMessage("a service brake with no torque is not a brake");
        assertThat(SERVICE).hasToString("service brake at 900 lb ft");
    }

    @Test
    void aBackstopIsMechanical() {
        assertThatThrownBy(() -> new Brake(BrakeKind.BACKSTOP, Torque.ofPoundFeet(1800)))
                .isInstanceOf(PlantError.class);
        assertThat(BACKSTOP.hydraulic()).isFalse();
    }

    @Test
    void brakesKnowWhatTheyActOn() {
        assertThat(EMERGENCY.kind().actsOnBullwheel()).isTrue();
        assertThat(SERVICE.kind().actsOnBullwheel()).isFalse();
        assertThat(BACKSTOP.kind().holdsAgainstRollback()).isTrue();
        assertThat(EMERGENCY.kind().holdsAgainstRollback()).isFalse();
    }

    @Test
    void aBullwheelMeasuresItsWayRound() {
        assertThat(Decimals.quantize(WHEEL.circumference().feet(), 2)).isEqualByComparingTo("36.13");
    }

    @Test
    void ropeSpeedFallsOutOfTheWheelAndTheRevolutions() {
        Speed speed = WHEEL.ropeSpeedAt(14);
        assertThat(Decimals.quantize(speed.feetPerMinute(), 1)).isEqualByComparingTo("505.8");
        assertThat(Decimals.quantize(WHEEL.revolutionsFor(speed), 2)).isEqualByComparingTo("14");
    }

    @Test
    void speedAndRevolutionsAreTheSameQuestionTwice() {
        for (long rpm : new long[] {6, 12, 20, 28}) {
            Speed speed = WHEEL.ropeSpeedAt(rpm);
            assertThat(Decimals.quantize(WHEEL.revolutionsFor(speed), 3)).isEqualByComparingTo(Decimals.of(rpm));
        }
    }

    @Test
    void aWheelWillNotTakeARopeThickerThanASixtiethOfIt() {
        assertThat(WHEEL.largestRope()).isEqualTo(Length.ofInches("2.3"));
        assertThat(WHEEL.acceptsRope(Length.ofInches("1.378"))).isTrue();
        assertThat(WHEEL.acceptsRope(Length.ofInches("2.5"))).isFalse();
    }

    @Test
    void aBullwheelNeedsASizeAndALiner() {
        assertThatThrownBy(() -> new Bullwheel(Length.ofFeet(0), "polyurethane"))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> new Bullwheel(Length.ofFeet(11), "  ")).isInstanceOf(PlantError.class);
        assertThat(WHEEL).hasToString("11' 6\" bullwheel, polyurethane liner");
    }

    @Test
    void aDriveTrainAlwaysHasAServiceBrake() {
        assertThatThrownBy(() -> driveTrain(List.of(EMERGENCY), Optional.of(DriveType.DIESEL_HYDRAULIC)))
                .isInstanceOf(PlantError.class)
                .hasMessage("a drive train carries a service brake");
        assertThat(driveTrain().hasBrake(BrakeKind.SERVICE)).isTrue();
    }

    @Test
    void aDriveTrainCarriesOneBrakeOfEachKind() {
        List<Brake> doubled = List.of(SERVICE, new Brake(BrakeKind.SERVICE, Torque.ofPoundFeet(700)), EMERGENCY);
        assertThatThrownBy(() -> driveTrain(doubled, Optional.empty())).isInstanceOf(PlantError.class);
    }

    @Test
    void aDriveTrainChecksItsOwnNumbers() {
        List<Brake> brakes = List.of(SERVICE, EMERGENCY);
        assertThatThrownBy(() -> driveTrain(DriveType.AC_DRIVE, 0, new BigDecimal("18.5"), brakes, Optional.empty()))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> driveTrain(DriveType.AC_DRIVE, 700, BigDecimal.ZERO, brakes, Optional.empty()))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aNamedBrakeCanBeFetchedOrRefused() {
        assertThat(driveTrain().brake(BrakeKind.EMERGENCY)).isEqualTo(EMERGENCY);
        assertThatThrownBy(() -> driveTrain().brake(BrakeKind.BACKSTOP))
                .isInstanceOf(PlantError.class)
                .hasMessage("there is no backstop brake on this drive");
    }

    @Test
    void standbyPowerIsAnAuxiliaryOrADiesel() {
        assertThat(driveTrain().hasStandbyPower()).isTrue();
        assertThat(driveTrain(List.of(SERVICE, EMERGENCY), Optional.empty()).hasStandbyPower())
                .isFalse();
        assertThat(driveTrain(
                                DriveType.DIESEL_HYDRAULIC,
                                700,
                                new BigDecimal("18.5"),
                                List.of(SERVICE, EMERGENCY),
                                Optional.empty())
                        .hasStandbyPower())
                .isTrue();
    }

    @Test
    void theMotorTurnsFasterThanTheWheelByTheGearRatio() {
        assertThat(driveTrain().motorSpeedFor(new BigDecimal(14))).isEqualByComparingTo("259.0");
    }

    @Test
    void anAerialLiftNeedsAnEmergencyBrakeAndStandbyPower() {
        assertThat(driveTrain().suits(LiftClass.DETACHABLE_CHAIR)).isTrue();
        assertThat(driveTrain(List.of(SERVICE), Optional.of(DriveType.DIESEL_HYDRAULIC))
                        .suits(LiftClass.FIXED_CHAIR))
                .isFalse();
        assertThat(driveTrain(List.of(SERVICE, EMERGENCY), Optional.empty()).suits(LiftClass.FIXED_CHAIR))
                .isFalse();
    }

    @Test
    void aSurfaceLiftIsHeldToNeither() {
        assertThat(driveTrain(List.of(SERVICE), Optional.empty()).suits(LiftClass.SURFACE_TOW))
                .isTrue();
    }

    @Test
    void aDrivingTerminalCarriesADriveTrain() {
        assertThatThrownBy(() ->
                        terminal(TerminalRole.DRIVE, "bottom", Length.ofFeet(0), Optional.empty(), Optional.empty()))
                .isInstanceOf(PlantError.class)
                .hasMessage("bottom drives the rope and has no drive train");
        assertThat(terminal(TerminalRole.DRIVE).driveTrain().ratedHorsepower()).isEqualTo(700);
    }

    @Test
    void aTerminalThatDoesNotDriveMustNotCarryOne() {
        assertThatThrownBy(() -> terminal(
                        TerminalRole.RETURN, "bottom", Length.ofFeet(0), Optional.of(driveTrain()), Optional.empty()))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> terminal(TerminalRole.RETURN).driveTrain()).isInstanceOf(PlantError.class);
    }

    @Test
    void aTensioningTerminalCarriesItsTravel() {
        assertThatThrownBy(() -> terminal(
                        TerminalRole.RETURN_TENSION, "bottom", Length.ofFeet(0), Optional.empty(), Optional.empty()))
                .isInstanceOf(PlantError.class);
        assertThat(terminal(TerminalRole.RETURN_TENSION).travel()).isEqualTo(Length.ofFeet(14));
    }

    @Test
    void aTerminalThatDoesNotTensionMustNotCarryTravel() {
        assertThatThrownBy(() -> terminal(
                        TerminalRole.DRIVE,
                        "bottom",
                        Length.ofFeet(0),
                        Optional.of(driveTrain()),
                        Optional.of(Length.ofFeet(14))))
                .isInstanceOf(PlantError.class);
        assertThatThrownBy(() -> terminal(TerminalRole.DRIVE).travel())
                .isInstanceOf(PlantError.class)
                .hasMessage("bottom does not tension the rope");
    }

    @Test
    void aTerminalPrintsItsRole() {
        assertThat(terminal(TerminalRole.DRIVE)).hasToString("bottom (drive)");
    }

    @Test
    void aLiftDrivesFromOneEndAndTensionsAtOneEnd() {
        Terminal bottom = terminal(TerminalRole.DRIVE);
        Terminal top = new Terminal(
                TerminalRole.RETURN_TENSION,
                "top",
                Length.ofFeet(4800),
                Length.ofFeet(8012),
                WHEEL,
                Optional.empty(),
                Optional.of(Length.ofFeet(14)));
        assertThatCode(() -> Terminal.checkPair(bottom, top)).doesNotThrowAnyException();
    }

    @Test
    void twoDriveTerminalsOrNoneIsRefused() {
        Terminal bottom = terminal(TerminalRole.DRIVE);
        Terminal other = terminal(TerminalRole.DRIVE, "top", Length.ofFeet(4800));
        assertThatThrownBy(() -> Terminal.checkPair(bottom, other))
                .isInstanceOf(PlantError.class)
                .hasMessage("a lift drives from one terminal, found 2");
        Terminal plain = terminal(TerminalRole.RETURN);
        assertThatThrownBy(() ->
                        Terminal.checkPair(plain, terminal(TerminalRole.RETURN_TENSION, "bottom", Length.ofFeet(4800))))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void theTerminalsHaveToBeTheRightWayUpTheHill() {
        Terminal bottom = terminal(TerminalRole.DRIVE, "bottom", Length.ofFeet(4800));
        Terminal top = terminal(TerminalRole.RETURN_TENSION, "top", Length.ofFeet(0));
        assertThatThrownBy(() -> Terminal.checkPair(bottom, top)).isInstanceOf(PlantError.class);
    }
}
