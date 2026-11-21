package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import io.github.rkaushikdev.sheaveline.plant.GripType;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The retorque programme for one fixed grip fleet, and the interval that closes up when grips come
 * loose.
 *
 * <p>Every grip gets a wrench on it on an interval. The interval is not fixed: if more than one grip
 * in ten is found slack, the fleet is telling the department that the interval is too long, and it
 * halves until a clean round says otherwise.
 *
 * @param fleet the carriers on the lift
 * @param specified the figure every grip is set to
 * @param checks the checks made, in order of date and then carrier
 * @param baseIntervalDays the interval while the fleet is holding its torque
 */
public record RetorqueCycle(CarrierSet fleet, Torque specified, List<TorqueCheck> checks, int baseIntervalDays) {

    /** More than this fraction of checks found slack halves the interval. */
    public static final BigDecimal SLACK_RATE_TRIGGER = new BigDecimal("0.10");

    /** Days between retorques while the fleet holds. */
    public static final int BASE_INTERVAL_DAYS = 365;

    /**
     * A cycle on a fixed grip fleet.
     *
     * @throws RopeworkError when any carrier is not on a fixed grip, the interval is under a day, or a
     *     check is on a carrier that is not on the lift
     */
    public RetorqueCycle {
        Objects.requireNonNull(fleet, "fleet");
        Objects.requireNonNull(specified, "specified");
        checks = List.copyOf(checks);
        for (Carrier carrier : fleet) {
            if (carrier.grip().map(grip -> grip.kind() != GripType.FIXED).orElse(true)) {
                throw new RopeworkError("only a fixed grip fleet is retorqued on a cycle");
            }
        }
        if (baseIntervalDays < 1) {
            throw new RopeworkError("a retorque interval is at least a day");
        }
        Set<Integer> onLine = fleet.carriers().stream().map(Carrier::number).collect(Collectors.toSet());
        for (TorqueCheck check : checks) {
            if (!onLine.contains(check.carrier())) {
                throw new RopeworkError("carrier " + check.carrier() + " is not on this lift");
            }
        }
    }

    /**
     * A cycle with nothing checked yet, on the base interval.
     *
     * @param fleet the carriers
     * @param specified the figure
     * @return the cycle
     */
    public static RetorqueCycle of(CarrierSet fleet, Torque specified) {
        return of(fleet, specified, List.of());
    }

    /**
     * A cycle on the base interval with its checks put in order.
     *
     * @param fleet the carriers
     * @param specified the figure
     * @param checks the checks, in any order
     * @return the cycle
     */
    public static RetorqueCycle of(CarrierSet fleet, Torque specified, List<TorqueCheck> checks) {
        return of(fleet, specified, checks, BASE_INTERVAL_DAYS);
    }

    /**
     * A cycle with its checks put in order of date and then carrier.
     *
     * @param fleet the carriers
     * @param specified the figure
     * @param checks the checks, in any order
     * @param baseIntervalDays the interval while the fleet holds
     * @return the cycle
     */
    public static RetorqueCycle of(CarrierSet fleet, Torque specified, List<TorqueCheck> checks, int baseIntervalDays) {
        List<TorqueCheck> ordered = checks.stream()
                .sorted(Comparator.comparing(TorqueCheck::checked).thenComparingInt(TorqueCheck::carrier))
                .toList();
        return new RetorqueCycle(fleet, specified, ordered, baseIntervalDays);
    }

    /**
     * The checks that found a grip slack.
     *
     * @return those checks, in order
     */
    public List<TorqueCheck> slackFound() {
        return checks.stream().filter(TorqueCheck::foundSlack).toList();
    }

    /**
     * The fraction of checks that found a grip slack.
     *
     * @return the rate, zero when nothing has been checked
     */
    public BigDecimal slackRate() {
        if (checks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return Decimals.divide(slackFound().size(), checks.size());
    }

    /**
     * The interval in force, halved while the fleet is coming loose.
     *
     * @return days between retorques
     */
    public int intervalDays() {
        if (slackRate().compareTo(SLACK_RATE_TRIGGER) > 0) {
            return Math.floorDiv(baseIntervalDays, 2);
        }
        return baseIntervalDays;
    }

    /**
     * The most recent check on one carrier.
     *
     * @param carrier the carrier number
     * @return the check, or empty when the grip has never been checked
     */
    public Optional<TorqueCheck> lastCheck(int carrier) {
        for (int index = checks.size() - 1; index >= 0; index--) {
            if (checks.get(index).carrier() == carrier) {
                return Optional.of(checks.get(index));
            }
        }
        return Optional.empty();
    }

    /**
     * When a grip next falls due, given the interval in force now.
     *
     * @param carrier the carrier number
     * @param fromDay the day to count from when the grip has never been checked
     * @return an interval after its last check, or after that day
     */
    public LocalDate dueOn(int carrier, LocalDate fromDay) {
        LocalDate start = lastCheck(carrier).map(TorqueCheck::checked).orElse(fromDay);
        return start.plusDays(intervalDays());
    }

    /**
     * The carriers with no check on file.
     *
     * @return their numbers, in fleet order
     */
    public List<Integer> neverChecked() {
        return fleet.carriers().stream()
                .map(Carrier::number)
                .filter(number -> lastCheck(number).isEmpty())
                .toList();
    }

    /**
     * The carriers more than an interval past their last check. A carrier never checked is not
     * counted here; it is in {@link #neverChecked()}.
     *
     * @param on the day asked about
     * @return their numbers, in fleet order
     */
    public List<Integer> overdue(LocalDate on) {
        int interval = intervalDays();
        List<Integer> late = new ArrayList<>();
        for (Carrier carrier : fleet) {
            Optional<TorqueCheck> last = lastCheck(carrier.number());
            if (last.isPresent() && on.isAfter(last.get().checked().plusDays(interval))) {
                late.add(carrier.number());
            }
        }
        return List.copyOf(late);
    }

    /**
     * Checks that ended with the grip still outside the band.
     *
     * @return those checks, in order
     */
    public List<TorqueCheck> leftWrong() {
        return checks.stream().filter(check -> !check.leftCorrect()).toList();
    }

    /**
     * The cycle with one more check on file.
     *
     * @param check the new check
     * @return the new cycle, its interval worked out again
     */
    public RetorqueCycle withCheck(TorqueCheck check) {
        List<TorqueCheck> more = new ArrayList<>(checks);
        more.add(check);
        return of(fleet, specified, more, baseIntervalDays);
    }

    /** Such as {@code 2 checks, 1 slack, interval 182 days}. */
    @Override
    public String toString() {
        return checks.size() + " checks, " + slackFound().size() + " slack, interval " + intervalDays() + " days";
    }
}
