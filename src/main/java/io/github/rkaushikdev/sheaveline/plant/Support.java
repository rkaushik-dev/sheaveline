package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Objects;

/**
 * A place where the rope is held up, and how high it is held. A support is either a terminal or a
 * tower; the terminals count because the first and last spans are real spans, and on a steep loading
 * terminal they are the ones with the trouble.
 *
 * @param kind terminal or tower
 * @param label what it is called on the drawings
 * @param station where it stands along the line
 * @param ropeElevation how high it holds the rope
 */
public record Support(SupportKind kind, String label, Length station, Length ropeElevation) {

    /** A support, nothing left null. */
    public Support {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(ropeElevation, "ropeElevation");
    }

    /**
     * A terminal holding the rope.
     *
     * @param label what it is called
     * @param station where it stands
     * @param ropeElevation the rope elevation at its bullwheel
     * @return the support
     */
    public static Support terminal(String label, Length station, Length ropeElevation) {
        return new Support(SupportKind.TERMINAL, label, station, ropeElevation);
    }

    /**
     * A tower holding the rope at its crossarm.
     *
     * @param tower the tower
     * @return the support, labelled with the tower's reference
     */
    public static Support ofTower(Tower tower) {
        return new Support(SupportKind.TOWER, tower.ref().toString(), tower.station(), tower.crossarmElevation());
    }

    /**
     * Whether this support is a tower.
     *
     * @return true for a tower
     */
    public boolean isTower() {
        return kind == SupportKind.TOWER;
    }

    /** The label. */
    @Override
    public String toString() {
        return label;
    }
}
