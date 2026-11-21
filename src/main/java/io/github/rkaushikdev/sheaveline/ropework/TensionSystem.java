package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The tension system on one lift, with the carriage where it is now.
 *
 * <p>Something has to keep the rope tight while it stretches in the cold, sags in the heat and gets
 * shorter every time a splice is made. On this hill that is either a counterweight hanging in the
 * return terminal or a hydraulic ram pushing the carriage, and the two behave differently enough that
 * the difference is a type.
 *
 * <p>The carriage position is the number that ends ropes. Every shortening moves it by half the
 * length removed, because the loop is doubled, and when it reaches the end of its travel the rope is
 * finished whatever the caliper says.
 *
 * @param kind what keeps the rope tight
 * @param nominal the tension it is set to hold
 * @param travel how far the carriage can move
 * @param carriage where the carriage is, measured from the closed end
 */
public record TensionSystem(TensionKind kind, Load nominal, Length travel, Length carriage) {

    /** The part of the travel kept clear at each end. */
    public static final BigDecimal RESERVE_FRACTION = new BigDecimal("0.10");

    /** The band a hydraulic ram holds, either side of nominal. */
    public static final BigDecimal HYDRAULIC_BAND = new BigDecimal("0.05");

    /** The band a counterweight holds, either side of nominal. */
    public static final BigDecimal COUNTERWEIGHT_BAND = new BigDecimal("0.02");

    private static final BigDecimal HALF = new BigDecimal("0.5");

    /**
     * A system with real numbers in it.
     *
     * @throws RopeworkError when the tension or the travel is not positive, or the carriage is behind
     *     the closed end
     */
    public TensionSystem {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(nominal, "nominal");
        Objects.requireNonNull(travel, "travel");
        Objects.requireNonNull(carriage, "carriage");
        if (nominal.pounds() <= 0) {
            throw new RopeworkError("a tension system holds a positive tension");
        }
        if (travel.mils() <= 0) {
            throw new RopeworkError("a tension system has travel");
        }
        if (carriage.mils() < 0) {
            throw new RopeworkError("the carriage position is measured from the closed end");
        }
    }

    /**
     * How much further the carriage can go before it runs out.
     *
     * @return the travel left at the open end
     */
    public Length remainingOut() {
        return travel.minus(carriage);
    }

    /**
     * How far the carriage is from the closed end.
     *
     * @return the travel left at the closed end
     */
    public Length remainingIn() {
        return carriage;
    }

    /**
     * The travel kept clear at each end.
     *
     * @return a tenth of the travel
     */
    public Length reserve() {
        return travel.scaled(RESERVE_FRACTION);
    }

    /**
     * Where the carriage is, as a percentage of its travel.
     *
     * @return the position
     */
    public BigDecimal positionPercent() {
        return Decimals.multiply(carriage.ratio(travel), 100);
    }

    /**
     * Whether the carriage is between its stops.
     *
     * @return true from the closed end to the open end, both included
     */
    public boolean isWithinTravel() {
        return Length.ZERO.compareTo(carriage) <= 0 && carriage.compareTo(travel) <= 0;
    }

    /**
     * Whether the carriage is inside the travel and not within a tenth of either stop.
     *
     * @return true with the reserve clear at both ends
     */
    public boolean hasReserve() {
        Length reserve = reserve();
        return remainingIn().compareTo(reserve) >= 0 && remainingOut().compareTo(reserve) >= 0;
    }

    /**
     * The same system with the carriage somewhere else.
     *
     * @param position the new carriage position
     * @return the new record
     */
    public TensionSystem movedTo(Length position) {
        return new TensionSystem(kind, nominal, travel, position);
    }

    /**
     * The system after a shortening of the loop, which moves the carriage by half of it.
     *
     * @param removed the length cut out of the rope
     * @return the new record
     * @throws RopeworkError when nothing was removed
     */
    public TensionSystem afterShortening(Length removed) {
        if (removed.mils() <= 0) {
            throw new RopeworkError("a shortening removes a positive length");
        }
        return movedTo(carriage.plus(removed.scaled(HALF)));
    }

    /**
     * How many more cuts of a size the carriage has room for, keeping the reserve clear.
     *
     * @param typical the length a cut usually removes
     * @return whole cuts left
     * @throws RopeworkError when the cut removes nothing
     */
    public int shorteningsLeft(Length typical) {
        if (typical.mils() <= 0) {
            throw new RopeworkError("a shortening removes a positive length");
        }
        Length usable = remainingOut().minus(reserve());
        if (usable.mils() <= 0) {
            return 0;
        }
        return usable.ratio(typical.scaled(HALF)).intValue();
    }

    /**
     * The tension a gauge should read.
     *
     * @return the nominal tension
     */
    public Load expectedTension() {
        return nominal;
    }

    /**
     * How far a measured tension may sit from nominal. A counterweight is held to a tighter band than a
     * ram.
     *
     * @return the half width of the band
     */
    public Load tolerance() {
        if (kind.holdsConstant()) {
            return nominal.scaled(COUNTERWEIGHT_BAND);
        }
        return nominal.scaled(HYDRAULIC_BAND);
    }

    /**
     * Whether a measured tension is inside the band.
     *
     * @param measured what the gauge read
     * @return true within the tolerance, either side
     */
    public boolean accepts(Load measured) {
        long difference = Math.abs(measured.pounds() - nominal.pounds());
        return difference <= tolerance().pounds();
    }

    /** As the morning sheet reads, such as {@code counterweight at 34,500 lb, carriage 50% out}. */
    @Override
    public String toString() {
        return kind.value() + " at " + nominal.formatPounds() + ", carriage " + Decimals.fixed(positionPercent(), 0)
                + "% out";
    }
}
