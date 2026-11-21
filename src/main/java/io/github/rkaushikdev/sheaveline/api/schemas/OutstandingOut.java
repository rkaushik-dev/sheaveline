package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;

/**
 * One line of the Monday morning list: an incident nobody has filed yet.
 *
 * @param id its id in the register
 * @param lift the machine
 * @param at when it happened
 * @param kind what the machine did
 * @param dueBy when the board must have heard
 * @param overdue whether that has passed
 * @param outstandingFor how long it has been waiting
 */
public record OutstandingOut(
        int id,
        @LiftCodeText LiftCode lift,
        LocalDateTime at,
        String kind,
        LocalDateTime dueBy,
        boolean overdue,
        @DurationText Duration outstandingFor) {}
