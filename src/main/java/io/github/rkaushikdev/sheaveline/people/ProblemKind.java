package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What is wrong with a roster. */
public enum ProblemKind implements Valued {
    /** A required post nobody is standing at. */
    UNCOVERED("post uncovered"),
    /** Somebody at a post their cards do not reach. */
    UNCARDED("no card for the post"),
    /** Somebody whose card for the post does not cover this class of machine. */
    WRONG_CLASS("card does not cover the machine"),
    /** Somebody whose card for the post and the machine has run out. */
    LAPSED("card has run out");

    private final String value;

    ProblemKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the problem is with somebody on the roster rather than an empty post.
     *
     * @return false only for an uncovered post
     */
    public boolean isAboutAPerson() {
        return this != UNCOVERED;
    }
}
