package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.ops.StopKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * What an operator sends when they push the button: a time and a kind and nothing else that
 * matters.
 *
 * <p>Everything the department cares about after that is worked out: whether it is reportable,
 * whether the line has to be walked before it runs again, and whether it counts against the
 * machine. A client that could send those would eventually send the convenient answer.
 *
 * @param kind why it stopped
 * @param stopped when
 * @param stoppedBy who stopped it
 * @param note anything worth saying, nothing when not sent
 */
public record StopIn(
        @NotNull StopKind kind, @NotNull LocalDateTime stopped, @NotNull @Size(min = 1) String stoppedBy, String note) {

    /** A stop, with no note when none was sent. */
    public StopIn {
        note = note == null ? "" : note;
    }
}
