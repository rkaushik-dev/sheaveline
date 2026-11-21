package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The load point, and whether the crew can keep up with it.
 *
 * @param transit how the carrier comes through the terminal
 * @param seats the seats on each carrier
 * @param attendants the crew loading it
 */
public record LoadingGate(TerminalTransit transit, int seats, int attendants) {

    /** Seconds one attendant takes to seat one passenger. */
    public static final BigDecimal SECONDS_PER_PASSENGER = new BigDecimal("1.4");

    /** Time on top of the seating for the bar and the gate. */
    public static final Duration BOARDING_ALLOWANCE = Duration.ofSeconds(2);

    /** The most attendants a load point is ever staffed with. */
    private static final int MOST_ATTENDANTS = 4;

    /**
     * A staffed gate loading at least one seat.
     *
     * @throws CapacityError when there are no seats or no attendants
     */
    public LoadingGate {
        Objects.requireNonNull(transit, "transit");
        if (seats < 1) {
            throw new CapacityError("a gate loads at least one seat");
        }
        if (attendants < 1) {
            throw new CapacityError("a load point is staffed");
        }
    }

    /**
     * A gate with one attendant.
     *
     * @param transit how the carrier comes through the terminal
     * @param seats the seats on each carrier
     */
    public LoadingGate(TerminalTransit transit, int seats) {
        this(transit, seats, 1);
    }

    /**
     * Seconds to seat a full carrier, which a second attendant shortens.
     *
     * @return the whole seconds of seating plus the boarding allowance
     */
    public Duration timeNeeded() {
        BigDecimal perRow = Decimals.divide(Decimals.multiply(Decimals.of(seats), SECONDS_PER_PASSENGER), attendants);
        return Duration.ofSeconds(perRow.toBigInteger().longValueExact()).plus(BOARDING_ALLOWANCE);
    }

    /**
     * How long each carrier is at the gate.
     *
     * @return the transit's loading window
     */
    public Duration window() {
        return transit.loadingWindow();
    }

    /**
     * Whether the crew fills a carrier inside its window.
     *
     * @return true when the window is at least the time needed
     */
    public boolean keepsUp() {
        return window().compareTo(timeNeeded()) >= 0;
    }

    /**
     * The time to spare in each window.
     *
     * @return the window less the time needed, zero when the crew cannot keep up
     */
    public Duration slack() {
        Duration window = window();
        Duration needed = timeNeeded();
        return window.compareTo(needed) >= 0 ? window.minus(needed) : Duration.ZERO;
    }

    /**
     * The fewest attendants that make the window work.
     *
     * @return one to four
     * @throws CapacityError when even four cannot keep up
     */
    public int attendantsNeeded() {
        for (int count = 1; count <= MOST_ATTENDANTS; count++) {
            if (withAttendants(count).keepsUp()) {
                return count;
            }
        }
        throw new CapacityError("no reasonable crew can load that carrier in that window");
    }

    /**
     * The same gate with another crew.
     *
     * @param changed the attendants
     * @return the new gate
     */
    public LoadingGate withAttendants(int changed) {
        return new LoadingGate(transit, seats, changed);
    }
}
