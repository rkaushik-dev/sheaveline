package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.github.rkaushikdev.sheaveline.MeasureError;
import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.TowerRef;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Measures on the wire, as the strings a person writes on a form.
 *
 * <p>A length does not go over the wire as a number. It goes as {@code "4800 ft"} or {@code 1' 6"},
 * which is what the drawing says and what the tablet in the terminal shows. That costs a parse
 * either side and buys three things: nothing is ever a float, a client that sends feet when the
 * field wanted inches gets told rather than silently believed, and the JSON is readable by the
 * person the department will eventually be arguing with.
 *
 * <p>Every one of these is the package's own parser, wired into the JSON reader. There is no second
 * grammar here, which is the point. A value that will not parse is {@link Refused}. A field says
 * which face it prints with its annotation: {@link LengthFeet} or {@link LengthInches} for a length,
 * {@link SpeedText} or {@link WindText} for a speed; the other measures have one face each.
 *
 * <p>The department's enumerations travel the same way, as the words a person reads rather than the
 * names the code uses.
 */
public final class WireText {

    private WireText() {}

    /**
     * The JSON module that reads and writes every measure, identity and enumeration this way.
     *
     * @return the module
     */
    public static Module module() {
        ValuedSerializers out = new ValuedSerializers();
        ValuedDeserializers in = new ValuedDeserializers();
        text(
                out,
                in,
                Length.class,
                Length::parse,
                WireText::feet,
                Map.of(LengthInches.class, one -> one.formatInches(3)));
        text(out, in, Load.class, Load::parse, Load::formatPounds, Map.of());
        text(out, in, Speed.class, Speed::parse, Speed::formatFpm, Map.of(WindText.class, Speed::formatMph));
        text(out, in, Duration.class, Duration::parse, Duration::formatHms, Map.of());
        text(out, in, Angle.class, Angle::parse, Angle::formatDegrees, Map.of());
        text(out, in, Bearing.class, Bearing::parse, Bearing::formatDegrees, Map.of());
        text(out, in, Temperature.class, Temperature::parse, one -> one.formatF(1), Map.of());
        text(out, in, Torque.class, Torque::parse, Torque::formatInchPounds, Map.of());
        text(out, in, TimeOfDay.class, TimeOfDay::parse, TimeOfDay::formatClock, Map.of());
        text(out, in, LiftCode.class, LiftCode::parse, LiftCode::toString, Map.of());
        text(out, in, TowerRef.class, TowerRef::parse, TowerRef::toString, Map.of());
        text(out, in, RopeSerial.class, RopeSerial::parse, RopeSerial::toString, Map.of());
        text(out, in, FilingNumber.class, FilingNumber::parse, FilingNumber::toString, Map.of());
        SimpleModule module = new SimpleModule("sheaveline-wire-text");
        module.setSerializers(out);
        module.setDeserializers(in);
        return module;
    }

    /**
     * A length in feet to two places, the face every station and elevation is written in.
     *
     * @param length the length
     * @return such as {@code 4800.00 ft}
     */
    public static String feet(Length length) {
        return Decimals.fixed(length.feet(), 2) + " ft";
    }

    /**
     * The words an enumeration is written in, in declaration order.
     *
     * @param type an enumeration whose constants are {@link Valued}
     * @return the words
     */
    public static List<String> words(Class<?> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> ((Valued) constant).value())
                .toList();
    }

    /**
     * What a caller is told when a value is not one of an enumeration's words.
     *
     * @param type an enumeration whose constants are {@link Valued}
     * @return such as {@code Input should be 'a', 'b' or 'c'}
     */
    public static String expected(Class<?> type) {
        List<String> quoted = words(type).stream().map(word -> "'" + word + "'").toList();
        if (quoted.size() == 1) {
            return "Input should be " + quoted.get(0);
        }
        return "Input should be " + String.join(", ", quoted.subList(0, quoted.size() - 1)) + " or "
                + quoted.get(quoted.size() - 1);
    }

    private static <T> void text(
            SimpleSerializers out,
            SimpleDeserializers in,
            Class<T> type,
            Function<String, T> parse,
            Function<T, String> plain,
            Map<Class<? extends Annotation>, Function<T, String>> faces) {
        out.addSerializer(type, new TextOut<>(type, plain, faces));
        in.addDeserializer(type, new TextIn<>(type, parse));
    }

    /** A value written as text, in the face its field asks for. */
    private static final class TextOut<T> extends StdSerializer<T> implements ContextualSerializer {

        private static final long serialVersionUID = 1L;

        private final transient Function<T, String> render;
        private final transient Map<Class<? extends Annotation>, Function<T, String>> faces;

        TextOut(
                Class<T> type,
                Function<T, String> render,
                Map<Class<? extends Annotation>, Function<T, String>> faces) {
            super(type);
            this.render = render;
            this.faces = new LinkedHashMap<>(faces);
        }

        @Override
        public void serialize(T value, JsonGenerator out, SerializerProvider provider) throws IOException {
            out.writeString(render.apply(value));
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
            if (property != null) {
                for (Map.Entry<Class<? extends Annotation>, Function<T, String>> face : faces.entrySet()) {
                    if (property.getAnnotation(face.getKey()) != null) {
                        return new TextOut<>(handledType(), face.getValue(), faces);
                    }
                }
            }
            return this;
        }
    }

    /** A value read from the text a person writes, or one already built passed straight through. */
    private static final class TextIn<T> extends StdDeserializer<T> {

        private static final long serialVersionUID = 1L;

        private final Class<T> type;
        private final transient Function<String, T> parse;

        TextIn(Class<T> type, Function<String, T> parse) {
            super(type);
            this.type = type;
            this.parse = parse;
        }

        @Override
        public T deserialize(JsonParser in, DeserializationContext context) throws IOException {
            JsonToken token = in.currentToken();
            if (token == JsonToken.VALUE_EMBEDDED_OBJECT && type.isInstance(in.getEmbeddedObject())) {
                return type.cast(in.getEmbeddedObject());
            }
            if (token == null || !token.isScalarValue()) {
                throw new Refused(in, "Input should be text, such as a measure as a person writes it");
            }
            try {
                return parse.apply(in.getText());
            } catch (MeasureError refused) {
                throw new Refused(in, refused.getMessage(), refused);
            }
        }
    }

    /** Every {@link Valued} enumeration written as its words. */
    private static final class ValuedSerializers extends SimpleSerializers {

        private static final long serialVersionUID = 1L;

        @Override
        public JsonSerializer<?> findSerializer(
                SerializationConfig config, JavaType type, BeanDescription description) {
            if (type.isEnumType() && Valued.class.isAssignableFrom(type.getRawClass())) {
                return new ValuedOut();
            }
            return super.findSerializer(config, type, description);
        }
    }

    /** Every {@link Valued} enumeration read from its words, and nothing else. */
    private static final class ValuedDeserializers extends SimpleDeserializers {

        private static final long serialVersionUID = 1L;

        @Override
        public JsonDeserializer<?> findEnumDeserializer(
                Class<?> type, DeserializationConfig config, BeanDescription description) throws JsonMappingException {
            if (Valued.class.isAssignableFrom(type)) {
                return new ValuedIn(type);
            }
            return super.findEnumDeserializer(type, config, description);
        }
    }

    private static final class ValuedOut extends StdSerializer<Valued> {

        private static final long serialVersionUID = 1L;

        ValuedOut() {
            super(Valued.class);
        }

        @Override
        public void serialize(Valued value, JsonGenerator out, SerializerProvider provider) throws IOException {
            out.writeString(value.value());
        }
    }

    private static final class ValuedIn extends StdDeserializer<Object> {

        private static final long serialVersionUID = 1L;

        private final transient List<Valued> constants;
        private final String expected;

        ValuedIn(Class<?> type) {
            super(type);
            this.constants = Arrays.stream(type.getEnumConstants())
                    .map(Valued.class::cast)
                    .toList();
            this.expected = WireText.expected(type);
        }

        @Override
        public Object deserialize(JsonParser in, DeserializationContext context) throws IOException {
            if (in.currentToken() == JsonToken.VALUE_STRING) {
                for (Valued constant : constants) {
                    if (constant.value().equals(in.getText())) {
                        return constant;
                    }
                }
            }
            throw new Refused(in, expected);
        }
    }
}
