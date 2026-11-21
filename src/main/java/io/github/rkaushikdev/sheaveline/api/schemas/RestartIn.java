package io.github.rkaushikdev.sheaveline.api.schemas;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Running a stopped machine again. The stop is named by the moment it was called, not by an id,
 * because the operator has the time written on the clipboard in front of them and does not have an
 * id.
 *
 * @param stopped when the stop was called
 * @param restarted when it ran again
 * @param restartedBy who ran it
 * @param lineChecked whether the line has been ridden since the stop, false when not sent
 */
public record RestartIn(
        @NotNull LocalDateTime stopped,
        @NotNull LocalDateTime restarted,
        @NotNull @Size(min = 1) String restartedBy,
        boolean lineChecked) {}
