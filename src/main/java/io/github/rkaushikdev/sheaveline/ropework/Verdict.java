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
    private static final BigDecimal WATCHED_NECK = BigDecimal.valueOf(3);

    /** The wear rows, one a reading, each finding once at the worse level it reaches. */
    public static final List<ReadingRule<WearSurvey>> WEAR_RULES = List.of(
            ReadingRule.below(
                    "diameter",
                    WearSurvey::smallestPercent,
                    CONDEMNING_PERCENT,
                    WATCHED_PERCENT,
                    value -> Decimals.quantize(value, 1) + "% of nominal"),
            ReadingRule.above(
                    "lay length",
                    WearSurvey::worstLayStretch,
                    CONDEMNING_STRETCH,
                    WATCHED_STRETCH,
                    value -> "lay opened " + percent(value) + "%"),
            ReadingRule.watchedAbove(
                    "ovality",
                    WearSurvey::worstOvalityFraction,
                    WATCHED_OVALITY,
                    value -> percent(value) + "% out of round"),
            ReadingRule.watchedAbove(
                    "local reduction",
                    WearSurvey::neckPercent,
                    WATCHED_NECK,
                    value -> Decimals.quantize(value, 1) + "% under the rest of the rope"));

    /** The trace rows: the loss of area, then the local faults, then the corrosion. */
    public static final List<ReadingRule<MagneticTest>> TRACE_RULES = List.of(
            ReadingRule.<MagneticTest>above(
                            "magnetic test",
                            MagneticTest::peakLoss,
                            MagneticTest.LMA_CONDEMN,
                            MagneticTest.LMA_WATCH,
                            value -> value + "% loss of area")
                    .named("loss of area"),
            ReadingRule.<MagneticTest>above(
                            "magnetic test",
                            test -> BigDecimal.valueOf(test.wiresInOneLay()),
                            BigDecimal.valueOf(MagneticTest.LOCAL_FAULT_CONDEMN),
                            BigDecimal.valueOf(MagneticTest.LOCAL_FAULT_WATCH),
                            value -> value + " wires broken inside one lay")
                    .named("local fault"),
            ReadingRule.<MagneticTest>above(
                            "magnetic test",
                            MagneticTest::peakCorrosion,
                            MagneticTest.CORROSION_CONDEMN,
                            MagneticTest.CORROSION_WATCH,
                            value -> value + "% corrosion")
                    .named("corrosion"));

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
     * What fires on a magnetic test: the peak loss of metallic area, the most wires the local faults
     * add up to inside one lay, and the worst corrosion, each at the worse of its two levels.
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
    public static WearVerdict worstOf(List<Finding> findings) {
        if (findings.stream().anyMatch(found -> found.verdict() == WearVerdict.CONDEMNED)) {
            return WearVerdict.CONDEMNED;
        }
        if (findings.stream().anyMatch(found -> found.verdict() == WearVerdict.MONITOR)) {
            return WearVerdict.MONITOR;
        }
        return WearVerdict.SERVICEABLE;
    }

    private static BigDecimal percent(BigDecimal fraction) {
        return Decimals.quantize(Decimals.multiply(fraction, 100), 1);
    }
}
