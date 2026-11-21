package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What the gauge and the tape said on one morning.
 *
 * @param taken the day of the reading
 * @param carriage where the carriage was, measured from the closed end
 * @param measured the tension the gauge read
 * @param temperature the air temperature at the time
 */
public record TensionReading(LocalDate taken, Length carriage, Load measured, Temperature temperature) {

    /**
     * A reading with real numbers in it.
     *
     * @throws RopeworkError when the tension is not positive or the carriage is behind the closed end
     */
    public TensionReading {
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(carriage, "carriage");
        Objects.requireNonNull(measured, "measured");
        Objects.requireNonNull(temperature, "temperature");
        if (measured.pounds() <= 0) {
            throw new RopeworkError("a tension reading is a positive load");
        }
        if (carriage.mils() < 0) {
            throw new RopeworkError("a carriage reading is measured from the closed end");
        }
    }

    /**
     * How far the reading is from what the system should hold.
     *
     * @param system the system it came off
     * @return the measured tension less nominal, signed
     */
    public Load deviationFrom(TensionSystem system) {
        return measured.minus(system.nominal());
    }

    /**
     * Whether the reading is inside the system's band.
     *
     * @param system the system it came off
     * @return true when the system accepts it
     */
    public boolean agreesWith(TensionSystem system) {
        return system.accepts(measured);
    }

    /**
     * How far the carriage has moved since the system was recorded.
     *
     * @param system the system it came off
     * @return the movement, negative toward the closed end
     */
    public Length carriageMoved(TensionSystem system) {
        return carriage.minus(system.carriage());
    }

    /** Such as {@code 34,800 lb on 2025-01-15}. */
    @Override
    public String toString() {
        return measured.formatPounds() + " on " + taken;
    }
}
