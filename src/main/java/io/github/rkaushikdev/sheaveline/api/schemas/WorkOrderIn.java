package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Raise an order off a defect that is already in the book.
 *
 * <p>A work order is raised from a defect rather than typed, so the body names the defect and
 * nothing else. The priority is not a field anybody can fill in, which is the whole point: an order
 * that came off a serious defect is immediate whatever the person raising it thought.
 *
 * @param where where on the machine
 * @param detail what is wrong
 * @param level how bad it is
 * @param raised the day it was raised
 * @param raisedBy who raised it
 */
public record WorkOrderIn(
        @NotNull @Size(min = 1) String where,
        @NotNull @Size(min = 1) String detail,
        @NotNull DefectLevel level,
        @NotNull LocalDate raised,
        @NotNull @Size(min = 1) String raisedBy) {}
