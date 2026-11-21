package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What the machine may do today: the one word the mountain manager wants. */
public enum ServiceStatus implements Valued {
    /** Nothing against it. */
    IN_SERVICE("in service"),
    /** It may carry the public, with something written on the daily sheet. */
    RESTRICTED("restricted"),
    /** It does not carry the public. */
    OUT_OF_SERVICE("out of service");

    private final String value;

    ServiceStatus(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the machine may carry the public at this status.
     *
     * @return false only when out of service
     */
    public boolean mayCarryPublic() {
        return this != OUT_OF_SERVICE;
    }

    /**
     * Whether the daily sheet carries a note about the machine at this status.
     *
     * @return true for anything but in service
     */
    public boolean needsANoteOnTheSheet() {
        return this != IN_SERVICE;
    }
}
