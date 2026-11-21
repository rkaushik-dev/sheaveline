package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
import io.github.rkaushikdev.sheaveline.plant.SheaveAssembly;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * One tower, the two spans either side of it, and how hard the rope presses on it.
 *
 * <p>The tower schedule gives a designed deviation angle. What the rope actually does is the
 * difference between the slope it arrives on and the slope it leaves on, and neither of those is the
 * chord slope. The force into the assembly is tension times that deviation. On a support it presses
 * the rope onto the sheaves and more of it is better. On a hold down it presses upward against them
 * and there is a floor under it, because a hold down with almost no force on it is a rope waiting to
 * come out of the sheave train.
 *
 * @param tower the tower
 * @param below the span whose top it stands at
 * @param above the span whose foot it stands at
 * @param sag how the rope hangs
 */
public record TowerLoading(Tower tower, Span below, Span above, SagModel sag) {

    /** The least upward force a hold down may carry, as a fraction of the rope tension. */
    public static final BigDecimal MINIMUM_HOLD_DOWN_FRACTION = new BigDecimal("0.02");

    /** How far the rope's deviation may stray from the designed one and still match it. */
    public static final Angle DESIGN_TOLERANCE = Angle.ofDegrees("0.25");

    /**
     * A tower standing between the two spans it is given.
     *
     * @throws LineError when the tower is not at the top of the span below or the foot of the span
     *     above
     */
    public TowerLoading {
        Objects.requireNonNull(tower, "tower");
        Objects.requireNonNull(below, "below");
        Objects.requireNonNull(above, "above");
        Objects.requireNonNull(sag, "sag");
        if (!below.upper().station().equals(tower.station())) {
            throw new LineError(tower.ref() + " is not at the top of the span below it");
        }
        if (!above.lower().station().equals(tower.station())) {
            throw new LineError(tower.ref() + " is not at the foot of the span above it");
        }
    }

    /**
     * The bend the rope takes over the tower. Positive bends the rope down onto the sheaves, negative
     * lifts it off them.
     *
     * @return the arrival slope below less the departure slope above
     */
    public Angle deviation() {
        return Deviation.arrivalSlope(sag, below).minus(Deviation.departureSlope(sag, above));
    }

    /**
     * The bend the tower schedule designed in.
     *
     * @return both assemblies' deviations together
     */
    public Angle designedDeviation() {
        return tower.uphill().deviation().plus(tower.downhill().deviation());
    }

    /**
     * How far the rope strays from the design.
     *
     * @return the deviation less the designed deviation
     */
    public Angle deviationError() {
        return deviation().minus(designedDeviation());
    }

    /**
     * Whether the rope bends within the usual tolerance of the design.
     *
     * @return true within a quarter of a degree
     */
    public boolean matchesDesign() {
        return matchesDesign(DESIGN_TOLERANCE);
    }

    /**
     * Whether the rope bends within a tolerance of the design.
     *
     * @param tolerance how far off it may be, either way
     * @return true within the tolerance
     */
    public boolean matchesDesign(Angle tolerance) {
        return deviationError().abs().compareTo(tolerance) <= 0;
    }

    /**
     * Tension times the deviation, which is the force into the assembly.
     *
     * @return the force, negative when the rope lifts
     */
    public Load radialForce() {
        return sag.tension().scaled(Decimals.of(deviation().radians()));
    }

    /**
     * Whether the rope presses down onto the sheaves.
     *
     * @return true for a positive deviation
     */
    public boolean pressesDown() {
        return deviation().millidegrees() > 0;
    }

    /**
     * What the rope says the tower is, which need not be what the schedule says.
     *
     * @return a combination for no bend at all, otherwise a support or a hold down
     */
    public AssemblyKind assemblyKindSeen() {
        if (deviation().isFlat()) {
            return AssemblyKind.COMBINATION;
        }
        return pressesDown() ? AssemblyKind.SUPPORT : AssemblyKind.HOLD_DOWN;
    }

    /**
     * Whether the tower carries the kind of assembly the rope calls for.
     *
     * @return true when the kind seen is on the tower
     */
    public boolean agreesWithSchedule() {
        return tower.kinds().contains(assemblyKindSeen());
    }

    /**
     * The floor under a hold down's seating force.
     *
     * @return the tension times the minimum fraction
     */
    public Load minimumHoldDownForce() {
        return sag.tension().scaled(MINIMUM_HOLD_DOWN_FRACTION);
    }

    /**
     * Whether a hold down has too little force on it, which is a deropement waiting to happen. A
     * tower with no hold down is never asked.
     *
     * @return true for a hold down whose upward force is under the floor
     */
    public boolean liftsOff() {
        if (!tower.isHoldDown()) {
            return false;
        }
        long upward = -radialForce().pounds();
        return upward < minimumHoldDownForce().pounds();
    }

    /**
     * The force on each sheave of the loaded side: the uphill assembly when the rope presses down,
     * the downhill one when it lifts.
     *
     * @return the load on one sheave
     */
    public Load perSheave() {
        SheaveAssembly side = pressesDown() ? tower.uphill() : tower.downhill();
        Load magnitude = Load.ofPounds(Math.abs(radialForce().pounds()));
        return side.perSheave(magnitude);
    }

    /** The two numbers a foreman wants, such as {@code JP-T4 at 7.46 deg, 4,489 lb into the assembly}. */
    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "%s at %s, %,d lb into the assembly",
                tower.ref(),
                deviation().formatDegrees(),
                Math.abs(radialForce().pounds()));
    }
}
