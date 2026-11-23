package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The one place that says what the rope file makes of a survey.
 *
 * <p>There were three of these once, one on each survey type, and each was a chain of tests with the
 * thresholds buried in it. With three, the department had three places to look when the board asked
 * where a figure came from, and the three did not agree about what monitor meant.
 *
 * <p>So the thresholds are rows now, each row says which reading it reads and what it costs, and the
 * surveys carry readings rather than opinions. {@link RopeFile} reads every source together and
 * returns the worst of them with the rows that fired attached.
 */
public final class Verdict {

    private static final BigDecimal CONDEMNING_PERCENT = BigDecimal.valueOf(93);
    private static final BigDecimal WATCHED_PERCENT = BigDecimal.valueOf(97);
    private static final BigDecimal CONDEMNING_STRETCH = new BigDecimal("0.05");
    private static final BigDecimal WATCHED_STRETCH = new BigDecimal("0.02");
    private static final BigDecimal WATCHED_OVALITY = new BigDecimal("0.05");

    /** The wear rows, condemning rows first. */
    public static final List<WearRule> WEAR_RULES = List.of(
            new WearRule(
                    "diameter",
                    WearVerdict.CONDEMNED,
                    Verdict::worstPercent,
                    value -> value.compareTo(CONDEMNING_PERCENT) < 0,
                    Verdict::ofNominal),
            new WearRule(
                    "lay length",
                    WearVerdict.CONDEMNED,
                    WearSurvey::worstLayStretch,
                    value -> value.compareTo(CONDEMNING_STRETCH) > 0,
                    Verdict::layOpened),
            new WearRule(
                    "diameter",
                    WearVerdict.MONITOR,
                    Verdict::worstPercent,
                    value -> value.compareTo(WATCHED_PERCENT) < 0,
                    Verdict::ofNominal),
            new WearRule(
                    "ovality",
                    WearVerdict.MONITOR,
                    Verdict::ovalityFraction,
                    value -> value.compareTo(WATCHED_OVALITY) > 0,
                    value -> percent(value) + "% out of round"),
            new WearRule(
                    "lay length",
                    WearVerdict.MONITOR,
                    WearSurvey::worstLayStretch,
                    value -> value.compareTo(WATCHED_STRETCH) > 0,
                    Verdict::layOpened));

    /** The trace rows, the loss of area before the local faults. */
    public static final List<TraceRule> TRACE_RULES = List.of(
            new TraceRule(
                    "loss of area",
                    MagneticTest::peakLoss,
                    MagneticTest.LMA_CONDEMN,
                    MagneticTest.LMA_WATCH,
                    value -> value + "% loss of area"),
            new TraceRule(
                    "local fault",
                    test -> BigDecimal.valueOf(test.wiresInOneLay()),
                    BigDecimal.valueOf(MagneticTest.LOCAL_FAULT_CONDEMN),
                    BigDecimal.valueOf(MagneticTest.LOCAL_FAULT_WATCH),
                    value -> value + " wires broken inside one lay"));

    private Verdict() {}

    /**
     * Every wear row that fires on a survey.
     *
     * @param survey the survey
     * @return the findings, in row order
     */
    public static List<Finding> wearFindings(WearSurvey survey) {
        return WEAR_RULES.stream()
                .map(rule -> rule.check(survey))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * What a wear survey costs the rope.
     *
     * @param survey the survey
     * @return the worst verdict any row reached
     */
    public static WearVerdict assessWear(WearSurvey survey) {
        return worstOf(wearFindings(survey));
    }

    /**
     * Every broken wire station that fires: condemning for one over the allowance, monitor for one
     * over the watch level. Each finding names the station and every limit it is over.
     *
     * @param survey the survey
     * @return the findings, condemning ones first
     */
    public static List<Finding> breakFindings(BreakSurvey survey) {
        BreakLimits allowed = survey.limits();
        BreakLimits watched = allowed.watchLevel();
        return Stream.concat(
                        survey.exceedances().stream().map(seen -> breakFinding(seen, WearVerdict.CONDEMNED, allowed)),
                        survey.nearLimit().stream().map(seen -> breakFinding(seen, WearVerdict.MONITOR, watched)))
                .toList();
    }

    private static Finding breakFinding(BreakObservation seen, WearVerdict verdict, BreakLimits limits) {
        return new Finding("broken wires", verdict, seen + ", " + String.join(", ", seen.breaches(limits)));
    }

    /**
     * What a broken wire survey costs the rope.
     *
     * @param survey the survey
     * @return the worst verdict any station reached
     */
    public static WearVerdict assessBreaks(BreakSurvey survey) {
        return worstOf(breakFindings(survey));
    }

    /**
     * What fires on a magnetic test: the peak loss of metallic area, and the most wires the local
     * faults add up to inside one lay, each at the worse of its two levels.
     *
     * @param test the test
     * @return the findings, the loss before the faults
     */
    public static List<Finding> testFindings(MagneticTest test) {
        return TRACE_RULES.stream()
                .map(rule -> rule.check(test))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * What a magnetic test costs the rope.
     *
     * @param test the test
     * @return the worst verdict either reading reached
     */
    public static WearVerdict assessTest(MagneticTest test) {
        return worstOf(testFindings(test));
    }

    /**
     * The worst verdict among some findings.
     *
     * @param findings the findings
     * @return condemned over monitor over serviceable, serviceable when there are none
     */
    static WearVerdict worstOf(List<Finding> findings) {
        if (findings.stream().anyMatch(found -> found.verdict() == WearVerdict.CONDEMNED)) {
            return WearVerdict.CONDEMNED;
        }
        if (findings.stream().anyMatch(found -> found.verdict() == WearVerdict.MONITOR)) {
            return WearVerdict.MONITOR;
        }
        return WearVerdict.SERVICEABLE;
    }

    private static BigDecimal worstPercent(WearSurvey survey) {
        return survey.percentOfNominal(survey.smallest());
    }

    private static BigDecimal ovalityFraction(WearSurvey survey) {
        return survey.worstOvality().ovality().ratio(survey.nominal());
    }

    private static String ofNominal(BigDecimal value) {
        return Decimals.quantize(value, 1) + "% of nominal";
    }

    private static String layOpened(BigDecimal value) {
        return "lay opened " + percent(value) + "%";
    }

    private static BigDecimal percent(BigDecimal fraction) {
        return Decimals.quantize(Decimals.multiply(fraction, 100), 1);
    }
}
