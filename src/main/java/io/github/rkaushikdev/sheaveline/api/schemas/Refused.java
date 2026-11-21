package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * A value in a request body that does not read as what its field wants, refused rather than
 * believed. The JSON reader adds where in the body it was found as the refusal travels out, and the
 * service answers it as a 422 naming that field.
 */
public final class Refused extends JsonMappingException {

    private static final long serialVersionUID = 1L;

    /**
     * A refusal.
     *
     * @param in where the value was being read
     * @param why what was wrong with it
     */
    public Refused(JsonParser in, String why) {
        super(in, why);
    }

    /**
     * A refusal passing on the domain's own.
     *
     * @param in where the value was being read
     * @param why what was wrong with it
     * @param cause the domain's refusal
     */
    public Refused(JsonParser in, String why, Throwable cause) {
        super(in, why, cause);
    }
}
