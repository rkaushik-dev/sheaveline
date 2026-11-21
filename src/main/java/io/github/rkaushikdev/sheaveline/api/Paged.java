package io.github.rkaushikdev.sheaveline.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of a list, and enough to ask for the next.
 *
 * <p>The page carries the total on purpose. The screen that shows it says how many readings there
 * are and lets somebody skip to the end, and working that out from a next link is worse than asking
 * for a count.
 *
 * @param items the rows on this page
 * @param total how many rows there are in all
 * @param limit the most rows a page holds
 * @param offset how many rows came before this page
 * @param nextOffset where the following page starts, or empty on the last page
 * @param <T> what the rows are
 */
public record Paged<T>(List<T> items, int total, int limit, int offset, Optional<Integer> nextOffset) {

    /** A page. */
    public Paged {
        items = List.copyOf(items);
        Objects.requireNonNull(nextOffset, "nextOffset");
    }

    /**
     * The page of a list a paging asks for.
     *
     * @param everything the whole list
     * @param paging which page
     * @param <T> what is in it
     * @return the page
     */
    public static <T> Paged<T> of(List<T> everything, Paging paging) {
        return new Paged<>(
                paging.cut(everything),
                everything.size(),
                paging.limit(),
                paging.offset(),
                paging.nextOffset(everything.size()));
    }

    /**
     * Whether nothing follows this page.
     *
     * @return true on the last page
     */
    @JsonIgnore
    public boolean isLast() {
        return nextOffset.isEmpty();
    }

    /**
     * How many rows are on this page.
     *
     * @return the count
     */
    public int size() {
        return items.size();
    }
}
