package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.util.Valued;

/** What happened to the people, mildest first, and how soon the board hears about it. */
public enum InjuryLevel implements Valued {
    /** Nobody was hurt. */
    NONE("none", Incident.MONTHLY),
    /** Patched up by patrol on the spot. */
    FIRST_AID("first aid", Incident.MONTHLY),
    /** Taken down the hill to a clinic. */
    TRANSPORTED("transported", Incident.NEXT_DAY),
    /** Kept in hospital. */
    ADMITTED("admitted to hospital", Incident.IMMEDIATE),
    /** Somebody died. */
    FATAL("fatal", Incident.IMMEDIATE);

    private final String value;
    private final Duration deadline;

    InjuryLevel(String value, Duration deadline) {
        this.value = value;
        this.deadline = deadline;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * How long the department has to tell the board, judged on the injury alone.
     *
     * @return the deadline
     */
    public Duration deadline() {
        return deadline;
    }

    /**
     * Whether the injury is serious enough to hold the machine.
     *
     * @return true for a hospital admission or a death
     */
    public boolean isSerious() {
        return this == ADMITTED || this == FATAL;
    }
}
