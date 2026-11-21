package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import java.util.List;
import java.util.Objects;

/**
 * How a carrier gets through one terminal.
 *
 * <p>On a fixed grip the carrier comes through the load point at line speed and the crew has the
 * length of the ramp to get everybody sat down. On a detachable the grip opens, the carrier drops to
 * a walking pace, runs round the terminal on tyres, and speeds back up on the way out. The loading
 * window is set by the slow section and has almost nothing to do with the line speed.
 *
 * @param lineSpeed the rope speed on the line
 * @param terminalSpeed the speed through the slow section
 * @param deceleration the ramp down from line speed
 * @param slowSection the stretch at terminal speed, where the crew loads
 * @param acceleration the ramp back up to line speed
 */
public record TerminalTransit(
        Speed lineSpeed, Speed terminalSpeed, Length deceleration, Length slowSection, Length acceleration) {

    /**
     * A transit that moves, never faster in the terminal than on the line.
     *
     * @throws CapacityError when either speed is stopped, the terminal runs faster than the line, or a
     *     section is negative
     */
    public TerminalTransit {
        Objects.requireNonNull(lineSpeed, "lineSpeed");
        Objects.requireNonNull(terminalSpeed, "terminalSpeed");
        Objects.requireNonNull(deceleration, "deceleration");
        Objects.requireNonNull(slowSection, "slowSection");
        Objects.requireNonNull(acceleration, "acceleration");
        if (lineSpeed.isStopped()) {
            throw new CapacityError("a terminal transit needs a line speed");
        }
        if (terminalSpeed.isStopped()) {
            throw new CapacityError("a carrier does not stop dead in the terminal");
        }
        if (terminalSpeed.compareTo(lineSpeed) > 0) {
            throw new CapacityError("a terminal does not run faster than the line");
        }
        for (Length piece : List.of(deceleration, slowSection, acceleration)) {
            if (piece.mils() < 0) {
                throw new CapacityError("a terminal section has a length");
            }
        }
    }

    /**
     * A fixed grip has no slow section of its own, so the ramp at line speed is the whole of it.
     *
     * @param lineSpeed the rope speed
     * @param ramp the loading ramp
     * @return the transit
     */
    public static TerminalTransit fixedGrip(Speed lineSpeed, Length ramp) {
        return new TerminalTransit(lineSpeed, lineSpeed, Length.ZERO, ramp, Length.ZERO);
    }

    /**
     * Whether the carrier slows down in the terminal.
     *
     * @return true when the terminal runs slower than the line
     */
    public boolean detaches() {
        return terminalSpeed.compareTo(lineSpeed) < 0;
    }

    /**
     * Mean of the two ends, which is close enough over a ramp this short.
     *
     * @return the ramp speed, rounded down to the mil a minute
     */
    public Speed averageRampSpeed() {
        return new Speed((lineSpeed.milsPerMinute() + terminalSpeed.milsPerMinute()) / 2);
    }

    /**
     * Time through the slow section.
     *
     * @return the slow section at terminal speed
     */
    public Duration slowTime() {
        return terminalSpeed.timeToCover(slowSection);
    }

    /**
     * Time through the whole terminal.
     *
     * @return the slow section plus both ramps at the ramp speed
     */
    public Duration transitTime() {
        Length ramp = deceleration.plus(acceleration);
        Duration onRamp = averageRampSpeed().timeToCover(ramp);
        return slowTime().plus(onRamp);
    }

    /**
     * How long a carrier is beside the crew, which is the slow section only.
     *
     * @return the loading window
     */
    public Duration loadingWindow() {
        return slowTime();
    }

    /**
     * The length of the terminal track.
     *
     * @return both ramps and the slow section
     */
    public Length length() {
        return deceleration.plus(slowSection).plus(acceleration);
    }
}
