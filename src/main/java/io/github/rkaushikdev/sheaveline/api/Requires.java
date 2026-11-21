package io.github.rkaushikdev.sheaveline.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The role a route needs. Anything below it is refused before the route reads a word of the
 * request, so a caller who may not be here hears that rather than what was wrong with what they
 * sent.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Requires {

    /**
     * The role.
     *
     * @return the least role that may call the route
     */
    Role value();
}
