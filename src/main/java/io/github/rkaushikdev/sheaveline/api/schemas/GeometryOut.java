package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import java.util.List;

/**
 * The line walked under some snow at some tension, and whether it may run.
 *
 * @param verdict fit, restricted or unfit
 * @param mayRun whether it may carry the public
 * @param snow the snow depth asked about
 * @param tension the tension asked about
 * @param ice the ice on the rope asked about
 * @param worstClearance the least clearance anywhere on the line
 * @param worstMargin how far that is above what is required
 * @param worstStation where it is
 * @param findings what the walk found
 */
public record GeometryOut(
        String verdict,
        boolean mayRun,
        @LengthFeet Length snow,
        @LoadText Load tension,
        @LengthInches Length ice,
        @LengthFeet Length worstClearance,
        @LengthFeet Length worstMargin,
        @LengthFeet Length worstStation,
        List<LineFindingOut> findings) {

    /** A walk's shape. */
    public GeometryOut {
        findings = List.copyOf(findings);
    }
}
