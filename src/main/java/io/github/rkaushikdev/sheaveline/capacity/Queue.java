package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One machine, one maze and the rate people are turning up at.
 *
 * <p>Capacity is what the machine can do. The queue is what the public experiences, and the two come
 * apart the moment arrivals beat capacity, because from then on the wait grows by the difference every
 * hour and nothing about the machine changes at all.
 *
 * <p>The singles lane is the cheapest capacity on the mountain. It does not make the lift faster; it
 * fills the empty seat beside the pair who came together, and on a quad that is worth more than a
 * hundred feet a minute. Every count and time here is whole, any fraction dropped.
 *
 * @param capacity what the machine moves
 * @param maze the corral
 * @param arrivalsAnHour people turning up an hour
 */
public record Queue(UphillCapacity capacity, Maze maze, int arrivalsAnHour) {

    /** What a singles lane adds to the loading efficiency. */
    public static final BigDecimal SINGLES_LIFT = new BigDecimal("0.08");

    /** The best loading efficiency any crew reaches, singles lane or not. */
    public static final BigDecimal MAXIMUM_OCCUPANCY = new BigDecimal("0.98");

    /**
     * A queue with people arriving at a real rate.
     *
     * @throws CapacityError when the arrival rate is negative
     */
    public Queue {
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(maze, "maze");
        if (arrivalsAnHour < 0) {
            throw new CapacityError("people cannot arrive at a negative rate");
        }
    }

    /**
     * The loading efficiency at this maze. A singles lane lifts it and nothing else.
     *
     * @return the machine's efficiency, raised by the singles lift up to the maximum when the maze has
     *     a singles lane
     */
    public BigDecimal occupancy() {
        if (!maze.singlesLane()) {
            return capacity.occupancy();
        }
        return capacity.occupancy().add(SINGLES_LIFT, Decimals.CONTEXT).min(MAXIMUM_OCCUPANCY);
    }

    /**
     * People an hour the machine actually clears from this maze.
     *
     * @return persons an hour at this maze's loading efficiency
     */
    public int throughput() {
        return capacity.atOccupancy(occupancy()).personsPerHour();
    }

    /**
     * Whether the machine clears people as fast as they arrive.
     *
     * @return true when the arrivals are no more than the throughput
     */
    public boolean keepsUp() {
        return arrivalsAnHour <= throughput();
    }

    /**
     * People an hour arriving that the machine cannot take.
     *
     * @return the excess of arrivals over throughput, never negative
     */
    public int surplusAnHour() {
        return Math.max(0, arrivalsAnHour - throughput());
    }

    /**
     * Seats an hour the machine could fill and nobody is there to take.
     *
     * @return the excess of throughput over arrivals, never negative
     */
    public int spareAnHour() {
        return Math.max(0, throughput() - arrivalsAnHour);
    }

    /**
     * How many people are standing there after a stretch at this rate.
     *
     * @param elapsed the time at this rate
     * @return the people queued
     */
    public int lengthAfter(Duration elapsed) {
        return Decimals.multiply(Decimals.of(surplusAnHour()), elapsed.hours())
                .toBigInteger()
                .intValueExact();
    }

    /**
     * How long the last person to join waits, which is the number on the sign.
     *
     * @param elapsed the time at this rate
     * @return the wait, zero with nobody standing
     * @throws CapacityError when people are standing and the machine clears nobody
     */
    public Duration waitAfter(Duration elapsed) {
        int standing = lengthAfter(elapsed);
        if (standing == 0) {
            return Duration.ZERO;
        }
        if (throughput() == 0) {
            throw new CapacityError("a machine that clears nobody has no wait to report");
        }
        return timeToClear(standing);
    }

    /**
     * Whether the queue is out of the maze and into the car park.
     *
     * @param elapsed the time at this rate
     * @return true when more people are standing than the maze holds
     */
    public boolean overflowsAfter(Duration elapsed) {
        return lengthAfter(elapsed) > maze.holds();
    }

    /**
     * How long until the maze is full.
     *
     * @return the time, or empty when the machine keeps up and the maze never fills
     */
    public Optional<Duration> fillsIn() {
        int surplus = surplusAnHour();
        if (surplus == 0) {
            return Optional.empty();
        }
        BigDecimal hours = Decimals.divide(Decimals.of(maze.holds()), surplus);
        return Optional.of(Duration.ofSeconds(Decimals.multiply(hours, Duration.SECONDS_PER_HOUR)
                .toBigInteger()
                .longValueExact()));
    }

    /**
     * How long to clear a queue of that size once the arrivals stop.
     *
     * @param standing the people queued
     * @return the time to load them all
     * @throws CapacityError when the queue is negative or the machine clears nobody
     */
    public Duration clearsIn(int standing) {
        if (standing < 0) {
            throw new CapacityError("a queue cannot be shorter than nobody");
        }
        if (throughput() == 0) {
            throw new CapacityError("a machine that clears nobody never clears a queue");
        }
        return timeToClear(standing);
    }

    /**
     * The same queue with another arrival rate.
     *
     * @param changed people an hour
     * @return the new queue
     */
    public Queue withArrivals(int changed) {
        return new Queue(capacity, maze, changed);
    }

    /**
     * The same machine and arrivals at another maze.
     *
     * @param changed the maze
     * @return the new queue
     */
    public Queue withMaze(Maze changed) {
        return new Queue(capacity, changed, arrivalsAnHour);
    }

    /** The throughput and whether it keeps up, such as {@code 2,040 an hour, 460 an hour behind}. */
    @Override
    public String toString() {
        String state = keepsUp() ? "keeping up" : surplusAnHour() + " an hour behind";
        return String.format(Locale.ROOT, "%,d an hour, %s", throughput(), state);
    }

    private Duration timeToClear(int standing) {
        BigDecimal seconds =
                Decimals.divide(Decimals.multiply(Decimals.of(standing), Duration.SECONDS_PER_HOUR), throughput());
        return Duration.ofSeconds(seconds.toBigInteger().longValueExact());
    }
}
