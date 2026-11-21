package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** How much a defect costs while it is open. */
public enum DefectLevel implements Valued {
    /** Worth writing down, and nothing more. */
    OBSERVATION("observation"),
    /** Something to fix, on a work order. */
    DEFECT("defect"),
    /** Something that stops the machine until it is fixed. */
    SERIOUS("serious");

    private final String value;

    DefectLevel(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether an open defect at this level keeps the machine from carrying the public.
     *
     * @return true only for a serious defect
     */
    public boolean stopsTheMachine() {
        return this == SERIOUS;
    }

    /**
     * Whether a defect at this level is turned into a work order.
     *
     * @return true for everything but an observation
     */
    public boolean needsAWorkOrder() {
        return this != OBSERVATION;
    }
}
