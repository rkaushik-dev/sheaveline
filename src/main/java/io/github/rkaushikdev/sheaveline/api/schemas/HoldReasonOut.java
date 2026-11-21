package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.weather.Hold;

/**
 * One reason the weather holds a machine.
 *
 * @param reason which weather
 * @param detail what the reading said
 * @param clearsOnReading whether a better reading lifts it, rather than a clock
 */
public record HoldReasonOut(String reason, String detail, boolean clearsOnReading) {

    /**
     * A reason as the tablet shows it.
     *
     * @param hold the reason
     * @return its shape
     */
    public static HoldReasonOut of(Hold hold) {
        return new HoldReasonOut(
                hold.reason().value(), hold.detail(), hold.reason().clearsOnReading());
    }
}
