package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** How much of the plan a drill actually exercised. */
public enum DrillKind implements Valued {
    /** Talked through round a table, nobody on the line. */
    TABLETOP("tabletop"),
    /** Some carriers worked, not the whole line. */
    PARTIAL("partial"),
    /** The whole plan worked on the line. */
    FULL_LINE("full line");

    private final String value;

    DrillKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the drill works real carriers.
     *
     * @return false only for a tabletop drill
     */
    public boolean putsPeopleOnTheLine() {
        return this != TABLETOP;
    }

    /**
     * Whether this kind of drill can count for the machine's annual drill.
     *
     * @return true only for a full line drill
     */
    public boolean satisfiesTheAnnual() {
        return this == FULL_LINE;
    }
}
