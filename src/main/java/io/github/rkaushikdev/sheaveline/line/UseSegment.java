package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Objects;

/**
 * A stretch of the line where the ground is used for one thing.
 *
 * @param start the first station of the stretch
 * @param end the last station, further up the line
 * @param use what the ground is used for
 */
public record UseSegment(Length start, Length end, GroundUse use) {

    /**
     * A stretch that runs up the line.
     *
     * @throws LineError when the end is not past the start
     */
    public UseSegment {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(use, "use");
        if (end.compareTo(start) <= 0) {
            throw new LineError("a use segment runs up the line");
        }
    }

    /**
     * Whether the stretch takes in a station, both ends included.
     *
     * @param station the station
     * @return true from the start to the end
     */
    public boolean covers(Length station) {
        return start.compareTo(station) <= 0 && station.compareTo(end) <= 0;
    }
}
