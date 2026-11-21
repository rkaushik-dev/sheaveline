package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Objects;

/**
 * One machine, described well enough to answer for itself. This is where all the other records
 * have to agree.
 *
 * <p>Everything else describes one part of a machine and checks itself. This record checks the parts
 * against each other, and it is the only place that can: a quad chair with six place carriers, a rope
 * too thick for the bullwheel, a tower standing above the top terminal, a design speed the class does
 * not allow, a survey that stops halfway up the line. Each of those is consistent inside its own
 * record and wrong the moment the records are put together.
 *
 * <p>The {@code with} methods give the same machine with one part replaced, checked again as a
 * whole.
 *
 * @param code what the crew paints on the terminal
 * @param name what the trail map calls it
 * @param liftClass the class the board files it under
 * @param bearing the bearing of the line, bottom to top
 * @param bottom the bottom terminal
 * @param top the top terminal
 * @param towers the towers
 * @param carriers the fleet
 * @param rope the haul rope on it
 * @param ground the survey under the line
 * @param designSpeed the rope speed it was designed for
 * @param commissioned the day it first ran
 */
public record Lift(
        LiftCode code,
        String name,
        LiftClass liftClass,
        Bearing bearing,
        Terminal bottom,
        Terminal top,
        TowerLine towers,
        CarrierSet carriers,
        HaulRope rope,
        LineProfile ground,
        Speed designSpeed,
        LocalDate commissioned) {

    /** How far the rope on the machine may differ from the length the geometry calls for. */
    public static final Length ROPE_LENGTH_TOLERANCE = Length.ofFeet(40);

    /**
     * A machine whose parts agree with each other.
     *
     * @throws PlantError naming the first disagreement found
     */
    public Lift {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(liftClass, "liftClass");
        Objects.requireNonNull(bearing, "bearing");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(towers, "towers");
        Objects.requireNonNull(carriers, "carriers");
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(designSpeed, "designSpeed");
        Objects.requireNonNull(commissioned, "commissioned");
        Terminal.checkPair(bottom, top);
        checkTowers(code, bottom, top, towers);
        carriers.requireSuits(liftClass);
        checkRope(code, bottom, top, rope, commissioned);
        checkSpeed(code, liftClass, designSpeed);
        checkSurvey(bottom, top, ground);
    }

    private static void checkTowers(LiftCode code, Terminal bottom, Terminal top, TowerLine towers) {
        if (!towers.lift().equals(code)) {
            throw new PlantError("the tower line belongs to " + towers.lift() + ", not " + code);
        }
        if (towers.first().station().compareTo(bottom.station()) <= 0) {
            throw new PlantError(towers.first().ref() + " stands below the bottom terminal");
        }
        if (towers.last().station().compareTo(top.station()) >= 0) {
            throw new PlantError(towers.last().ref() + " stands above the top terminal");
        }
    }

    private static void checkRope(LiftCode code, Terminal bottom, Terminal top, HaulRope rope, LocalDate commissioned) {
        for (Terminal terminal : new Terminal[] {bottom, top}) {
            if (!terminal.bullwheel().acceptsRope(rope.diameter())) {
                throw new PlantError("the rope is too thick for the bullwheel at " + terminal.label());
            }
        }
        if (rope.installed().isBefore(commissioned)) {
            throw new PlantError("rope " + rope.serial() + " went on before " + code + " was built");
        }
    }

    private static void checkSpeed(LiftCode code, LiftClass liftClass, Speed designSpeed) {
        if (designSpeed.isStopped()) {
            throw new PlantError(code + " has no design speed");
        }
        if (designSpeed.compareTo(liftClass.lineSpeedLimit()) > 0) {
            throw new PlantError(designSpeed + " is above what a " + liftClass.value() + " may run");
        }
    }

    private static void checkSurvey(Terminal bottom, Terminal top, LineProfile ground) {
        if (!ground.covers(bottom.station())) {
            throw new PlantError("the survey does not reach the bottom terminal");
        }
        if (!ground.covers(top.station())) {
            throw new PlantError("the survey does not reach the top terminal");
        }
    }

    /**
     * The line cut into spans, terminal to terminal through every tower.
     *
     * @return the spans
     */
    public SpanLine spans() {
        Support lower = Support.terminal(bottom.label(), bottom.station(), bottom.ropeElevation());
        Support upper = Support.terminal(top.label(), top.station(), top.ropeElevation());
        return SpanLine.between(lower, towers, upper);
    }

    /**
     * The terminal that turns the rope.
     *
     * @return the driving terminal
     */
    public Terminal driveTerminal() {
        return bottom.role().drives() ? bottom : top;
    }

    /**
     * The terminal that holds the tension.
     *
     * @return the tensioning terminal
     */
    public Terminal tensionTerminal() {
        return bottom.role().tensions() ? bottom : top;
    }

    /**
     * Terminal to terminal along the ground.
     *
     * @return the horizontal length
     */
    public Length horizontalLength() {
        return top.station().minus(bottom.station());
    }

    /**
     * How far the rope climbs terminal to terminal.
     *
     * @return the vertical rise
     */
    public Length verticalRise() {
        return top.ropeElevation().minus(bottom.ropeElevation());
    }

    /**
     * The rope path from bottom to top.
     *
     * @return the summed chords
     */
    public Length slopeLength() {
        return spans().slopeLength();
    }

    /**
     * Twice up the hill plus half a turn round each bullwheel.
     *
     * @return the rope length the geometry calls for
     */
    public Length expectedRopeLength() {
        Length wraps = bottom.bullwheel().diameter().plus(top.bullwheel().diameter());
        return slopeLength().scaled(2).plus(wraps.scaled(Decimals.divide(Decimals.of(Math.PI), 2)));
    }

    /**
     * Whether the rope on the machine is within the usual tolerance of the geometry.
     *
     * @return true within forty feet
     */
    public boolean ropeLengthAgrees() {
        return ropeLengthAgrees(ROPE_LENGTH_TOLERANCE);
    }

    /**
     * Whether the rope on the machine is within a tolerance of the geometry.
     *
     * @param tolerance how far off it may be
     * @return true within the tolerance
     */
    public boolean ropeLengthAgrees(Length tolerance) {
        return rope.length().minus(expectedRopeLength()).abs().compareTo(tolerance) <= 0;
    }

    /**
     * How long a rider is on the line at design speed.
     *
     * @return the ride time
     */
    public Duration rideTime() {
        return designSpeed.timeToCover(slopeLength());
    }

    /**
     * How many carriers are on the line.
     *
     * @return the count
     */
    public int carriersOnLine() {
        return carriers.onLine().size();
    }

    /**
     * How many seats are on the line.
     *
     * @return the count
     */
    public int seatsOnLine() {
        return carriers.seatsOnLine();
    }

    /**
     * Whole years since the machine first ran.
     *
     * @param on the day asked about
     * @return completed years
     * @throws PlantError when the day is before it was built
     */
    public int ageInYears(LocalDate on) {
        if (on.isBefore(commissioned)) {
            throw new PlantError(on + " is before " + code + " was built");
        }
        int years = on.getYear() - commissioned.getYear();
        if (MonthDay.from(on).isBefore(MonthDay.from(commissioned))) {
            years -= 1;
        }
        return years;
    }

    /**
     * The bearing of the line from bottom to top.
     *
     * @return the uphill bearing
     */
    public Bearing uphillBearing() {
        return bearing;
    }

    /**
     * The bearing of the line from top to bottom.
     *
     * @return the downhill bearing
     */
    public Bearing downhillBearing() {
        return bearing.reciprocal();
    }

    /**
     * The same machine under another code.
     *
     * @param changed the code
     * @return the lift, checked again
     */
    public Lift withCode(LiftCode changed) {
        return new Lift(
                changed,
                name,
                liftClass,
                bearing,
                bottom,
                top,
                towers,
                carriers,
                rope,
                ground,
                designSpeed,
                commissioned);
    }

    /**
     * The same machine filed under another class.
     *
     * @param changed the class
     * @return the lift, checked again
     */
    public Lift withLiftClass(LiftClass changed) {
        return new Lift(
                code, name, changed, bearing, bottom, top, towers, carriers, rope, ground, designSpeed, commissioned);
    }

    /**
     * The same machine with another bottom terminal.
     *
     * @param changed the terminal
     * @return the lift, checked again
     */
    public Lift withBottom(Terminal changed) {
        return new Lift(
                code,
                name,
                liftClass,
                bearing,
                changed,
                top,
                towers,
                carriers,
                rope,
                ground,
                designSpeed,
                commissioned);
    }

    /**
     * The same machine with other towers.
     *
     * @param changed the towers
     * @return the lift, checked again
     */
    public Lift withTowers(TowerLine changed) {
        return new Lift(
                code,
                name,
                liftClass,
                bearing,
                bottom,
                top,
                changed,
                carriers,
                rope,
                ground,
                designSpeed,
                commissioned);
    }

    /**
     * The same machine with another fleet.
     *
     * @param changed the carriers
     * @return the lift, checked again
     */
    public Lift withCarriers(CarrierSet changed) {
        return new Lift(
                code, name, liftClass, bearing, bottom, top, towers, changed, rope, ground, designSpeed, commissioned);
    }

    /**
     * The same machine with another rope on it.
     *
     * @param changed the rope
     * @return the lift, checked again
     */
    public Lift withRope(HaulRope changed) {
        return new Lift(
                code,
                name,
                liftClass,
                bearing,
                bottom,
                top,
                towers,
                carriers,
                changed,
                ground,
                designSpeed,
                commissioned);
    }

    /**
     * The same machine over another survey.
     *
     * @param changed the survey
     * @return the lift, checked again
     */
    public Lift withGround(LineProfile changed) {
        return new Lift(
                code,
                name,
                liftClass,
                bearing,
                bottom,
                top,
                towers,
                carriers,
                rope,
                changed,
                designSpeed,
                commissioned);
    }

    /**
     * The same machine designed for another speed.
     *
     * @param changed the design speed
     * @return the lift, checked again
     */
    public Lift withDesignSpeed(Speed changed) {
        return new Lift(
                code, name, liftClass, bearing, bottom, top, towers, carriers, rope, ground, changed, commissioned);
    }

    /** Such as {@code JP John Paul, detachable chairlift}. */
    @Override
    public String toString() {
        return code + " " + name + ", " + liftClass.value();
    }
}
