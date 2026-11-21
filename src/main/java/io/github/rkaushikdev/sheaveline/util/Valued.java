package io.github.rkaushikdev.sheaveline.util;

import java.util.Optional;

/**
 * An enumeration whose constants each carry the words they are written as.
 *
 * <p>The words are what the paperwork, the database and the http surface all read and write, so
 * {@code LiftClass.DETACHABLE_CHAIR} goes out as {@code "detachable chairlift"} everywhere. The
 * constant's Java name is never stored or shown.
 */
public interface Valued {

    /**
     * The words this constant is written as.
     *
     * @return the stored and printed form
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
