package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What a magnetic test trace showed at one place. */
public enum IndicationKind implements Valued {
    /** A gradual thinning of the steel, measured as a percentage. */
    LOSS_OF_AREA("loss of metallic area"),
    /** A step in the trace at one place: a broken wire or a group of them. */
    LOCAL_FAULT("local fault"),
    /** Rust inside the rope, measured as a percentage. */
    CORROSION("corrosion");

    private final String value;

    IndicationKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the magnitude is a percentage rather than a count of wires.
     *
     * @return false only for a local fault
     */
    public boolean isMeasuredAsPercent() {
        return this != LOCAL_FAULT;
    }
}
