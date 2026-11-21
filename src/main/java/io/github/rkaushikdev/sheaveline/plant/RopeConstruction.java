package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * Strands by wires, as the reel tag reads.
 *
 * <p>Construction matters because it decides how many outer wires there are, and the broken wire
 * limits are counted against that number rather than against a flat figure.
 */
public enum RopeConstruction implements Valued {
    /** Six strands of twenty five wires, filler wire pattern. */
    SIX_BY_TWENTY_FIVE("6x25 filler wire", 6, 12),
    /** Six strands of twenty six wires, Warrington Seale pattern. */
    SIX_BY_TWENTY_SIX("6x26 warrington seale", 6, 10),
    /** Six strands of thirty six wires, Warrington Seale pattern. */
    SIX_BY_THIRTY_SIX("6x36 warrington seale", 6, 14),
    /** Eight strands of nineteen wires, Seale pattern. */
    EIGHT_BY_NINETEEN("8x19 seale", 8, 9);

    private final String value;
    private final int strands;
    private final int outerWiresPerStrand;

    RopeConstruction(String value, int strands, int outerWiresPerStrand) {
        this.value = value;
        this.strands = strands;
        this.outerWiresPerStrand = outerWiresPerStrand;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * How many strands the rope is laid from.
     *
     * @return the strand count
     */
    public int strands() {
        return strands;
    }

    /**
     * How many wires of each strand show on the outside.
     *
     * @return the outer wire count of one strand
     */
    public int outerWiresPerStrand() {
        return outerWiresPerStrand;
    }

    /**
     * Every wire that can be seen, and therefore every wire a break is counted in.
     *
     * @return strands times outer wires per strand
     */
    public int outerWires() {
        return strands * outerWiresPerStrand;
    }
}
