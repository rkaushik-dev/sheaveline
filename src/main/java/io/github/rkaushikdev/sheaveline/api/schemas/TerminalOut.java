package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Terminal;
import java.util.Optional;

/**
 * One terminal, with its drive when it drives and its carriage travel when it tensions.
 *
 * @param label what the terminal is called
 * @param role what it does
 * @param station how far along the line it stands
 * @param ropeElevation the elevation of the rope at the bullwheel
 * @param bullwheelDiameter the bullwheel's diameter
 * @param bullwheelLiner what the bullwheel is lined with
 * @param tensionTravel the tension carriage travel, when it tensions
 * @param drive the drive train, when it drives
 */
public record TerminalOut(
        String label,
        String role,
        @LengthFeet Length station,
        @LengthFeet Length ropeElevation,
        @LengthFeet Length bullwheelDiameter,
        String bullwheelLiner,
        @LengthFeet Optional<Length> tensionTravel,
        Optional<DriveOut> drive) {

    /**
     * The terminal as the tablet shows it.
     *
     * @param terminal the terminal
     * @return its shape
     */
    public static TerminalOut of(Terminal terminal) {
        return new TerminalOut(
                terminal.label(),
                terminal.role().value(),
                terminal.station(),
                terminal.ropeElevation(),
                terminal.bullwheel().diameter(),
                terminal.bullwheel().liner(),
                terminal.tensionTravel(),
                terminal.drive().map(DriveOut::of));
    }
}
