package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.plant.Grip;
import io.github.rkaushikdev.sheaveline.plant.GripType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One detachable grip pulled on the bench until it moved.
 *
 * <p>A detachable grip is asked to do two contradictory things. It has to let go cleanly in the
 * terminal and it has to hold absolutely on the line, and the only way to know it still does the
 * second is to pull one until it slips and see where it goes.
 *
 * @param carrier the carrier it came off
 * @param tested the day of the test
 * @param tester who ran it
 * @param slippedAt the pull at which it moved
 * @param rated the pull it is rated to let go at
 */
public record SlipTest(int carrier, LocalDate tested, String tester, Load slippedAt, Load rated) {

    /** The band either side of the rating a grip must slip inside, as a fraction of it. */
    public static final BigDecimal SLIP_TOLERANCE = new BigDecimal("0.15");

    /**
     * A test that could have been run.
     *
     * @throws RopeworkError when the carrier number is under one, the test is unsigned, or either
     *     figure is not positive
     */
    public SlipTest {
        Objects.requireNonNull(tested, "tested");
        Objects.requireNonNull(tester, "tester");
        Objects.requireNonNull(slippedAt, "slippedAt");
        Objects.requireNonNull(rated, "rated");
        if (carrier < 1) {
            throw new RopeworkError("a slip test names the carrier it came off");
        }
        if (tester.strip().isEmpty()) {
            throw new RopeworkError("a slip test is signed by whoever ran it");
        }
        if (slippedAt.pounds() <= 0) {
            throw new RopeworkError("a grip that never moved has no slip figure");
        }
        if (rated.pounds() <= 0) {
            throw new RopeworkError("a slip test is read against a rated figure");
        }
    }

    /**
     * A test read against the rating of the grip it came off.
     *
     * @param carrier the carrier it came off
     * @param tested the day of the test
     * @param tester who ran it
     * @param slippedAt the pull at which it moved
     * @param grip the grip
     * @return the test
     * @throws RopeworkError when the grip is not detachable
     */
    public static SlipTest of(int carrier, LocalDate tested, String tester, Load slippedAt, Grip grip) {
        if (grip.kind() != GripType.DETACHABLE) {
            throw new RopeworkError("only a detachable grip goes on the slip bench");
        }
        return new SlipTest(carrier, tested, tester, slippedAt, grip.ratedSlip());
    }

    /**
     * The pulls a sound grip slips between.
     *
     * @return fifteen percent either side of the rating
     */
    public Band band() {
        Load margin = rated.scaled(SLIP_TOLERANCE);
        return new Band(rated.minus(margin), rated.plus(margin));
    }

    /**
     * How far the slip was from the rating.
     *
     * @return the difference as a fraction of the rating, negative when it slipped early
     */
    public BigDecimal deviation() {
        return slippedAt.minus(rated).ratio(rated);
    }

    /**
     * Whether the grip slipped inside the band.
     *
     * @return true from the low end to the high end, both included
     */
    public boolean passed() {
        Band band = band();
        return band.low().compareTo(slippedAt) <= 0 && slippedAt.compareTo(band.high()) <= 0;
    }

    /**
     * Whether the grip let go below the band, as opposed to holding too hard.
     *
     * @return true when it slipped early
     */
    public boolean slippedEarly() {
        return slippedAt.compareTo(band().low()) < 0;
    }

    /** Such as {@code carrier 7 slipped at 2,550 lb, pass}. */
    @Override
    public String toString() {
        String outcome = passed() ? "pass" : "fail";
        return "carrier " + carrier + " slipped at " + slippedAt.formatPounds() + ", " + outcome;
    }

    /**
     * The pulls a sound grip slips between.
     *
     * @param low the lowest
     * @param high the highest
     */
    public record Band(Load low, Load high) {

        /** A band. */
        public Band {
            Objects.requireNonNull(low, "low");
            Objects.requireNonNull(high, "high");
        }
    }
}
