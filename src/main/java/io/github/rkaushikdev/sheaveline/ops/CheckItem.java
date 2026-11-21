package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One line of the pre opening sheet, and which machines it belongs on.
 *
 * <p>A critical line that fails stops the machine. Anything else can be deferred to the work order
 * list with a note, and the lift opens.
 *
 * @param code the line's code on the sheet, such as {@code B1}
 * @param description what gets checked
 * @param critical whether a failure stops the machine
 * @param classes the classes of machine whose sheet carries this line
 */
public record CheckItem(String code, String description, boolean critical, Set<LiftClass> classes) {

    /** A line, with its classes held in declaration order. */
    public CheckItem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Set<LiftClass> copy = EnumSet.noneOf(LiftClass.class);
        copy.addAll(classes);
        classes = Collections.unmodifiableSet(copy);
    }

    /**
     * A line that belongs on every machine's sheet.
     *
     * @param code the line's code on the sheet
     * @param description what gets checked
     * @param critical whether a failure stops the machine
     */
    public CheckItem(String code, String description, boolean critical) {
        this(code, description, critical, EnumSet.allOf(LiftClass.class));
    }

    /**
     * Whether this line is on that machine's sheet.
     *
     * @param lift the machine
     * @return true when its class is one of this line's classes
     */
    public boolean appliesTo(Lift lift) {
        return classes.contains(lift.liftClass());
    }

    /** Such as {@code B1 line ridden top to bottom}. */
    @Override
    public String toString() {
        return code + " " + description;
    }
}
