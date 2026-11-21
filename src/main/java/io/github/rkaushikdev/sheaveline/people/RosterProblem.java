package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing wrong with a roster, said in a way somebody can act on.
 *
 * @param kind what is wrong
 * @param post the post it is wrong at
 * @param at the minute it starts
 * @param person who it is about, for a problem about a person
 */
public record RosterProblem(ProblemKind kind, Post post, TimeOfDay at, Optional<String> person) {

    /** A problem. */
    public RosterProblem {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(post, "post");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(person, "person");
    }

    /**
     * A problem about nobody in particular, such as an empty post.
     *
     * @param kind what is wrong
     * @param post the post
     * @param at the minute it starts
     */
    public RosterProblem(ProblemKind kind, Post post, TimeOfDay at) {
        this(kind, post, at, Optional.empty());
    }

    /** Such as {@code 8:00 top operator: T Woo, no card for the post}. */
    @Override
    public String toString() {
        String who = person.filter(name -> !name.isEmpty()).orElse("nobody");
        return at + " " + post.value() + ": " + who + ", " + kind.value();
    }
}
