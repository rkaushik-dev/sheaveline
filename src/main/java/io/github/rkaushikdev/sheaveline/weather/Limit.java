package io.github.rkaushikdev.sheaveline.weather;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * One figure off a reading held against one limit of the machine's class, and the figure it has to
 * come back past before the hold comes off.
 *
 * <p>Calling a hold and lifting it are not the same comparison. A chair held at a forty five mile an
 * hour gust that reopens the moment the anemometer shows forty four is a chair that stops again ten
 * minutes later with people on it. So every limit carries the figure it reopens at as well as the
 * figure it holds at, and for most of them the two are the same.
 *
 * @param <M> the measure read
 * @param reason the hold it calls
 * @param reading how the figure is taken off a decision
 * @param limit the class's limit, or empty when the class is not asked about this
 * @param holdsBelow true when a figure under the limit holds, as for visibility, false when a figure
 *     over it holds, as for a gust
 * @param reopensAt the figure the reading has to come back to, given the limit
 * @param describe how a holding figure is told to the operator, given the figure and the limit
 */
public record Limit<M extends Comparable<M>>(
        HoldReason reason,
        Function<WeatherHold, M> reading,
        Function<WeatherHold, Optional<M>> limit,
        boolean holdsBelow,
        UnaryOperator<M> reopensAt,
        BiFunction<M, M, String> describe) {

    /** A row. */
    public Limit {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(reopensAt, "reopensAt");
        Objects.requireNonNull(describe, "describe");
    }

    /**
     * Whether the figure holds the machine.
     *
     * @param decision the machine read against a reading
     * @return the hold when the figure is past the limit, otherwise empty, and always empty for a
     *     class with no such limit
     */
    public Optional<Hold> check(WeatherHold decision) {
        Optional<M> allowed = limit.apply(decision);
        if (allowed.isEmpty()) {
            return Optional.empty();
        }
        M figure = reading.apply(decision);
        if (!past(figure, allowed.get())) {
            return Optional.empty();
        }
        return Optional.of(new Hold(reason, describe.apply(figure, allowed.get())));
    }

    /**
     * Whether the figure is inside the limit but not yet back to the reopening figure, which is
     * where a machine still running is slowed down rather than stopped.
     *
     * @param decision the machine read against a reading
     * @return the figure, told the way a hold would tell it, or empty when it is past the limit or
     *     clear of the margin
     */
    public Optional<Hold> near(WeatherHold decision) {
        Optional<M> allowed = limit.apply(decision);
        if (allowed.isEmpty() || check(decision).isPresent() || clears(decision)) {
            return Optional.empty();
        }
        return Optional.of(new Hold(reason, describe.apply(reading.apply(decision), allowed.get())));
    }

    /**
     * Whether a hold for this reason can come off on the reading.
     *
     * @param decision the machine read against the latest reading
     * @return true once the figure is back to the reopening figure or better, and always for a class
     *     with no such limit
     */
    public boolean clears(WeatherHold decision) {
        Optional<M> allowed = limit.apply(decision);
        if (allowed.isEmpty()) {
            return true;
        }
        M figure = reading.apply(decision);
        M reopen = reopensAt.apply(allowed.get());
        int against = figure.compareTo(reopen);
        return holdsBelow ? against >= 0 : against <= 0;
    }

    private boolean past(M figure, M allowed) {
        int against = figure.compareTo(allowed);
        return holdsBelow ? against < 0 : against > 0;
    }
}
