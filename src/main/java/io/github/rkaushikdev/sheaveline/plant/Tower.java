package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A steel tower on the line, with a sheave assembly on each side of its crossarm.
 *
 * <p>A tower does one of three jobs, decided by which way the rope bends over it. On a rise the rope
 * is pushed down onto support sheaves. In a dip it is pulled up against hold down sheaves.
 *
 * @param ref the lift and the tower's number
 * @param station how far up the line it stands from the drive terminal
 * @param baseElevation the elevation of its footing
 * @param height from the footing to the crossarm
 * @param uphill the assembly on the uphill side
 * @param downhill the assembly on the downhill side
 */
public record Tower(
        TowerRef ref,
        Length station,
        Length baseElevation,
        Length height,
        SheaveAssembly uphill,
        SheaveAssembly downhill) {

    /**
     * A tower standing on the line.
     *
     * @throws PlantError when it stands before the drive terminal or has no height
     */
    public Tower {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(baseElevation, "baseElevation");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(uphill, "uphill");
        Objects.requireNonNull(downhill, "downhill");
        if (station.mils() < 0) {
            throw new PlantError(ref + " stands before the drive terminal");
        }
        if (height.mils() <= 0) {
            throw new PlantError(ref + " has no height");
        }
    }

    /**
     * The tower's number up the line.
     *
     * @return one upward
     */
    public int number() {
        return ref.number();
    }

    /**
     * The lift the tower is on.
     *
     * @return its code
     */
    public LiftCode lift() {
        return ref.lift();
    }

    /**
     * Where the crossarm is.
     *
     * @return the footing plus the height
     */
    public Length crossarmElevation() {
        return baseElevation.plus(height);
    }

    /**
     * The bend both sides put in the rope together.
     *
     * @return the sum of the two deviations
     */
    public Angle totalDeviation() {
        return uphill.deviation().plus(downhill.deviation());
    }

    /**
     * The kinds of assembly on the tower.
     *
     * @return one or two kinds
     */
    public Set<AssemblyKind> kinds() {
        return Collections.unmodifiableSet(EnumSet.of(uphill.kind(), downhill.kind()));
    }

    /**
     * Whether either side holds the rope down.
     *
     * @return true when any assembly carries up force
     */
    public boolean isHoldDown() {
        return kinds().stream().anyMatch(AssemblyKind::carriesUpForce);
    }

    /**
     * How many sheaves the tower carries.
     *
     * @return both sides together
     */
    public int sheaveCount() {
        return uphill.sheaves() + downhill.sheaves();
    }

    /** Such as {@code JP-T7 at station 2400}. */
    @Override
    public String toString() {
        return ref + " at station " + Decimals.fixed(station.feet(), 0);
    }
}
