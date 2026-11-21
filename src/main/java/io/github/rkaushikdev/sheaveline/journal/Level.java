package io.github.rkaushikdev.sheaveline.journal;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How much of the journal you want. Declared in order, so a comparison reads the right way. */
public enum Level {
    /** Everything, including what only the person fixing it cares about. */
    DEBUG(10),
    /** What the software did. */
    INFO(20),
    /** Something worth a second look. */
    WARN(30),
    /** Something went wrong. */
    ERROR(40),
    /** Nothing at all. */
    OFF(100);

    private final int weight;

    Level(int weight) {
        this.weight = weight;
    }

    /**
     * The number the level stands for, for anything that orders levels by it.
     *
     * @return 10 for debug through 100 for off
     */
    public int weight() {
        return weight;
    }

    /**
     * The level's name as it is written in settings and printed.
     *
     * @return the lower case name
     */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Look a level up by name, ignoring case and surrounding space.
     *
     * @param name what was written, such as {@code "warn"} or {@code "  Error "}
     * @return the level
     * @throws IllegalArgumentException naming every level there is, when nobody has that one
     */
    public static Level named(String name) {
        String wanted = name.strip().toUpperCase(Locale.ROOT);
        for (Level one : values()) {
            if (one.name().equals(wanted)) {
                return one;
            }
        }
        String every = Arrays.stream(values()).map(Level::label).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("no such level " + name + ", wanted one of " + every);
    }
}
