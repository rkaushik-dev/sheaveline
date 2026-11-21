package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.util.Objects;

/**
 * One person at one post for one stretch of the day.
 *
 * @param person who
 * @param post where
 * @param starts when they take the post
 * @param ends when they leave it, which is not a minute they cover
 */
public record Assignment(String person, Post post, TimeOfDay starts, TimeOfDay ends) {

    /**
     * A named stretch that runs forwards.
     *
     * @throws OperationsError when it names nobody, or finishes no later than it starts
     */
    public Assignment {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(post, "post");
        Objects.requireNonNull(starts, "starts");
        Objects.requireNonNull(ends, "ends");
        if (person.strip().isEmpty()) {
            throw new OperationsError("an assignment names a person");
        }
        if (ends.compareTo(starts) <= 0) {
            throw new OperationsError(person + " would finish before starting");
        }
    }

    /**
     * Whether the post is held at that minute.
     *
     * @param moment the minute
     * @return true from the start, up to but not including the end
     */
    public boolean covers(TimeOfDay moment) {
        return starts.compareTo(moment) <= 0 && moment.compareTo(ends) < 0;
    }

    /**
     * How long the stretch is.
     *
     * @return start to end
     */
    public Duration length() {
        return starts.until(ends);
    }

    /**
     * Whether two stretches share a minute; one ending as the other starts does not.
     *
     * @param other the other stretch
     * @return true when they overlap
     */
    public boolean overlaps(Assignment other) {
        return starts.compareTo(other.ends) < 0 && other.starts.compareTo(ends) < 0;
    }

    /** Such as {@code R Diaz on bottom operator, 9:00 to 16:00}. */
    @Override
    public String toString() {
        return person + " on " + post.value() + ", " + starts + " to " + ends;
    }
}
