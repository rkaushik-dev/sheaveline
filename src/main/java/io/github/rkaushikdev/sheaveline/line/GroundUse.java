package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * What is happening under the rope, which sets the clearance wanted. Twelve feet over untracked
 * timber is fine and twelve feet over a groomed road with a cat on it is not.
 */
public enum GroundUse implements Valued {
    /** Untracked ground. */
    OPEN("open ground", 12),
    /** A trail the groomers run. */
    TRAIL("groomed trail", 16),
    /** A road. */
    ROAD("road", 24),
    /** A building or other structure. */
    STRUCTURE("structure", 20),
    /** A pond or a river. */
    WATER("open water", 30);

    private final String value;
    private final Length minimum;

    GroundUse(String value, long minimumFeet) {
        this.value = value;
        this.minimum = Length.ofFeet(minimumFeet);
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * The clearance this use wants under the carriers.
     *
     * @return the minimum
     */
    public Length minimum() {
        return minimum;
    }

    /**
     * Whether people or machines travel over this ground.
     *
     * @return true for a trail or a road
     */
    public boolean isTrafficked() {
        return this == TRAIL || this == ROAD;
    }
}
