package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One mark on a magnetic test trace, at a station along the rope.
 *
 * @param station where along the rope, counted from the splice
 * @param kind what the mark is
 * @param magnitude a percentage, or for a local fault the number of wires
 */
public record Indication(Length station, IndicationKind kind, BigDecimal magnitude) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * A mark that a trace could show.
     *
     * @throws RopeworkError when the station is behind the splice, the magnitude is not positive, a
     *     percentage is over a hundred, or a local fault is not a whole number of wires
     */
    public Indication {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(magnitude, "magnitude");
        if (station.mils() < 0) {
            throw new RopeworkError("an indication station is measured from the splice, forwards");
        }
        if (magnitude.signum() <= 0) {
            throw new RopeworkError("an indication with no magnitude is not an indication");
        }
        if (kind.isMeasuredAsPercent() && magnitude.compareTo(HUNDRED) > 0) {
            throw new RopeworkError("a percentage indication above a hundred is a bad trace");
        }
        if (!kind.isMeasuredAsPercent() && !Decimals.isWhole(magnitude)) {
            throw new RopeworkError("a local fault is counted in whole wires");
        }
    }

    /**
     * A loss of metallic area.
     *
     * @param station where along the rope
     * @param percent the loss, such as {@code "4.5"}
     * @return the indication
     */
    public static Indication loss(Length station, String percent) {
        return loss(station, Decimals.of(percent));
    }

    /**
     * A loss of metallic area.
     *
     * @param station where along the rope
     * @param percent the loss
     * @return the indication
     */
    public static Indication loss(Length station, BigDecimal percent) {
        return new Indication(station, IndicationKind.LOSS_OF_AREA, percent);
    }

    /**
     * A local fault.
     *
     * @param station where along the rope
     * @param wires how many wires the step in the trace stands for
     * @return the indication
     */
    public static Indication fault(Length station, int wires) {
        return new Indication(station, IndicationKind.LOCAL_FAULT, Decimals.of(wires));
    }

    /** Such as {@code 4.5% loss of metallic area at 1200 ft} or {@code 3 wire local fault at 2400 ft}. */
    @Override
    public String toString() {
        String at = " at " + Decimals.fixed(station.feet(), 0) + " ft";
        if (kind.isMeasuredAsPercent()) {
            return magnitude + "% " + kind.value() + at;
        }
        return Decimals.fixed(magnitude, 0) + " wire local fault" + at;
    }
}
