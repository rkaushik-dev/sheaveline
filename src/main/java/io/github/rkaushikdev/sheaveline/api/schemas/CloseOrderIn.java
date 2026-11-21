package io.github.rkaushikdev.sheaveline.api.schemas;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Closing a work order.
 *
 * @param closed the day the work was finished
 * @param closedBy who finished it
 */
public record CloseOrderIn(@NotNull LocalDate closed, @NotNull @Size(min = 1) String closedBy) {}
