package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A stop, with what follows from its kind already worked out.
 *
 * @param lift the machine
 * @param kind why it stopped
 * @param stopped when
 * @param stoppedBy who stopped it
 * @param note anything worth saying
 * @param restarted when it ran again, once it has
 * @param restartedBy who ran it, once somebody has
 * @param lineChecked whether the line has been ridden since
 * @param open whether it is still stopped
 * @param reportable whether the board hears about it
 * @param countsAgainstTheMachine whether it is the machine's fault
 * @param length how long it lasted, once it is over
 */
public record StopOut(
        @LiftCodeText LiftCode lift,
        String kind,
        LocalDateTime stopped,
        String stoppedBy,
        String note,
        Optional<LocalDateTime> restarted,
        Optional<String> restartedBy,
        boolean lineChecked,
        boolean open,
        boolean reportable,
        boolean countsAgainstTheMachine,
        @DurationText Optional<Duration> length) {

    /**
     * A stop as the tablet shows it.
     *
     * @param stop the stop
     * @return its shape
     */
    public static StopOut of(StopEvent stop) {
        return new StopOut(
                stop.lift(),
                stop.kind().value(),
                stop.stopped(),
                stop.stoppedBy(),
                stop.note(),
                stop.restarted(),
                stop.restartedBy(),
                stop.lineChecked(),
                stop.isOpen(),
                stop.kind().isReportable(),
                stop.kind().countsAgainstTheMachine(),
                stop.isOpen() ? Optional.empty() : Optional.of(stop.length()));
    }
}
