package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field holding {@link io.github.rkaushikdev.sheaveline.plant.TowerRef} that goes over the wire as text,
 * as it is stencilled on the tower, such as {@code JP-T4}. It is read back through the same parser, so whatever
 * a tower reference can be written as is accepted.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@Schema(type = "string", examples = "JP-T4")
public @interface TowerRefText {}
