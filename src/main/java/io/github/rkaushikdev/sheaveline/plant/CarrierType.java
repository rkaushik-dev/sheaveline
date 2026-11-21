package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What the public rides in or hangs from. */
public enum CarrierType implements Valued {
    /** A two seat chair. */
    CHAIR_2("double chair", 2),
    /** A three seat chair. */
    CHAIR_3("triple chair", 3),
    /** A four seat chair. */
    CHAIR_4("quad chair", 4),
    /** A six seat chair. */
    CHAIR_6("six place chair", 6),
    /** An eight place gondola cabin. */
    CABIN_8("eight place cabin", 8),
    /** A tram car. */
    TRAM_CAR("tram car", 60),
    /** A t bar, two riders standing. */
    TBAR("t bar", 2),
    /** A platter, one rider standing. */
    PLATTER("platter", 1);

    private final String value;
    private final int seats;

    CarrierType(String value, int seats) {
        this.value = value;
        this.seats = seats;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * How many people the carrier takes.
     *
     * @return the seat count, at least one
     */
    public int seats() {
        return seats;
    }

    /**
     * Whether the riders are inside a cabin.
     *
     * @return true for cabins and tram cars
     */
    public boolean isEnclosed() {
        return this == CABIN_8 || this == TRAM_CAR;
    }

    /**
     * Whether the riders stay on the snow.
     *
     * @return true for t bars and platters
     */
    public boolean isSurface() {
        return this == TBAR || this == PLATTER;
    }
}
