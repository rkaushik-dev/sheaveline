package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** Why somebody is riding down. */
public enum DownhillReason implements Valued {
    /** A rider who cannot ski or ride down. */
    DISABILITY("disability"),
    /** A beginner who went up and should not come down the trail. */
    BEGINNER("beginner returning"),
    /** A rider whose skis or board broke. */
    EQUIPMENT("equipment failure"),
    /** Somebody who came up for the view. */
    SIGHTSEEING("sightseeing"),
    /** Staff going down the hill. */
    STAFF("staff movement");

    private final String value;

    DownhillReason(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the riders are members of the public.
     *
     * @return false only for staff movement
     */
    public boolean isPublic() {
        return this != STAFF;
    }

    /**
     * Whether the rider rides with an escort, because somebody who cannot get themselves off does not
     * ride alone.
     *
     * @return true for a disability and an equipment failure
     */
    public boolean needsAnEscort() {
        return this == DISABILITY || this == EQUIPMENT;
    }
}
