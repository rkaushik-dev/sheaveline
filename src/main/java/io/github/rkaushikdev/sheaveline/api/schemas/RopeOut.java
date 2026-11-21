package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import java.time.LocalDate;

/**
 * What the rope file looks like on the wire: one rope, with the figures off its spec.
 *
 * @param serial the serial on the mill certificate
 * @param construction how the strands are laid up
 * @param grade the grade of the wire
 * @param lay the direction of the lay
 * @param diameter the diameter it was made to
 * @param condemningDiameter the diameter it comes off at
 * @param breakingStrength the least it breaks at
 * @param maximumTension the most it may run at
 * @param length how long it is
 * @param installed the day it went on
 * @param splices how many splices it carries
 * @param endless whether it is spliced into a loop
 */
public record RopeOut(
        @RopeSerialText RopeSerial serial,
        String construction,
        String grade,
        String lay,
        @LengthInches Length diameter,
        @LengthInches Length condemningDiameter,
        @LoadText Load breakingStrength,
        @LoadText Load maximumTension,
        @LengthFeet Length length,
        LocalDate installed,
        int splices,
        boolean endless) {

    /**
     * The rope as the tablet shows it.
     *
     * @param rope the rope
     * @return its shape
     */
    public static RopeOut of(HaulRope rope) {
        return new RopeOut(
                rope.serial(),
                rope.spec().construction().value(),
                rope.spec().grade().value(),
                rope.spec().lay().value(),
                rope.diameter(),
                rope.spec().condemningDiameter(),
                rope.spec().breakingStrength(),
                rope.spec().maximumTension(),
                rope.length(),
                rope.installed(),
                rope.splices(),
                rope.isEndless());
    }
}
