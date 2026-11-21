package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.util.Valued;

/** What the machine did, or what was done to it, and how soon the board hears about it. */
public enum IncidentKind implements Valued {
    /** Something went wrong getting on. */
    LOADING("loading", Incident.MONTHLY),
    /** Something went wrong getting off. */
    UNLOADING("unloading", Incident.MONTHLY),
    /** A rider came off a carrier on the line. */
    FALL_FROM_CARRIER("fall from carrier", Incident.IMMEDIATE),
    /** The rope came off the sheaves. */
    DEROPEMENT("deropement", Incident.IMMEDIATE),
    /** The line ran backwards. */
    ROLLBACK("rollback", Incident.IMMEDIATE),
    /** Clothing, a pack or a person caught in the machine. */
    ENTANGLEMENT("entanglement", Incident.NEXT_DAY),
    /** Riders were taken off the line by the patrol. */
    EVACUATION("evacuation", Incident.NEXT_DAY),
    /** A structure gave way. */
    STRUCTURAL("structural failure", Incident.IMMEDIATE),
    /** The electrics failed. */
    ELECTRICAL("electrical failure", Incident.NEXT_DAY);

    private final String value;
    private final Duration deadline;

    IncidentKind(String value, Duration deadline) {
        this.value = value;
        this.deadline = deadline;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * How long the department has to tell the board, judged on what the machine did alone.
     *
     * @return the deadline
     */
    public Duration deadline() {
        return deadline;
    }

    /**
     * Whether the machine stays stopped until somebody has looked at it.
     *
     * @return true for a deropement, a rollback, a structural failure and a fall from a carrier
     */
    public boolean stopsTheMachine() {
        return this == DEROPEMENT || this == ROLLBACK || this == STRUCTURAL || this == FALL_FROM_CARRIER;
    }
}
