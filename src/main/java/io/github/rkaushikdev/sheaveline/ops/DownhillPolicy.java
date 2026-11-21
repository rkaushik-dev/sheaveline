package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Objects;
import java.util.Optional;

/**
 * What one class of machine may do when the traffic runs the other way.
 *
 * <p>Loading at the top is not the reverse of loading at the bottom. The attendant is standing where
 * the carriers are moving away rather than toward them, and the rope has to be slowed because a
 * rider stepping off downhill has the carrier catching up with them rather than leaving. A tow does
 * not carry down at all.
 *
 * @param allowed whether the class carries down at all
 * @param speed the fastest the rope may run downhill, or empty when the class sets no limit
 * @param carriersAtOnce how many carriers may be loaded downhill at once
 * @param attendantAtTop whether somebody has to be at the top terminal
 */
public record DownhillPolicy(boolean allowed, Optional<Speed> speed, int carriersAtOnce, boolean attendantAtTop) {

    /** A policy. */
    public DownhillPolicy {
        Objects.requireNonNull(speed, "speed");
    }

    /**
     * The policy for a class of machine.
     *
     * @param liftClass the class
     * @return what that class may do downhill
     */
    public static DownhillPolicy forClass(LiftClass liftClass) {
        return switch (liftClass) {
            case FIXED_CHAIR -> new DownhillPolicy(true, Optional.of(Speed.ofFeetPerMinute(300)), 2, true);
            case DETACHABLE_CHAIR -> new DownhillPolicy(true, Optional.of(Speed.ofFeetPerMinute(500)), 4, true);
            case PULSE_GONDOLA -> new DownhillPolicy(true, Optional.of(Speed.ofFeetPerMinute(400)), 1, true);
            case DETACHABLE_GONDOLA -> new DownhillPolicy(true, Optional.of(Speed.ofFeetPerMinute(500)), 6, true);
            case AERIAL_TRAM -> new DownhillPolicy(true, Optional.empty(), 1, true);
            case SURFACE_TOW -> new DownhillPolicy(false, Optional.empty(), 0, false);
        };
    }
}
