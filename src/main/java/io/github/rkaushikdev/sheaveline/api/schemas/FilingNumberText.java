package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field holding {@link io.github.rkaushikdev.sheaveline.plant.FilingNumber} that goes over the wire as text,
 * as the number the board files it under, such as {@code UT-2025-0147}. It is read back through the same parser, so whatever
 * a filing number can be written as is accepted.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@Schema(type = "string", examples = "UT-2025-0147")
public @interface FilingNumberText {}
