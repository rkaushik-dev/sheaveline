package io.github.rkaushikdev.sheaveline.ropework;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One threshold on one reading of a wear survey, and what breaching it costs.
 *
 * @param name the reading the row reads, which is the finding's source
 * @param verdict what breaching it costs
 * @param reading how the reading is taken off a survey
 * @param breached whether a reading breaches the threshold
 * @param describe how a breaching reading is printed
 */
public record WearRule(
        String name,
        WearVerdict verdict,
        Function<WearSurvey, BigDecimal> reading,
        Predicate<BigDecimal> breached,
        Function<BigDecimal, String> describe) {

    /** A row. */
    public WearRule {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(breached, "breached");
        Objects.requireNonNull(describe, "describe");
    }

    /**
     * Read the row against a survey.
     *
     * @param survey the survey
     * @return the finding when the row fires, otherwise empty
     */
    public Optional<Finding> check(WearSurvey survey) {
        BigDecimal value = reading.apply(survey);
        if (!breached.test(value)) {
            return Optional.empty();
        }
        return Optional.of(new Finding(name, verdict, describe.apply(value)));
    }
}
