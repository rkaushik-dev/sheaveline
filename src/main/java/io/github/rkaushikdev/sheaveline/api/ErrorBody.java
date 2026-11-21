package io.github.rkaushikdev.sheaveline.api;

import java.util.Objects;
import java.util.Optional;

/**
 * What every failure looks like on the wire: a kind, a sentence and, where there is one, the field
 * that caused it. The field is written as {@code null} when there is none, never left out.
 *
 * @param kind the kind of failure, such as {@code NotFound}
 * @param detail the sentence the client shows
 * @param field the field that caused it, when one did
 */
public record ErrorBody(String kind, String detail, Optional<String> field) {

    /** A body. */
    public ErrorBody {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(field, "field");
    }
}
