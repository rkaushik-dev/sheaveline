package io.github.rkaushikdev.sheaveline.api;

import java.util.Objects;

/**
 * A request that does not read as what the route wants: a query value of the wrong kind, a number
 * out of bounds. It is answered as a 422 naming the field, like every other unreadable request.
 */
public class RequestRejected extends RuntimeException {

    private final String field;

    /**
     * A refusal.
     *
     * @param field the field that did not read, as the client spelt it
     * @param message what was wrong with it
     */
    public RequestRejected(String field, String message) {
        super(message);
        this.field = Objects.requireNonNull(field, "field");
    }

    /**
     * The field that did not read.
     *
     * @return the field, as the client spelt it
     */
    public String field() {
        return field;
    }
}
