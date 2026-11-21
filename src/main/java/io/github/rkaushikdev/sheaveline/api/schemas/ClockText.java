package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field holding {@link io.github.rkaushikdev.sheaveline.schedule.TimeOfDay} that goes over the wire as text,
 * as a twenty four hour clock, such as {@code 15:45}. It is read back through the same parser, so whatever
 * a time of day can be written as is accepted.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@Schema(type = "string", examples = "15:45")
public @interface ClockText {}
