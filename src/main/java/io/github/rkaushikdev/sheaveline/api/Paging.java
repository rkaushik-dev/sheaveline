package io.github.rkaushikdev.sheaveline.api;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;

/**
 * How much of a list the caller wants and where to start, because some of these lists do not stop
 * growing.
 *
 * <p>Most of what this system holds is small. There are six machines and there will be six machines
 * next year. Three things are not: the weather readings, which arrive every ten minutes from every
 * station all winter, the stops, and the day logs. A tablet at the bottom terminal on a bar of signal
 * asking for the whole observation table is a request that times out and then gets retried.
 *
 * <p>Offset and limit rather than a cursor. The tables that need it are ordered by a timestamp and
 * read far more often than they are written, and a cursor buys correctness under concurrent insert
 * that nobody here is going to notice. Saying so out loud is cheaper than pretending otherwise later.
 *
 * <p>A route that pages takes one of these; it is read from the {@code limit} and {@code offset}
 * query values, each defaulted when absent, and a value out of bounds is refused as a 422.
 *
 * @param limit how many rows to hand back at once
 * @param offset how many rows to skip
 */
public record Paging(
        @Parameter(description = "How many rows to hand back at once.") @Min(1) @Max(MOST_AT_ONCE) int limit,
        @Parameter(description = "How many rows to skip.") @Min(0) int offset) {

    /** The page size when the caller does not say. */
    public static final int DEFAULT_LIMIT = 50;

    /** The biggest page anybody may ask for. */
    public static final int MOST_AT_ONCE = 500;

    /**
     * A page of the usual size from the start.
     *
     * @return the paging
     */
    public static Paging defaults() {
        return new Paging(DEFAULT_LIMIT, 0);
    }

    /**
     * The first page of a size.
     *
     * @param limit how many rows
     * @return the paging
     */
    public static Paging first(int limit) {
        return new Paging(limit, 0);
    }

    /**
     * The part of a list this page covers. A page past the end is empty rather than an error.
     *
     * @param everything the whole list
     * @param <T> what is in it
     * @return the slice
     */
    public <T> List<T> cut(List<T> everything) {
        int from = Math.min(offset, everything.size());
        int to = (int) Math.min((long) offset + limit, everything.size());
        return List.copyOf(everything.subList(from, to));
    }

    /**
     * Where the following page starts.
     *
     * @param total how long the whole list is
     * @return the offset, or empty when this is the last page
     */
    public Optional<Integer> nextOffset(int total) {
        int following = offset + limit;
        return following < total ? Optional.of(following) : Optional.empty();
    }
}
