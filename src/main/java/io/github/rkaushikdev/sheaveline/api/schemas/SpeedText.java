package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field holding {@link io.github.rkaushikdev.sheaveline.measures.Speed} that goes over the wire as text,
 * in feet a minute, the face a rope speed is written in, such as {@code 1000 fpm}. It is read back through the same parser, so whatever
 * a speed can be written as is accepted.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@Schema(type = "string", examples = "1000 fpm")
public @interface SpeedText {}
