package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;
import java.util.Optional;

/**
 * What a team is carrying, and how far up it gets them.
 *
 * <p>A twenty foot ladder is not a twenty foot reach, because somebody has to stand on it and work. A
 * rope system reaches as far down as it is long.
 *
 * @param ladder the ladder's length, empty when the team carries none
 * @param rope the rope system's length, empty when the team carries none
 * @param seats rescue seats carried
 * @param harnesses harnesses carried
 * @param radios radios carried
 */
public record RescueKit(Optional<Length> ladder, Optional<Length> rope, int seats, int harnesses, int radios) {

    /** The part of a ladder taken up by standing on it and working. */
    public static final Length LADDER_WORKING_ALLOWANCE = Length.ofFeet(4);

    /**
     * A kit.
     *
     * @throws EvacuationError when the ladder or the rope has no length, or a count is negative
     */
    public RescueKit {
        Objects.requireNonNull(ladder, "ladder");
        Objects.requireNonNull(rope, "rope");
        requireLength("ladder", ladder);
        requireLength("rope", rope);
        requireCount("seats", seats);
        requireCount("harnesses", harnesses);
        requireCount("radios", radios);
    }

    private static void requireLength(String name, Optional<Length> piece) {
        if (piece.isPresent() && piece.get().mils() <= 0) {
            throw new EvacuationError("a " + name + " with no length is not kit");
        }
    }

    private static void requireCount(String name, int count) {
        if (count < 0) {
            throw new EvacuationError("a kit cannot hold negative " + name);
        }
    }

    /**
     * How high the ladder lets the team work: its length less the room to stand and work.
     *
     * @return the reach, zero with no ladder or one too short to stand on
     */
    public Length ladderReach() {
        return ladder.map(length -> length.minus(LADDER_WORKING_ALLOWANCE))
                .filter(reach -> reach.mils() > 0)
                .orElse(Length.ZERO);
    }

    /**
     * How far down the rope system lowers.
     *
     * @return its length, zero with no rope
     */
    public Length ropeReach() {
        return rope.orElse(Length.ZERO);
    }

    /**
     * How high the team can work with this kit.
     *
     * @return the longer of the ladder's reach and the rope's
     */
    public Length reach() {
        Length ladderReach = ladderReach();
        Length ropeReach = ropeReach();
        return ladderReach.compareTo(ropeReach) >= 0 ? ladderReach : ropeReach;
    }

    /**
     * Whether a carrier at that height is within reach.
     *
     * @param height the carrier's height off the ground
     * @return true up to and including the reach
     */
    public boolean reaches(Length height) {
        return height.compareTo(reach()) <= 0;
    }

    /**
     * Whether the ladder alone will not do it.
     *
     * @param height the carrier's height off the ground
     * @return true above the ladder's reach
     */
    public boolean needsARopeFor(Length height) {
        return height.compareTo(ladderReach()) > 0;
    }

    /**
     * Whether the kit is enough for a team: everybody working aloft needs a harness, and the team
     * needs a radio and a seat.
     *
     * @param people the team's size
     * @return true with a harness each, a radio and a seat
     */
    public boolean isComplete(int people) {
        return harnesses >= people && radios >= 1 && seats >= 1;
    }

    /**
     * Whether the kit holds a piece.
     *
     * @param item the piece
     * @return true when it carries at least one
     */
    public boolean carries(KitItem item) {
        return switch (item) {
            case LADDER -> ladder.isPresent();
            case ROPE_SYSTEM -> rope.isPresent();
            case SEAT -> seats > 0;
            case HARNESS -> harnesses > 0;
            case RADIO -> radios > 0;
        };
    }

    /** Such as {@code reach 120 ft, 2 seats}. */
    @Override
    public String toString() {
        return "reach " + Decimals.fixed(reach().feet(), 0) + " ft, " + seats + " seats";
    }
}
