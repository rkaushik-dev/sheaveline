package io.github.rkaushikdev.sheaveline.evac;

import java.util.Comparator;
import java.util.List;

/**
 * The greatest or least of a list, the first of them on a tie.
 *
 * <p>Which team gets a carrier, and which beat is called the busiest, must not depend on anything
 * but the order the candidates were listed in, so every pick here keeps the earliest of equals.
 */
final class Ranking {

    private Ranking() {}

    /**
     * The greatest item.
     *
     * @param items the candidates, never empty
     * @param order how they compare
     * @param <T> the item
     * @return the first of the greatest
     */
    static <T> T highest(List<T> items, Comparator<? super T> order) {
        T best = items.get(0);
        for (T item : items) {
            if (order.compare(item, best) > 0) {
                best = item;
            }
        }
        return best;
    }

    /**
     * The least item.
     *
     * @param items the candidates, never empty
     * @param order how they compare
     * @param <T> the item
     * @return the first of the least
     */
    static <T> T lowest(List<T> items, Comparator<? super T> order) {
        return highest(items, order.reversed());
    }
}
