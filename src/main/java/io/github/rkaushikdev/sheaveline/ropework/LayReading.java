package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A lay length, measured over six strands with a rule.
 *
 * @param station where along the rope, counted from the splice
 * @param measured the length of one lay
 */
public record LayReading(Length station, Length measured) {

    /**
     * A reading that is a length.
     *
     * @throws RopeworkError when the measured lay is not positive
     */
    public LayReading {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(measured, "measured");
        if (measured.mils() <= 0) {
            throw new RopeworkError("a lay reading is a positive length");
        }
    }

    /**
     * How far the lay has opened out, as a fraction of what it should be.
     *
     * @param nominal the lay the rope was made with
     * @return the stretch, negative when the lay has closed up
     */
    public BigDecimal stretchAgainst(Length nominal) {
        return measured.minus(nominal).ratio(nominal);
    }
}
