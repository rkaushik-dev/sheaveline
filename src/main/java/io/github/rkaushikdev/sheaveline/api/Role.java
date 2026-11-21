package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * What a key is for. The role decides what the key may do rather than who holds it, and each role
 * covers everything below it.
 */
public enum Role implements Valued {
    /** Reads anything: the office screen, the auditor. */
    READER("reader", 0),
    /** Writes the day book: the terminal tablets. */
    OPERATOR("operator", 1),
    /** Signs off inspections and rope walks. */
    MECHANIC("mechanic", 2),
    /** Files with the board, closes orders, takes machines off the register. */
    MANAGER("manager", 3);

    private final String value;
    private final int rank;

    Role(String value, int rank) {
        this.value = value;
        this.rank = rank;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Where the role stands, from the reader at nought up.
     *
     * @return the rank
     */
    public int rank() {
        return rank;
    }

    /**
     * Whether a key with this role may do what a key with another may: a manager key does anything
     * an operator key does, and so on down.
     *
     * @param wanted the role a job needs
     * @return true when this role is the same or above
     */
    public boolean covers(Role wanted) {
        return rank >= wanted.rank;
    }
}
