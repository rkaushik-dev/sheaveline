package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Every autumn's shots, so a tower's movement can be read as a rate.
 *
 * <p>The trend is per tower, not per line. Two towers on the same lift with the same absolute
 * movement are not the same finding, because the one on the fill is doing what a fill does and the
 * one on rock is telling you something.
 *
 * @param surveys the surveys, oldest first
 */
public record AlignmentHistory(List<AlignmentSurvey> surveys) {

    /** A rate needs at least this many shots of the tower. */
    public static final int LEAST_SHOTS = 2;

    /**
     * A history.
     *
     * @throws PlantError when it is empty or two surveys share a day
     */
    public AlignmentHistory {
        surveys = List.copyOf(surveys);
        if (surveys.isEmpty()) {
            throw new PlantError("a history has surveys in it");
        }
        if (surveys.stream().map(AlignmentSurvey::taken).distinct().count() != surveys.size()) {
            throw new PlantError("two surveys on the same day cannot both be the trend");
        }
    }

    /**
     * A history with its surveys put in date order.
     *
     * @param surveys the surveys, in any order
     * @return the history
     */
    public static AlignmentHistory of(List<AlignmentSurvey> surveys) {
        return new AlignmentHistory(surveys.stream()
                .sorted(Comparator.comparing(AlignmentSurvey::taken))
                .toList());
    }

    /**
     * The most recent survey.
     *
     * @return the latest
     */
    public AlignmentSurvey latest() {
        return surveys.get(surveys.size() - 1);
    }

    /**
     * The earliest survey.
     *
     * @return the first
     */
    public AlignmentSurvey first() {
        return surveys.get(0);
    }

    /**
     * Every shot of one tower, with the day it was taken.
     *
     * @param ref the tower
     * @return the dated shots, oldest first
     */
    public List<DatedShot> shotsOf(TowerRef ref) {
        List<DatedShot> found = new ArrayList<>();
        for (AlignmentSurvey one : surveys) {
            one.shot(ref).ifPresent(shot -> found.add(new DatedShot(one.taken(), shot)));
        }
        return List.copyOf(found);
    }

    /**
     * How fast the top is going over.
     *
     * @param ref the tower
     * @return the lean a year, truncated to a whole millidegree, or empty when the file will not
     *     carry a line
     */
    public Optional<Angle> leanAYear(TowerRef ref) {
        List<DatedShot> shots = shotsOf(ref);
        Optional<BigDecimal> years = span(shots);
        if (years.isEmpty()) {
            return Optional.empty();
        }
        Length moved =
                last(shots).shot().offsetAtTop().minus(shots.get(0).shot().offsetAtTop());
        Length height = latest().tower(ref).height();
        long rate = Angle.ofRiseRun(moved, height).millidegrees();
        return Optional.of(new Angle(truncated(Decimals.divide(Decimals.of(rate), years.get()))));
    }

    /**
     * How fast the footing is going down.
     *
     * @param ref the tower
     * @return the settlement a year, truncated to a whole mil, or empty when the file will not carry
     *     a line
     */
    public Optional<Length> settlingAYear(TowerRef ref) {
        List<DatedShot> shots = shotsOf(ref);
        Optional<BigDecimal> years = span(shots);
        if (years.isEmpty()) {
            return Optional.empty();
        }
        long dropped = shots.get(0).shot().crossarmElevation().mils()
                - last(shots).shot().crossarmElevation().mils();
        return Optional.of(new Length(truncated(Decimals.divide(Decimals.of(dropped), years.get()))));
    }

    /**
     * When the lean reaches the plumb tolerance, which is the number worth budgeting on.
     *
     * @param ref the tower
     * @return years left, zero when already over, or empty when it is not going over
     */
    public Optional<BigDecimal> yearsUntilOutOfPlumb(TowerRef ref) {
        return yearsUntilOutOfPlumb(ref, TowerShot.PLUMB_TOLERANCE);
    }

    /**
     * When the lean reaches a tolerance.
     *
     * @param ref the tower
     * @param tolerance the largest lean allowed
     * @return years left, zero when already over, or empty when it is not going over
     */
    public Optional<BigDecimal> yearsUntilOutOfPlumb(TowerRef ref, Angle tolerance) {
        Optional<Angle> rate = leanAYear(ref);
        if (rate.isEmpty() || rate.get().millidegrees() <= 0) {
            return Optional.empty();
        }
        long now = Math.abs(latest().lean(ref).millidegrees());
        long left = tolerance.millidegrees() - now;
        if (left <= 0) {
            return Optional.of(BigDecimal.ZERO);
        }
        return Optional.of(Decimals.divide(left, rate.get().millidegrees()));
    }

    /**
     * Every tower going over, soonest first. The ones on rock come first.
     *
     * @return those towers
     */
    public List<TowerRef> moving() {
        return moving(TowerShot.PLUMB_TOLERANCE);
    }

    /**
     * Every tower going over against a tolerance, soonest first.
     *
     * @param tolerance the largest lean allowed
     * @return those towers
     */
    public List<TowerRef> moving(Angle tolerance) {
        record Projected(BigDecimal years, TowerRef ref) {}
        List<Projected> found = new ArrayList<>();
        for (TowerShot one : latest().shots()) {
            yearsUntilOutOfPlumb(one.ref(), tolerance).ifPresent(years -> found.add(new Projected(years, one.ref())));
        }
        return found.stream()
                .sorted(Comparator.comparing(Projected::years))
                .map(Projected::ref)
                .toList();
    }

    /**
     * How many surveys are on file.
     *
     * @return the count
     */
    public int size() {
        return surveys.size();
    }

    private static Optional<BigDecimal> span(List<DatedShot> shots) {
        if (shots.size() < LEAST_SHOTS) {
            return Optional.empty();
        }
        long days = ChronoUnit.DAYS.between(shots.get(0).taken(), last(shots).taken());
        BigDecimal years = Decimals.divide(days, 365);
        return years.signum() <= 0 ? Optional.empty() : Optional.of(years);
    }

    private static DatedShot last(List<DatedShot> shots) {
        return shots.get(shots.size() - 1);
    }

    private static long truncated(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /**
     * One shot of a tower and the day it was taken.
     *
     * @param taken the survey day
     * @param shot the shot
     */
    public record DatedShot(LocalDate taken, TowerShot shot) {

        /** A dated shot, nothing left null. */
        public DatedShot {
            Objects.requireNonNull(taken, "taken");
            Objects.requireNonNull(shot, "shot");
        }
    }
}
