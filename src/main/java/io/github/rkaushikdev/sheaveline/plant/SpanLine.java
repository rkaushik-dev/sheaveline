package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Every span of one lift, from the bottom terminal to the top. The line is cut into spans once, in
 * order, and everything downstream reads the list. Spans are looked up by index, counting from one.
 *
 * @param spans the spans in order up the hill
 */
public record SpanLine(List<Span> spans) implements Iterable<Span> {

    /** A line of spans, copied. */
    public SpanLine {
        spans = List.copyOf(spans);
        if (spans.isEmpty()) {
            throw new PlantError("a line has at least two supports");
        }
    }

    /**
     * Cut a list of supports into spans.
     *
     * @param supports the supports in order up the hill
     * @return the line
     * @throws PlantError when there are fewer than two supports, or a span does not climb
     */
    public static SpanLine through(List<Support> supports) {
        if (supports.size() < 2) {
            throw new PlantError("a line has at least two supports");
        }
        List<Span> spans = new ArrayList<>();
        for (int at = 1; at < supports.size(); at++) {
            spans.add(new Span(at, supports.get(at - 1), supports.get(at)));
        }
        return new SpanLine(spans);
    }

    /**
     * The spans between two terminals and the towers in between.
     *
     * @param bottom the bottom terminal
     * @param towers the towers
     * @param top the top terminal
     * @return the line
     */
    public static SpanLine between(Support bottom, TowerLine towers, Support top) {
        List<Support> supports = new ArrayList<>();
        supports.add(bottom);
        towers.forEach(tower -> supports.add(Support.ofTower(tower)));
        supports.add(top);
        return through(supports);
    }

    /**
     * How many spans there are.
     *
     * @return the count
     */
    public int size() {
        return spans.size();
    }

    @Override
    public Iterator<Span> iterator() {
        return spans.iterator();
    }

    /**
     * A span by index.
     *
     * @param index counting from one at the bottom
     * @return the span
     * @throws PlantError when there is no such span
     */
    public Span span(int index) {
        if (index < 1 || index > spans.size()) {
            throw new PlantError("there is no span " + index);
        }
        return spans.get(index - 1);
    }

    /**
     * Every support, bottom to top.
     *
     * @return one more support than there are spans
     */
    public List<Support> supports() {
        List<Support> all = new ArrayList<>();
        all.add(spans.get(0).lower());
        spans.forEach(span -> all.add(span.upper()));
        return List.copyOf(all);
    }

    /**
     * The rope path from bottom to top, summed span by span.
     *
     * @return the total chord length
     */
    public Length slopeLength() {
        return spans.stream().map(Span::chord).reduce(Length.ZERO, Length::plus);
    }

    /**
     * The distance along the ground from bottom to top.
     *
     * @return the horizontal length
     */
    public Length horizontalLength() {
        return last().upper().station().minus(spans.get(0).lower().station());
    }

    /**
     * How far the rope climbs from bottom to top.
     *
     * @return the vertical rise
     */
    public Length verticalRise() {
        return last().upper().ropeElevation().minus(spans.get(0).lower().ropeElevation());
    }

    /**
     * The slope of the whole line.
     *
     * @return the angle off the vertical rise and the horizontal length
     */
    public Angle averageSlope() {
        return Angle.ofRiseRun(verticalRise(), horizontalLength());
    }

    /**
     * The span with the longest ground distance, the lowest on a tie.
     *
     * @return that span
     */
    public Span longest() {
        return greatest(span -> span.horizontal().mils());
    }

    /**
     * The span with the steepest chord, uphill or down, the lowest on a tie.
     *
     * @return that span
     */
    public Span steepest() {
        return greatest(span -> Math.abs(span.slope().millidegrees()));
    }

    /**
     * The spans the rope drops over.
     *
     * @return those spans in order
     */
    public List<Span> downhillSpans() {
        return spans.stream().filter(Span::isDownhill).toList();
    }

    /**
     * The span a station falls in, the lower one when it sits on a support.
     *
     * @param station the station
     * @return the span
     * @throws PlantError when the station is off the line
     */
    public Span containing(Length station) {
        for (Span span : spans) {
            if (span.lower().station().compareTo(station) <= 0
                    && station.compareTo(span.upper().station()) <= 0) {
                return span;
            }
        }
        throw new PlantError("station " + Decimals.fixed(station.feet(), 0) + " is off the line");
    }

    private Span last() {
        return spans.get(spans.size() - 1);
    }

    private Span greatest(ToLongFunction<Span> measure) {
        Span best = spans.get(0);
        for (Span span : spans) {
            if (measure.applyAsLong(span) > measure.applyAsLong(best)) {
                best = span;
            }
        }
        return best;
    }
}
