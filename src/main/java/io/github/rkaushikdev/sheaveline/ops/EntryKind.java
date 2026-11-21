package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What sort of line this is in the day book. */
public enum EntryKind implements Valued {
    /** Something an operator thought worth writing down. */
    NOTE("note"),
    /** The machine opened. */
    OPENED("opened"),
    /** The machine was swept and closed. */
    CLOSED("closed"),
    /** One crew handed the machine to the next. */
    HANDOVER("handover"),
    /** Somebody from outside the department came by. */
    VISITOR("visitor");

    private final String value;

    EntryKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the book writes this line itself rather than somebody typing it.
     *
     * @return true for the opening and closing lines
     */
    public boolean isStructural() {
        return this == OPENED || this == CLOSED;
    }
}
