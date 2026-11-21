package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Comparator;
import java.util.List;

/**
 * What the ground is doing, station by station, with open ground as the default.
 *
 * @param segments the stretches with a recorded use
 */
public record GroundUseMap(List<UseSegment> segments) {

    /** A map, its stretches copied. */
    public GroundUseMap {
        segments = List.copyOf(segments);
    }

    /** A map with nothing recorded, so open ground everywhere. */
    public GroundUseMap() {
        this(List.of());
    }

    /**
     * A map of the given stretches, put in order up the line.
     *
     * @param segments the stretches, in any order
     * @return the map
     */
    public static GroundUseMap of(List<UseSegment> segments) {
        return new GroundUseMap(segments.stream()
                .sorted(Comparator.comparingLong(segment -> segment.start().mils()))
                .toList());
    }

    /**
     * The most demanding use recorded at that station, the first recorded of them on a tie.
     *
     * @param station the station
     * @return the use, open ground where nothing is recorded
     */
    public GroundUse useAt(Length station) {
        return segments.stream()
                .filter(segment -> segment.covers(station))
                .map(UseSegment::use)
                .max(Comparator.comparingLong(use -> use.minimum().mils()))
                .orElse(GroundUse.OPEN);
    }

    /**
     * The clearance wanted at that station.
     *
     * @param station the station
     * @return the minimum for the use there
     */
    public Length minimumAt(Length station) {
        return useAt(station).minimum();
    }
}
