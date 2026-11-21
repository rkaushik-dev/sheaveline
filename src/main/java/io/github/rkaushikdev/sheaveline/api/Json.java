package io.github.rkaushikdev.sheaveline.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.github.rkaushikdev.sheaveline.api.schemas.WireText;
import io.github.rkaushikdev.sheaveline.util.Valued;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Iterator;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * How the service reads, writes and describes JSON, in one place so the application, its document
 * and the tests of its shapes cannot drift apart.
 *
 * <p>Field names are snake case. A field nobody declared is ignored, and a field with a default
 * that is left out, or sent as null, takes its default. Text fields take text and nothing else, and
 * a number with a fraction is not quietly cut down to a whole one. Dates and
 * moments are ISO strings. The department's enumerations travel as the words a person reads, both
 * ways, and every measure travels as the text a person writes on a form ({@link WireText}).
 */
public final class Json {

    private Json() {}

    /**
     * Apply the service's JSON rules to a builder: the application's, or a test's.
     *
     * @param builder the builder
     */
    public static void configure(Jackson2ObjectMapperBuilder builder) {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .featuresToDisable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .modulesToInstall(modules -> modules.add(WireText.module()))
                .postConfigurer(mapper -> mapper.coercionConfigFor(LogicalType.Textual)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    /**
     * A mapper built the way the service builds its own.
     *
     * @return the mapper
     */
    public static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        configure(builder);
        return builder.build();
    }

    /**
     * What the document says the shapes are: read through the service's own mapper, so the names
     * are the names on the wire, every measure a string, and every enumeration the words it is
     * written in.
     *
     * @return the resolver the document is built with
     */
    public static ModelResolver modelResolver() {
        return new WireSchemas(mapper());
    }

    /**
     * The document's schema for one shape, built the way the served document builds it.
     *
     * @param shape the shape
     * @return its schema
     */
    public static Schema<?> schemaOf(Class<?> shape) {
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(modelResolver());
        return converters.readAll(shape).get(shape.getSimpleName());
    }

    /** The shapes as the wire has them, with a {@link Valued} enumeration as a list of its words. */
    private static final class WireSchemas extends ModelResolver {

        WireSchemas(ObjectMapper mapper) {
            super(mapper);
            openapi31(true);
        }

        @Override
        public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
            Class<?> raw =
                    TypeFactory.defaultInstance().constructType(type.getType()).getRawClass();
            if (raw.isEnum() && Valued.class.isAssignableFrom(raw)) {
                StringSchema words = new StringSchema();
                WireText.words(raw).forEach(words::addEnumItem);
                return words;
            }
            return super.resolve(type, context, chain);
        }
    }
}
