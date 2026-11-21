package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.ropework.LayReading;
import jakarta.validation.constraints.NotNull;

/**
 * One lay length reading, as the mechanic types it.
 *
 * @param station how far along the rope it was taken
 * @param measured the lay length measured there
 */
public record LayReadingIn(@NotNull @LengthFeet Length station, @NotNull @LengthInches Length measured) {

    /**
     * The reading in the domain's own terms.
     *
     * @return the reading
     */
    public LayReading toDomain() {
        return new LayReading(station, measured);
    }
}
