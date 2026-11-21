package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import java.time.LocalDate;

/**
 * The haul rope on a machine, with its mill details. The rope file has a fuller shape of its own,
 * {@link RopeOut}.
 *
 * @param serial the serial on the mill certificate
 * @param construction how the strands are laid up
 * @param grade the grade of the wire
 * @param lay the direction of the lay
 * @param diameter the diameter it was made to
 * @param length how long it is
 * @param installed the day it went on
 * @param splices how many splices it carries
 */
public record LiftRopeOut(
        @RopeSerialText RopeSerial serial,
        String construction,
        String grade,
        String lay,
        @LengthInches Length diameter,
        @LengthFeet Length length,
        LocalDate installed,
        int splices) {

    /**
     * The rope as the tablet shows it under its machine.
     *
     * @param rope the rope
     * @return its shape
     */
    public static LiftRopeOut of(HaulRope rope) {
        return new LiftRopeOut(
                rope.serial(),
                rope.spec().construction().value(),
                rope.spec().grade().value(),
                rope.spec().lay().value(),
                rope.diameter(),
                rope.length(),
                rope.installed(),
                rope.splices());
    }
}
