package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.plant.BrakeKind;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * One morning walk of one machine: the check sheet that gets walked before the first chair carries
 * anybody.
 *
 * <p>Some of the list is the same on every lift and some of it only exists on certain machines,
 * which is why the sheet is generated from the lift rather than printed once and photocopied. A
 * detachable has a grip conveyor to check and a fixed grip does not, and a sheet with a blank line
 * for something the machine does not have is a sheet people stop reading.
 *
 * @param lift the machine walked
 * @param day the morning it was walked
 * @param results what was recorded, line by line
 * @param mechanic the mechanic who walked it
 * @param operator the operator who walked it
 */
public record PreOpSheet(Lift lift, LocalDate day, List<CheckResult> results, String mechanic, String operator) {

    private static final Set<LiftClass> ALL_CLASSES = EnumSet.allOf(LiftClass.class);
    private static final Set<LiftClass> AERIAL = classesWhere(LiftClass::isAerial);
    private static final Set<LiftClass> DETACHABLES = classesWhere(LiftClass::isDetachable);

    /** Every line any machine's sheet can carry, in the order the walk takes them. */
    public static final List<CheckItem> STANDARD_SHEET = List.of(
            new CheckItem("A1", "service brake set and released", true, ALL_CLASSES),
            new CheckItem("A2", "emergency brake tested", true, AERIAL),
            new CheckItem("A3", "backstop free", false, ALL_CLASSES),
            new CheckItem("B1", "line ridden top to bottom", true, ALL_CLASSES),
            new CheckItem("B2", "rope seated in every sheave train", true, ALL_CLASSES),
            new CheckItem("B3", "tension carriage free and within travel", true, ALL_CLASSES),
            new CheckItem("C1", "grip conveyor and timing checked", true, DETACHABLES),
            new CheckItem("C2", "terminal gates and photo eyes", true, DETACHABLES),
            new CheckItem("D1", "stop switches at both terminals", true, ALL_CLASSES),
            new CheckItem("D2", "telephone or radio to both terminals", true, ALL_CLASSES),
            new CheckItem("D3", "evacuation equipment on site and sealed", true, AERIAL),
            new CheckItem("E1", "loading ramp groomed", false, ALL_CLASSES),
            new CheckItem("E2", "signage in place", false, ALL_CLASSES),
            new CheckItem("E3", "first aid kit stocked", false, ALL_CLASSES));

    private static final Map<BrakeKind, String> BRAKE_LINES =
            Map.of(BrakeKind.SERVICE, "A1", BrakeKind.EMERGENCY, "A2", BrakeKind.BACKSTOP, "A3");

    /**
     * A walked sheet.
     *
     * @throws OperationsError when the sheet lacks a mechanic or an operator, walks a line twice, or
     *     carries a line that is not on this machine's sheet
     */
    public PreOpSheet {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(mechanic, "mechanic");
        Objects.requireNonNull(operator, "operator");
        results = List.copyOf(results);
        if (mechanic.strip().isEmpty() || operator.strip().isEmpty()) {
            throw new OperationsError("a sheet carries a mechanic and an operator");
        }
        Set<String> codes = new HashSet<>();
        for (CheckResult result : results) {
            codes.add(result.item().code());
        }
        if (codes.size() != results.size()) {
            throw new OperationsError("an item is walked once");
        }
        for (CheckResult result : results) {
            if (!result.item().appliesTo(lift)) {
                throw new OperationsError(result.item().code() + " is not on a " + lift.code() + " sheet");
            }
        }
    }

    /**
     * The brake lines of the sheet, which the mechanic signs separately.
     *
     * @return the service brake, emergency brake and backstop lines, in sheet order
     */
    public static List<CheckItem> brakeItems() {
        Collection<String> codes = BRAKE_LINES.values();
        return STANDARD_SHEET.stream()
                .filter(item -> codes.contains(item.code()))
                .toList();
    }

    /**
     * The sheet this machine actually gets, which is not the whole list.
     *
     * @return the standard lines that apply to this machine, in sheet order
     */
    public List<CheckItem> items() {
        return STANDARD_SHEET.stream().filter(item -> item.appliesTo(lift)).toList();
    }

    /**
     * What was recorded against one line.
     *
     * @param code the line's code
     * @return the result, or empty when that line was not walked
     */
    public Optional<CheckResult> resultFor(String code) {
        return results.stream()
                .filter(result -> result.item().code().equals(code))
                .findFirst();
    }

    /**
     * The lines of this machine's sheet nobody walked.
     *
     * @return the missing lines, in sheet order
     */
    public List<CheckItem> missing() {
        Set<String> walked =
                results.stream().map(result -> result.item().code()).collect(Collectors.toSet());
        return items().stream().filter(item -> !walked.contains(item.code())).toList();
    }

    /**
     * The lines that failed, critical or not.
     *
     * @return the failures, in the order recorded
     */
    public List<CheckResult> failures() {
        return withOutcome(CheckOutcome.FAIL);
    }

    /**
     * The lines put off to the work order book.
     *
     * @return the deferrals, in the order recorded
     */
    public List<CheckResult> deferrals() {
        return withOutcome(CheckOutcome.DEFERRED);
    }

    /**
     * The failures that stop the machine.
     *
     * @return the critical failures, in the order recorded
     */
    public List<CheckResult> criticalFailures() {
        return failures().stream().filter(result -> result.item().critical()).toList();
    }

    /**
     * Whether every line of this machine's sheet was walked.
     *
     * @return true when nothing is missing
     */
    public boolean isComplete() {
        return missing().isEmpty();
    }

    /**
     * Whether the sheet lets the machine open.
     *
     * @return true when it is complete and nothing critical failed
     */
    public boolean mayOpen() {
        return isComplete() && criticalFailures().isEmpty();
    }

    /**
     * What stands between this sheet and the first chair.
     *
     * @return such as {@code B2 not walked} for each missing line, then {@code B2 failed} for each
     *     critical failure
     */
    public List<String> blockers() {
        List<String> reasons = new ArrayList<>();
        for (CheckItem item : missing()) {
            reasons.add(item.code() + " not walked");
        }
        for (CheckResult result : criticalFailures()) {
            reasons.add(result.item().code() + " failed");
        }
        return List.copyOf(reasons);
    }

    /** Such as {@code JP 2025-01-15, clear to open}. */
    @Override
    public String toString() {
        String state = mayOpen() ? "clear to open" : "not clear";
        return lift.code() + " " + day + ", " + state;
    }

    private List<CheckResult> withOutcome(CheckOutcome outcome) {
        return results.stream().filter(result -> result.outcome() == outcome).toList();
    }

    private static Set<LiftClass> classesWhere(Predicate<LiftClass> test) {
        Set<LiftClass> found = EnumSet.noneOf(LiftClass.class);
        for (LiftClass liftClass : LiftClass.values()) {
            if (test.test(liftClass)) {
                found.add(liftClass);
            }
        }
        return found;
    }
}
