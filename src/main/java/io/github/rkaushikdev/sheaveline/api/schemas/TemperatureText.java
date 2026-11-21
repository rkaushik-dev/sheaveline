package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field holding {@link io.github.rkaushikdev.sheaveline.measures.Temperature} that goes over the wire as text,
 * in degrees Fahrenheit to one place, such as {@code -12.0 F}. It is read back through the same parser, so whatever
 * a temperature can be written as is accepted.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@Schema(type = "string", examples = "-12.0 F")
public @interface TemperatureText {}
