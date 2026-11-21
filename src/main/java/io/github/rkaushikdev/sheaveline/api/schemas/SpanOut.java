package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Span;

/**
 * One span of rope between two supports, with its figures worked out.
 *
 * @param index the span's number, from one at the bottom
 * @param label from which support to which
 * @param horizontal the horizontal distance it covers
 * @param rise how much it climbs
 * @param chord the straight line between the supports
 * @param slope the chord's slope
 */
public record SpanOut(
        int index,
        String label,
        @LengthFeet Length horizontal,
        @LengthFeet Length rise,
        @LengthFeet Length chord,
        @AngleText Angle slope) {

    /**
     * The span as the tablet shows it.
     *
     * @param span the span
     * @return its shape
     */
    public static SpanOut of(Span span) {
        return new SpanOut(span.index(), span.label(), span.horizontal(), span.rise(), span.chord(), span.slope());
    }
}
