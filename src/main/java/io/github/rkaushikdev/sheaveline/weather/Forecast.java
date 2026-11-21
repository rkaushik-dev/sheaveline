package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One forecast, issued once, covering a run of periods, and read against the same rules the
 * anemometer is read against.
 *
 * <p>The department does not act on a forecast, it plans against one. Whether to put a second crew
 * on the summit lift at ten, whether to warn the ski school that the tram will be down after two,
 * whether to run the groomers up the far side while the wind is still under the limit.
 *
 * <p>The one thing that must not happen is a forecast judged by a different standard from a
 * reading. So a period is turned into an observation and handed to the same hold engine, and if the
 * answer disagrees with what the anemometer says later, that is the weather being wrong rather than
 * the software.
 *
 * @param station the station the forecast is for
 * @param issued when it was issued
 * @param issuedBy who issued it
 * @param periods the periods, in order and not overlapping
 */
public record Forecast(Station station, LocalDateTime issued, String issuedBy, List<ForecastPeriod> periods) {

    /**
     * A forecast.
     *
     * @throws WeatherError when nobody issued it, it has no periods, or two periods overlap
     */
    public Forecast {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(issuedBy, "issuedBy");
        periods = List.copyOf(periods);
        if (issuedBy.strip().isEmpty()) {
            throw new WeatherError("a forecast names who issued it");
        }
        if (periods.isEmpty()) {
            throw new WeatherError("a forecast has periods in it");
        }
        for (int index = 1; index < periods.size(); index++) {
            if (periods.get(index).starts().isBefore(periods.get(index - 1).ends())) {
                throw new WeatherError("two forecast periods overlap");
            }
        }
    }

    /**
     * A forecast with its periods put in order of their start.
     *
     * @param station the station the forecast is for
     * @param issued when it was issued
     * @param issuedBy who issued it
     * @param periods the periods, in any order
     * @return the forecast
     * @throws WeatherError when nobody issued it, it has no periods, or two periods overlap
     */
    public static Forecast of(Station station, LocalDateTime issued, String issuedBy, List<ForecastPeriod> periods) {
        List<ForecastPeriod> ordered = periods.stream()
                .sorted(Comparator.comparing(ForecastPeriod::starts))
                .toList();
        return new Forecast(station, issued, issuedBy, ordered);
    }

    /**
     * The period covering a moment.
     *
     * @param moment the moment
     * @return the period, or empty when the forecast does not reach it
     */
    public Optional<ForecastPeriod> at(LocalDateTime moment) {
        return periods.stream().filter(period -> period.covers(moment)).findFirst();
    }

    /**
     * The period with the strongest peak wind. The earliest wins a tie.
     *
     * @return the period
     */
    public ForecastPeriod windiest() {
        return periods.stream()
                .max(Comparator.comparingLong(one -> one.wind().peak().milsPerMinute()))
                .orElseThrow();
    }

    /**
     * The coldest period. The earliest wins a tie.
     *
     * @return the period
     */
    public ForecastPeriod coldest() {
        return periods.stream()
                .min(Comparator.comparingLong(one -> one.temperature().decidegreesF()))
                .orElseThrow();
    }

    /**
     * The same engine that reads the anemometer, given a forecast instead.
     *
     * @param period the period
     * @param lift the machine
     * @return the holds the period is expected to bring
     */
    public List<Hold> holdsIn(ForecastPeriod period, Lift lift) {
        return new WeatherHold(lift, period.asObservation(station)).holds();
    }

    /**
     * The periods expected to hold the machine.
     *
     * @param lift the machine
     * @return the periods, in order
     */
    public List<ForecastPeriod> expectedHolds(Lift lift) {
        return periods.stream().filter(one -> !holdsIn(one, lift).isEmpty()).toList();
    }

    /**
     * The first period expected to hold the machine.
     *
     * @param lift the machine
     * @return the period, or empty when none is
     */
    public Optional<ForecastPeriod> firstHold(Lift lift) {
        return expectedHolds(lift).stream().findFirst();
    }

    /**
     * The periods the machine is expected to run through.
     *
     * @param lift the machine
     * @return the periods, in order
     */
    public List<ForecastPeriod> clearPeriods(Lift lift) {
        return periods.stream().filter(one -> holdsIn(one, lift).isEmpty()).toList();
    }

    /**
     * When the machine is next expected to be runnable.
     *
     * @param lift the machine
     * @param moment the moment asked from
     * @return the start of the first clear period not yet over, or the moment itself when that
     *     period has already begun; empty when the forecast never clears
     */
    public Optional<LocalDateTime> clearAfter(Lift lift, LocalDateTime moment) {
        for (ForecastPeriod period : periods) {
            if (!period.ends().isAfter(moment)) {
                continue;
            }
            if (holdsIn(period, lift).isEmpty()) {
                return Optional.of(period.starts().isAfter(moment) ? period.starts() : moment);
            }
        }
        return Optional.empty();
    }

    /**
     * The periods confident enough to plan against.
     *
     * @return the periods, in order
     */
    public List<ForecastPeriod> worthActingOn() {
        return periods.stream()
                .filter(one -> one.confidence().worthPlanningAgainst())
                .toList();
    }

    /**
     * The stretch of time the forecast speaks for.
     *
     * @return the start of the first period and the end of the last
     */
    public Coverage covers() {
        return new Coverage(periods.getFirst().starts(), periods.getLast().ends());
    }

    /**
     * How many whole hours old the forecast is, which is how far to trust it.
     *
     * @param now the moment
     * @return the whole hours since it was issued
     * @throws WeatherError when the moment is before the forecast was issued
     */
    public long ageAt(LocalDateTime now) {
        if (now.isBefore(issued)) {
            throw new WeatherError("that moment is before the forecast was issued");
        }
        return ChronoUnit.HOURS.between(issued, now);
    }

    /**
     * How many periods the forecast has.
     *
     * @return the count
     */
    public int size() {
        return periods.size();
    }

    /**
     * The stretch of time a forecast speaks for.
     *
     * @param starts the start of its first period
     * @param ends the end of its last period
     */
    public record Coverage(LocalDateTime starts, LocalDateTime ends) {}
}
