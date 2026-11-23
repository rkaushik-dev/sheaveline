package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * A hold still open in the day book, and whether the reading lets it come off.
 *
 * @param reason which weather it was called for
 * @param started when it was called
 * @param startedBy who called it
 * @param detail the figures it was called on
 * @param mayClear whether the latest reading, and for lightning the clock, lets it come off now
 * @param clearsAt for a lightning hold, the moment it may come off if nothing else strikes
 */
public record OpenHoldOut(
        String reason,
        LocalDateTime started,
        String startedBy,
        String detail,
        boolean mayClear,
        Optional<LocalDateTime> clearsAt) {

    /** An open hold's shape. */
    public OpenHoldOut {
        Objects.requireNonNull(clearsAt, "clearsAt");
    }

    /**
     * An open hold as the tablet shows it.
     *
     * @param hold the hold
     * @param mayClear whether it may come off now
     * @param clearsAt when a lightning hold runs out, empty for any other
     * @return its shape
     */
    public static OpenHoldOut of(HoldRecord hold, boolean mayClear, Optional<LocalDateTime> clearsAt) {
        return new OpenHoldOut(
                hold.reason().value(),
                hold.started(),
                hold.startedBy(),
                hold.detail(),
                mayClear,
                hold.reason() == HoldReason.LIGHTNING ? clearsAt : Optional.empty());
    }
}
