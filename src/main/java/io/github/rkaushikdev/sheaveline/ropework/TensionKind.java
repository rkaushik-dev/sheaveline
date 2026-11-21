package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * What keeps the rope tight.
 *
 * <p>A counterweight holds one tension no matter where the carriage sits, because it is a weight on a
 * rope. A hydraulic ram holds a tension only inside a pressure band and drifts outside it. A screw
 * take up holds whatever it was wound to and nothing else, which is why nobody uses one on a
 * passenger lift any more.
 */
public enum TensionKind implements Valued {
    /** A weight hanging in the return terminal. */
    COUNTERWEIGHT("counterweight"),
    /** A ram pushing the carriage. */
    HYDRAULIC("hydraulic"),
    /** A screw wound to a position. */
    SCREW("screw take up");

    private final String value;

    TensionKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the tension stays the same wherever the carriage is. A weight is a weight.
     *
     * @return true for a counterweight
     */
    public boolean holdsConstant() {
        return this == COUNTERWEIGHT;
    }

    /**
     * Whether the system has no pump, no pressure switch, nothing to fail closed.
     *
     * @return true for a counterweight or a screw
     */
    public boolean isPassive() {
        return this == COUNTERWEIGHT || this == SCREW;
    }

    /**
     * Whether the system may tension a lift that carries passengers.
     *
     * @return false for a screw take up
     */
    public boolean allowedOnPassengerLifts() {
        return this != SCREW;
    }
}
