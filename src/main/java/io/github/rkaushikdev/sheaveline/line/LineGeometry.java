package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One lift, one set of conditions, and the walk of the whole line that follows from them.
 *
 * <p>This answers the question the mountain manager actually asks, which is whether the line is fit
 * to run today with this much snow on the ground and this much tension in the rope. That means walking
 * every span for clearance and every tower for what the rope is doing to it, and then deciding what
 * the worst of it means.
 *
 * @param lift the machine
 * @param sag how its rope hangs
 * @param snow the snow allowance on the ground
 * @param uses what the ground under the line is used for
 */
public record LineGeometry(Lift lift, SagModel sag, Length snow, GroundUseMap uses) {

    private static final Length RESTRICTING_MARGIN = Length.ofFeet(3);

    /** A walk, nothing left null. */
    public LineGeometry {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(sag, "sag");
        Objects.requireNonNull(snow, "snow");
        Objects.requireNonNull(uses, "uses");
    }

    /**
     * A walk over open ground everywhere.
     *
     * @param lift the machine
     * @param sag how its rope hangs
     * @param snow the snow allowance on the ground
     */
    public LineGeometry(Lift lift, SagModel sag, Length snow) {
        this(lift, sag, snow, new GroundUseMap());
    }

    /**
     * The spans, bottom to top.
     *
     * @return every span on the line
     */
    public List<Span> spans() {
        return lift.spans().spans();
    }

    /**
     * Hanger plus the depth of the carrier under it, for the tightest carrier: the longest hanger on
     * the line.
     *
     * @return the drop below the rope
     * @throws java.util.NoSuchElementException when no carrier is on the line
     */
    public Length carrierDrop() {
        return lift.carriers().onLine().stream()
                .map(Carrier::hanger)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    /**
     * The clearance walk of one span under these conditions.
     *
     * @param span the span
     * @return the check
     */
    public ClearanceCheck clearanceCheck(Span span) {
        return new ClearanceCheck(span, lift.ground(), sag, carrierDrop(), snow, uses);
    }

    /**
     * The tightest reading of every span at the usual step.
     *
     * @return one reading a span, bottom to top
     */
    public List<ClearanceReading> tightestClearances() {
        return tightestClearances(ClearanceCheck.SAMPLE_STEP);
    }

    /**
     * The tightest reading of every span.
     *
     * @param step the sampling step
     * @return one reading a span, bottom to top
     */
    public List<ClearanceReading> tightestClearances(Length step) {
        return spans().stream().map(span -> clearanceCheck(span).tightest(step)).toList();
    }

    /**
     * The least margin anywhere on the line at the usual step.
     *
     * @return that reading
     */
    public ClearanceReading worstClearance() {
        return worstClearance(ClearanceCheck.SAMPLE_STEP);
    }

    /**
     * The least margin anywhere on the line, the lowest span of them on a tie.
     *
     * @param step the sampling step
     * @return that reading
     */
    public ClearanceReading worstClearance(Length step) {
        return tightestClearances(step).stream()
                .min(Comparator.comparingLong(reading -> reading.margin().mils()))
                .orElseThrow();
    }

    /**
     * What the rope does to every tower, each between the span below it and the span above.
     *
     * @return one loading a tower, bottom to top
     */
    public List<TowerLoading> towerLoadings() {
        List<Span> spans = spans();
        List<Tower> towers = lift.towers().towers();
        List<TowerLoading> loadings = new ArrayList<>();
        for (int position = 0; position < towers.size(); position++) {
            loadings.add(new TowerLoading(towers.get(position), spans.get(position), spans.get(position + 1), sag));
        }
        return List.copyOf(loadings);
    }

    /**
     * Everything wrong with the line at the usual step.
     *
     * @return the findings, spans first, then towers
     */
    public List<Finding> findings() {
        return findings(ClearanceCheck.SAMPLE_STEP);
    }

    /**
     * Everything wrong with the line. A span short of its clearance stops the lift and one with under
     * three feet of margin restricts it. A hold down that lifts off stops the lift, a tower whose rope
     * disagrees with its schedule restricts it, and one off its design deviation is noted.
     *
     * @param step the sampling step
     * @return the findings, spans first, then towers
     */
    public List<Finding> findings(Length step) {
        List<Finding> found = new ArrayList<>();
        for (Span span : spans()) {
            ClearanceReading worst = clearanceCheck(span).tightest(step);
            String where = "span " + span.index();
            if (!worst.passes()) {
                found.add(new Finding(
                        where,
                        Severity.STOP,
                        "clearance " + Decimals.fixed(worst.available().feet(), 1) + " ft over "
                                + worst.use().value() + ", "
                                + Decimals.fixed(worst.required().feet(), 0)
                                + " ft wanted"));
            } else if (worst.margin().compareTo(RESTRICTING_MARGIN) < 0) {
                found.add(new Finding(
                        where,
                        Severity.RESTRICT,
                        "only " + Decimals.fixed(worst.margin().feet(), 1) + " ft of margin over "
                                + worst.use().value()));
            }
        }
        for (TowerLoading loading : towerLoadings()) {
            String where = loading.tower().ref().toString();
            if (loading.liftsOff()) {
                found.add(new Finding(where, Severity.STOP, "hold down carries less than the minimum seating force"));
            } else if (!loading.agreesWithSchedule()) {
                found.add(new Finding(
                        where,
                        Severity.RESTRICT,
                        "rope bends " + loading.deviation().formatDegrees() + " against a " + kindsOf(loading.tower())
                                + " tower"));
            } else if (!loading.matchesDesign()) {
                found.add(new Finding(
                        where,
                        Severity.NOTE,
                        "deviation off design by " + loading.deviationError().formatDegrees()));
            }
        }
        return List.copyOf(found);
    }

    /**
     * What the walk at the usual step says about the line.
     *
     * @return the verdict
     */
    public GeometryVerdict verdict() {
        return verdict(ClearanceCheck.SAMPLE_STEP);
    }

    /**
     * What the walk says about the line, read off the worst finding and nothing else.
     *
     * @param step the sampling step
     * @return unfit for any stop, restricted for any restriction, otherwise fit
     */
    public GeometryVerdict verdict(Length step) {
        Set<Severity> severities = EnumSet.noneOf(Severity.class);
        findings(step).forEach(finding -> severities.add(finding.severity()));
        if (severities.contains(Severity.STOP)) {
            return GeometryVerdict.UNFIT;
        }
        if (severities.contains(Severity.RESTRICT)) {
            return GeometryVerdict.RESTRICTED;
        }
        return GeometryVerdict.FIT;
    }

    /**
     * The same walk with another snow allowance.
     *
     * @param changed the snow
     * @return the new walk
     */
    public LineGeometry withSnow(Length changed) {
        return new LineGeometry(lift, sag, changed, uses);
    }

    /**
     * The same walk with the rope hanging another way, such as under another tension.
     *
     * @param changed the sag model
     * @return the new walk
     */
    public LineGeometry withTension(SagModel changed) {
        return new LineGeometry(lift, changed, snow, uses);
    }

    private static String kindsOf(Tower tower) {
        return tower.kinds().stream().map(AssemblyKind::value).sorted().collect(Collectors.joining("/"));
    }
}
