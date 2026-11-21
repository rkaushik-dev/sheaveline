package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;

/**
 * The hour meter and the cycle counter as they were read one evening.
 *
 * @param lift the machine
 * @param day the day
 * @param hours what the hour meter said, as the decimal it shows
 * @param cycles what the cycle counter said
 * @param readBy who read them
 * @param meterReplaced whether the meter was new that morning
 */
public record MeterOut(
        @LiftCodeText LiftCode lift, LocalDate day, String hours, long cycles, String readBy, boolean meterReplaced) {

    /**
     * A reading as the tablet shows it.
     *
     * @param reading the reading
     * @return its shape
     */
    public static MeterOut of(MeterReading reading) {
        return new MeterOut(
                reading.lift(),
                reading.day(),
                reading.hours().toString(),
                reading.cycles(),
                reading.readBy(),
                reading.meterReplaced());
    }
}
