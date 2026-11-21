package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One job that has to be done to a machine on an interval.
 *
 * @param code how the crew refers to it on the radio, such as {@code A1}
 * @param description what the job is, written as an instruction
 * @param interval how often it comes round
 * @param critical whether a machine with this job overdue may not carry the public
 * @param classes the classes of machine that carry the job
 */
public record Requirement(
        String code, String description, Interval interval, boolean critical, Set<LiftClass> classes) {

    /** Every class with carriers in the air. */
    public static final Set<LiftClass> AERIAL = classesWhere(LiftClass::isAerial);

    /** Every class whose carriers come off the rope in the terminals. */
    public static final Set<LiftClass> DETACHABLES = classesWhere(LiftClass::isDetachable);

    /**
     * A job.
     *
     * @throws UpkeepError when the code or the description is blank
     */
    public Requirement {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(interval, "interval");
        classes = copyOf(Objects.requireNonNull(classes, "classes"));
        if (code.strip().isEmpty()) {
            throw new UpkeepError("a requirement has a code");
        }
        if (description.strip().isEmpty()) {
            throw new UpkeepError(code + " has no description");
        }
    }

    /**
     * A critical job on every class of machine.
     *
     * @param code how the crew refers to it
     * @param description what the job is
     * @param interval how often it comes round
     */
    public Requirement(String code, String description, Interval interval) {
        this(code, description, interval, true, EnumSet.allOf(LiftClass.class));
    }

    /**
     * A critical job on some classes of machine.
     *
     * @param code how the crew refers to it
     * @param description what the job is
     * @param interval how often it comes round
     * @param classes the classes that carry it
     */
    public Requirement(String code, String description, Interval interval, Set<LiftClass> classes) {
        this(code, description, interval, true, classes);
    }

    /**
     * Whether a machine of that class carries this job.
     *
     * @param liftClass the class
     * @return true when the class is one of this job's
     */
    public boolean appliesTo(LiftClass liftClass) {
        return classes.contains(liftClass);
    }

    /** Such as {@code A1 annual inspection, every 365 days}. */
    @Override
    public String toString() {
        return code + " " + description + ", " + interval;
    }

    private static Set<LiftClass> classesWhere(Predicate<LiftClass> test) {
        return copyOf(Arrays.stream(LiftClass.values()).filter(test).toList());
    }

    private static Set<LiftClass> copyOf(Collection<LiftClass> classes) {
        Set<LiftClass> copy = EnumSet.noneOf(LiftClass.class);
        copy.addAll(classes);
        return Collections.unmodifiableSet(copy);
    }
}
