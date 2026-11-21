package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;

/**
 * One caliper reading, worked out.
 *
 * @param station how far along the rope it was taken
 * @param mean the mean of the two diameters
 * @param ovality how far apart the two diameters are
 */
public record DiameterReadingOut(@LengthFeet Length station, @LengthInches Length mean, @LengthInches Length ovality) {

    /**
     * The reading as the tablet shows it.
     *
     * @param reading the reading
     * @return its shape
     */
    public static DiameterReadingOut of(DiameterReading reading) {
        return new DiameterReadingOut(reading.station(), reading.mean(), reading.ovality());
    }
}
