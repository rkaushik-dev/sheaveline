package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.util.Valued;

/** What a card lets somebody do. */
public enum Endorsement implements Valued {
    /** Run the bottom of a machine or a mid station. */
    OPERATOR("operator"),
    /** Run the top of a machine, and anything an operator may. */
    LEAD_OPERATOR("lead operator"),
    /** Stand as the mechanic on site. */
    MECHANIC("mechanic"),
    /** Work an evacuation. */
    EVACUATION("evacuation");

    private final String value;

    Endorsement(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the card lets somebody run a machine.
     *
     * @return true for the operator and lead operator cards
     */
    public boolean isOperational() {
        return this == OPERATOR || this == LEAD_OPERATOR;
    }

    /**
     * The endorsement a post asks for.
     *
     * @param post the post
     * @return an operator card for the bottom and a mid station, a lead card for the top, and a
     *     mechanic card for the mechanic on site
     */
    public static Endorsement neededFor(Post post) {
        return switch (post) {
            case BOTTOM, MID -> OPERATOR;
            case TOP -> LEAD_OPERATOR;
            case MECHANIC -> MECHANIC;
        };
    }
}
