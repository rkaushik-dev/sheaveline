package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import jakarta.validation.constraints.NotNull;

/**
 * One caliper reading, as the mechanic types it.
 *
 * @param station how far along the rope it was taken
 * @param acrossCrowns the diameter across the strand crowns
 * @param acrossValleys the diameter across the valleys between them
 */
public record DiameterReadingIn(
        @NotNull @LengthFeet Length station,
        @NotNull @LengthInches Length acrossCrowns,
        @NotNull @LengthInches Length acrossValleys) {

    /**
     * The reading in the domain's own terms.
     *
     * @return the reading
     */
    public DiameterReading toDomain() {
        return new DiameterReading(station, acrossCrowns, acrossValleys);
    }
}
