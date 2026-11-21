package io.github.rkaushikdev.sheaveline.util;

import java.util.Optional;

/**
 * An enumeration whose constants each carry the words they are written as.
 *
 * <p>The words are what the paperwork, the http surface and the printed pages read and write, so
 * {@code LiftClass.DETACHABLE_CHAIR} is shown as {@code "detachable chairlift"}. The database and
 * the lift drawing store the constant's name instead, which a reworded sentence cannot break.
 */
public interface Valued {

    /**
     * The words this constant is written as.
     *
     * @return the printed form
     */
    String value();

    /**
     * The constant written as the given words.
     *
     * @param type the enumeration
     * @param value the words, matched exactly
     * @param <E> the enumeration
     * @return the constant, or empty when no constant is written that way
     */
    static <E extends Enum<E> & Valued> Optional<E> lookup(Class<E> type, String value) {
        for (E one : type.getEnumConstants()) {
            if (one.value().equals(value)) {
                return Optional.of(one);
            }
        }
        return Optional.empty();
    }
}
