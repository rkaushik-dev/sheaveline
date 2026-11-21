package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A wind observation as the summit station reports it, and what it does to a line.
 *
 * <p>The station sends a direction, a sustained speed and a gust. None of those is the number the
 * hold rules want. What matters is how much of the wind is trying to push carriers sideways off the
 * line, which depends on the angle between the wind and the line, and what the wind does to exposed
 * skin, which is why the chill is here and not with the temperature.
 *
 * @param speed the sustained speed
 * @param bearing where the wind comes from
 * @param gust the gust, when the station reported one
 */
public record Wind(Speed speed, Bearing bearing, Optional<Speed> gust) {

    /** The chill formula is only defined at or below this many degrees Fahrenheit. */
    public static final BigDecimal CHILL_CEILING_F = new BigDecimal("50");

    /** The chill formula is only defined above this many miles an hour. */
    public static final BigDecimal CHILL_FLOOR_MPH = new BigDecimal("3");

    /** No wind from anywhere. */
    public static final Wind CALM = new Wind(Speed.STOPPED, new Bearing(0));

    private static final Pattern SHORTHAND = Pattern.compile(
            "\\s*(?<from>[A-Za-z]{1,3}|\\d{1,3})\\s+(?<speed>\\d+(?:\\.\\d+)?)\\s*(?:g\\s*(?<gust>\\d+(?:\\.\\d+)?))?\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * An observation.
     *
     * @throws ParseError when the gust is below the sustained speed
     */
    public Wind {
        Objects.requireNonNull(speed, "speed");
        Objects.requireNonNull(bearing, "bearing");
        Objects.requireNonNull(gust, "gust");
        if (gust.isPresent() && gust.get().compareTo(speed) < 0) {
            throw new ParseError("gust " + gust.get() + " below sustained " + speed, "a wind observation");
        }
    }

    /**
     * An observation with no gust.
     *
     * @param speed the sustained speed
     * @param bearing where the wind comes from
     */
    public Wind(Speed speed, Bearing bearing) {
        this(speed, bearing, Optional.empty());
    }

    /**
     * An observation in miles an hour.
     *
     * @param mph the sustained speed
     * @param bearing where the wind comes from
     * @param gustMph the gust, when there was one
     * @return the observation
     */
    public static Wind ofMph(BigDecimal mph, Bearing bearing, Optional<BigDecimal> gustMph) {
        return new Wind(Speed.ofMilesPerHour(mph), bearing, gustMph.map(Speed::ofMilesPerHour));
    }

    /**
     * An observation in whole miles an hour with no gust.
     *
     * @param mph the sustained speed
     * @param bearing where the wind comes from, as a point or degrees
     * @return the observation
     */
    public static Wind ofMph(long mph, String bearing) {
        return ofMph(Decimals.of(mph), Bearing.parse(bearing), Optional.empty());
    }

    /**
     * An observation in whole miles an hour with a gust.
     *
     * @param mph the sustained speed
     * @param bearing where the wind comes from, as a point or degrees
     * @param gustMph the gust
     * @return the observation
     */
    public static Wind ofMph(long mph, String bearing, long gustMph) {
        return ofMph(Decimals.of(mph), Bearing.parse(bearing), Optional.of(Decimals.of(gustMph)));
    }

    /**
     * Read the station shorthand, {@code SW 24G38} or {@code 225 24}.
     *
     * @param text what the station sent
     * @return the observation
     * @throws ParseError when it is not an observation
     */
    public static Wind parse(String text) {
        Matcher match = SHORTHAND.matcher(text);
        if (!match.matches()) {
            throw new ParseError(text, "a wind observation");
        }
        return ofMph(
                Decimals.of(match.group("speed")),
                Bearing.parse(match.group("from")),
                Optional.ofNullable(match.group("gust")).map(Decimals::of));
    }

    /**
     * The number a hold rule reads, which is the gust when there is one.
     *
     * @return the gust or the sustained speed
     */
    public Speed peak() {
        return gust.orElse(speed);
    }

    /**
     * Where the wind is going.
     *
     * @return the reciprocal of where it comes from
     */
    public Bearing blowingToward() {
        return bearing.reciprocal();
    }

    /**
     * How much of the wind is trying to push carriers off the line.
     *
     * @param line the bearing of the line, bottom to top
     * @return the cross component of the sustained speed
     */
    public Speed across(Bearing line) {
        return resolve(line, StrictMath::sin);
    }

    /**
     * How much of the wind runs up or down the line.
     *
     * @param line the bearing of the line, bottom to top
     * @return the along component of the sustained speed
     */
    public Speed along(Bearing line) {
        return resolve(line, StrictMath::cos);
    }

    /**
     * The cross component of the gust, which is what a hold reads.
     *
     * @param line the bearing of the line
     * @return the cross component of the peak
     */
    public Speed peakAcross(Bearing line) {
        return new Wind(peak(), bearing).across(line);
    }

    /**
     * Whether the wind opposes a carrier travelling up the line.
     *
     * @param line the bearing of the line
     * @return true when it blows down the line
     */
    public boolean isHeadwind(Bearing line) {
        return StrictMath.cos(offset(line)) < 0;
    }

    /**
     * The weather service wind chill, in Fahrenheit.
     *
     * <p>The formula is only defined at or below fifty degrees with the wind at three miles an hour
     * or more. Outside that the reading stands as it is.
     *
     * @param temperature the air temperature
     * @return what the wind makes it feel like
     */
    public Temperature chill(Temperature temperature) {
        BigDecimal degrees = temperature.degreesF();
        BigDecimal mph = speed.milesPerHour();
        if (degrees.compareTo(CHILL_CEILING_F) > 0 || mph.compareTo(CHILL_FLOOR_MPH) < 0) {
            return temperature;
        }
        BigDecimal factor = Decimals.of(StrictMath.pow(mph.doubleValue(), 0.16));
        BigDecimal chilled = new BigDecimal("35.74")
                .add(Decimals.multiply(new BigDecimal("0.6215"), degrees), Decimals.CONTEXT)
                .subtract(Decimals.multiply(new BigDecimal("35.75"), factor), Decimals.CONTEXT)
                .add(Decimals.multiply(Decimals.multiply(new BigDecimal("0.4275"), degrees), factor), Decimals.CONTEXT);
        return Temperature.ofDegreesF(chilled);
    }

    /**
     * The station shorthand: the point, whole miles an hour, and the gust after a {@code G}.
     *
     * @return such as {@code SW 24G38} or {@code W 18}
     */
    public String formatShorthand() {
        BigDecimal sustained = Decimals.quantize(speed.milesPerHour(), 0);
        return gust.map(one ->
                        bearing.nearestPoint() + " " + sustained + "G" + Decimals.quantize(one.milesPerHour(), 0))
                .orElse(bearing.nearestPoint() + " " + sustained);
    }

    /** The station shorthand. */
    @Override
    public String toString() {
        return formatShorthand();
    }

    private double offset(Bearing line) {
        return line.difference(blowingToward()).radians();
    }

    private Speed resolve(Bearing line, DoubleUnaryOperator part) {
        double component = Math.abs(part.applyAsDouble(offset(line)));
        return new Speed((long) Math.rint(speed.milsPerMinute() * component));
    }
}
