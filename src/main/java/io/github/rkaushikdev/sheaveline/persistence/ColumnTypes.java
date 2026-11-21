package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.TowerRef;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.function.Function;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

/**
 * Column types that store a measure as the integer it already is.
 *
 * <p>Every measure is an exact integer in a small base unit, so the honest column is an integer
 * column and the converter is a one line conversion either side of it. Storing a length as a float
 * would be storing a number this system deliberately never produced.
 *
 * <p>The one worth reading twice is the length column. It stores mils, and a mil is a thousandth of
 * an inch, so a five thousand foot line is sixty million of them. That fits in an ordinary signed
 * integer with room to spare, and it means two lengths compare in the database exactly as they
 * compare in memory. A value too large for its column is refused rather than cut down.
 *
 * <p>Measures and identifiers convert wherever their type appears on a row. A decimal is only
 * stored in thousandths where a row asks for it by name, since a decimal is not one kind of thing.
 * A key is never converted, so an identifier that is a row's key is described as a type of its own,
 * a {@link Key}, stored the same way.
 */
public final class ColumnTypes {

    /** How long a lift code column is. */
    public static final int LIFT_CODE_LENGTH = 8;

    /** How long a tower reference column is. */
    public static final int TOWER_REF_LENGTH = 16;

    /** How long a rope serial column is. */
    public static final int ROPE_SERIAL_LENGTH = 20;

    /** How long a filing number column is. */
    public static final int FILING_NUMBER_LENGTH = 16;

    /** How long a stored enumeration name may be. */
    public static final int ENUM_LENGTH = 32;

    private ColumnTypes() {}

    /** A length, stored as whole mils. */
    @Converter(autoApply = true)
    public static final class LengthColumn implements AttributeConverter<Length, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Length value) {
            return value == null ? null : Math.toIntExact(value.mils());
        }

        @Override
        public Length convertToEntityAttribute(Integer value) {
            return value == null ? null : new Length(value);
        }
    }

    /** A load, stored as whole pounds. */
    @Converter(autoApply = true)
    public static final class LoadColumn implements AttributeConverter<Load, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Load value) {
            return value == null ? null : Math.toIntExact(value.pounds());
        }

        @Override
        public Load convertToEntityAttribute(Integer value) {
            return value == null ? null : new Load(value);
        }
    }

    /** A weight per foot, stored as whole thousandths of a pound. */
    @Converter(autoApply = true)
    public static final class LoadPerFootColumn implements AttributeConverter<LoadPerFoot, Integer> {

        @Override
        public Integer convertToDatabaseColumn(LoadPerFoot value) {
            return value == null ? null : Math.toIntExact(value.millipoundsPerFoot());
        }

        @Override
        public LoadPerFoot convertToEntityAttribute(Integer value) {
            return value == null ? null : new LoadPerFoot(value);
        }
    }

    /** A speed, stored as whole mils a minute. */
    @Converter(autoApply = true)
    public static final class SpeedColumn implements AttributeConverter<Speed, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Speed value) {
            return value == null ? null : Math.toIntExact(value.milsPerMinute());
        }

        @Override
        public Speed convertToEntityAttribute(Integer value) {
            return value == null ? null : new Speed(value);
        }
    }

    /** A duration, stored as whole seconds. */
    @Converter(autoApply = true)
    public static final class DurationColumn implements AttributeConverter<Duration, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Duration value) {
            return value == null ? null : Math.toIntExact(value.seconds());
        }

        @Override
        public Duration convertToEntityAttribute(Integer value) {
            return value == null ? null : new Duration(value);
        }
    }

    /** An angle, stored as whole thousandths of a degree. */
    @Converter(autoApply = true)
    public static final class AngleColumn implements AttributeConverter<Angle, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Angle value) {
            return value == null ? null : Math.toIntExact(value.millidegrees());
        }

        @Override
        public Angle convertToEntityAttribute(Integer value) {
            return value == null ? null : new Angle(value);
        }
    }

    /** A compass bearing, stored the same way and folded into one circle on the way back. */
    @Converter(autoApply = true)
    public static final class BearingColumn implements AttributeConverter<Bearing, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Bearing value) {
            return value == null ? null : Math.toIntExact(value.millidegrees());
        }

        @Override
        public Bearing convertToEntityAttribute(Integer value) {
            return value == null ? null : new Bearing(value);
        }
    }

    /** A reading, stored as whole tenths of a degree Fahrenheit. */
    @Converter(autoApply = true)
    public static final class TemperatureColumn implements AttributeConverter<Temperature, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Temperature value) {
            return value == null ? null : Math.toIntExact(value.decidegreesF());
        }

        @Override
        public Temperature convertToEntityAttribute(Integer value) {
            return value == null ? null : new Temperature(value);
        }
    }

    /** A torque, stored as whole inch pounds. */
    @Converter(autoApply = true)
    public static final class TorqueColumn implements AttributeConverter<Torque, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Torque value) {
            return value == null ? null : Math.toIntExact(value.inchPounds());
        }

        @Override
        public Torque convertToEntityAttribute(Integer value) {
            return value == null ? null : new Torque(value);
        }
    }

    /** A wall clock time, stored as minutes since midnight. */
    @Converter(autoApply = true)
    public static final class TimeOfDayColumn implements AttributeConverter<TimeOfDay, Integer> {

        @Override
        public Integer convertToDatabaseColumn(TimeOfDay value) {
            return value == null ? null : value.minutes();
        }

        @Override
        public TimeOfDay convertToEntityAttribute(Integer value) {
            return value == null ? null : new TimeOfDay(value);
        }
    }

    /** A lift code, stored as it is painted, which is what people search on. */
    @Converter(autoApply = true)
    public static final class LiftCodeColumn implements AttributeConverter<LiftCode, String> {

        @Override
        public String convertToDatabaseColumn(LiftCode value) {
            return value == null ? null : value.value();
        }

        @Override
        public LiftCode convertToEntityAttribute(String value) {
            return value == null ? null : new LiftCode(value);
        }
    }

    /** A tower, stored in its printed form, such as {@code JP-T4}. */
    @Converter(autoApply = true)
    public static final class TowerRefColumn implements AttributeConverter<TowerRef, String> {

        @Override
        public String convertToDatabaseColumn(TowerRef value) {
            return value == null ? null : value.toString();
        }

        @Override
        public TowerRef convertToEntityAttribute(String value) {
            return value == null ? null : TowerRef.parse(value);
        }
    }

    /** A rope's mill serial, stored in its printed form, such as {@code FAT-2019-04412}. */
    @Converter(autoApply = true)
    public static final class RopeSerialColumn implements AttributeConverter<RopeSerial, String> {

        @Override
        public String convertToDatabaseColumn(RopeSerial value) {
            return value == null ? null : value.toString();
        }

        @Override
        public RopeSerial convertToEntityAttribute(String value) {
            return value == null ? null : RopeSerial.parse(value);
        }
    }

    /** A board filing number, stored in its printed form, such as {@code UT-2025-0147}. */
    @Converter(autoApply = true)
    public static final class FilingNumberColumn implements AttributeConverter<FilingNumber, String> {

        @Override
        public String convertToDatabaseColumn(FilingNumber value) {
            return value == null ? null : value.toString();
        }

        @Override
        public FilingNumber convertToEntityAttribute(String value) {
            return value == null ? null : FilingNumber.parse(value);
        }
    }

    /**
     * A decimal held to three places, stored as a whole number of thousandths. A fourth place is
     * rounded to the nearest thousandth, a tie going to the even one.
     */
    @Converter
    public static final class DecimalColumn implements AttributeConverter<BigDecimal, Integer> {

        @Override
        public Integer convertToDatabaseColumn(BigDecimal value) {
            return value == null
                    ? null
                    : Decimals.quantize(Decimals.multiply(value, 1000), 0).intValueExact();
        }

        @Override
        public BigDecimal convertToEntityAttribute(Integer value) {
            return value == null ? null : Decimals.divide(value, 1000);
        }
    }

    /**
     * An identifier that is a row's key, stored as text in its printed form. A row's key names its
     * type with {@code @JavaType}, and every row pointing at it reads the same type back.
     *
     * @param <T> the identifier
     */
    public abstract static class Key<T> extends AbstractClassJavaType<T> {

        private final transient Function<T, String> printed;
        private final transient Function<String, T> parsed;

        /**
         * A key type.
         *
         * @param type the identifier
         * @param printed how it is written
         * @param parsed how it is read back
         */
        protected Key(Class<T> type, Function<T, String> printed, Function<String, T> parsed) {
            super(type);
            this.printed = printed;
            this.parsed = parsed;
        }

        @Override
        public JdbcType getRecommendedJdbcType(JdbcTypeIndicators indicators) {
            return indicators.getJdbcType(Types.VARCHAR);
        }

        @Override
        public String toString(T value) {
            return printed.apply(value);
        }

        @Override
        public T fromString(CharSequence text) {
            return parsed.apply(text.toString());
        }

        @Override
        public <X> X unwrap(T value, Class<X> type, WrapperOptions options) {
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            if (type.isAssignableFrom(String.class)) {
                return type.cast(printed.apply(value));
            }
            throw unknownUnwrap(type);
        }

        @Override
        public <X> T wrap(X value, WrapperOptions options) {
            if (value == null) {
                return null;
            }
            if (getJavaTypeClass().isInstance(value)) {
                return getJavaTypeClass().cast(value);
            }
            if (value instanceof String text) {
                return parsed.apply(text);
            }
            throw unknownWrap(value.getClass());
        }
    }

    /** A lift code as a key. */
    public static final class LiftCodeKey extends Key<LiftCode> {
        /** The key type. */
        public LiftCodeKey() {
            super(LiftCode.class, LiftCode::value, LiftCode::new);
        }
    }

    /** A rope serial as a key. */
    public static final class RopeSerialKey extends Key<RopeSerial> {
        /** The key type. */
        public RopeSerialKey() {
            super(RopeSerial.class, RopeSerial::toString, RopeSerial::parse);
        }
    }

    /** A board filing number as a key. */
    public static final class FilingNumberKey extends Key<FilingNumber> {
        /** The key type. */
        public FilingNumberKey() {
            super(FilingNumber.class, FilingNumber::toString, FilingNumber::parse);
        }
    }
}
