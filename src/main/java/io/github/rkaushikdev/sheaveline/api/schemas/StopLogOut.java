package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.StopLog;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.util.List;

/**
 * Every stop that touched a day, totalled.
 *
 * @param lift the machine
 * @param stops the stops
 * @param downtime how long it stood in all
 * @param chargeable how much of that was the machine's fault
 */
public record StopLogOut(
        @LiftCodeText LiftCode lift,
        List<StopOut> stops,
        @DurationText Duration downtime,
        @DurationText Duration chargeable) {

    /** A stop log's shape. */
    public StopLogOut {
        stops = List.copyOf(stops);
    }

    /**
     * The stops as the tablet shows them.
     *
     * @param log the stops
     * @return their shape
     */
    public static StopLogOut of(StopLog log) {
        return new StopLogOut(
                log.lift(), log.stops().stream().map(StopOut::of).toList(), log.downtime(), log.chargeableDowntime());
    }
}
