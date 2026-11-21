package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing sent to the board, under one number.
 *
 * <p>A filing records whether it was late, and lateness is measured against the incident rather
 * than against the filing clerk's day. A deropement at half past eleven on a Saturday is due at half
 * past one on the Saturday, and Monday morning is not an answer.
 *
 * @param number the number the board issued it under
 * @param kind what it is about
 * @param lift the machine
 * @param filed when it went out
 * @param about what it says
 * @param filedBy who sent it
 * @param acknowledged the day the board wrote back, once it has
 */
public record Filing(
        FilingNumber number,
        FilingKind kind,
        LiftCode lift,
        LocalDateTime filed,
        String about,
        String filedBy,
        Optional<LocalDate> acknowledged) {

    /**
     * A filing.
     *
     * @throws BoardError when it says nothing, is unsigned, or was acknowledged before it was sent
     */
    public Filing {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(filed, "filed");
        Objects.requireNonNull(about, "about");
        Objects.requireNonNull(filedBy, "filedBy");
        Objects.requireNonNull(acknowledged, "acknowledged");
        if (about.strip().isEmpty()) {
            throw new BoardError(number + " says nothing");
        }
        if (filedBy.strip().isEmpty()) {
            throw new BoardError(number + " is signed by whoever sent it");
        }
        if (acknowledged.isPresent() && acknowledged.get().isBefore(filed.toLocalDate())) {
            throw new BoardError(number + " was acknowledged before it was sent");
        }
    }

    /**
     * A filing the board has not written back on.
     *
     * @param number the number the board issued it under
     * @param kind what it is about
     * @param lift the machine
     * @param filed when it went out
     * @param about what it says
     * @param filedBy who sent it
     */
    public Filing(
            FilingNumber number, FilingKind kind, LiftCode lift, LocalDateTime filed, String about, String filedBy) {
        this(number, kind, lift, filed, about, filedBy, Optional.empty());
    }

    /**
     * Whether the board has written back.
     *
     * @return true once there is an acknowledgement
     */
    public boolean isAcknowledged() {
        return acknowledged.isPresent();
    }

    /**
     * Whether the filing is still waiting on the board.
     *
     * @return true for a kind the board writes back on that it has not written back on yet
     */
    public boolean isOutstanding() {
        return kind.needsAnAcknowledgement() && !isAcknowledged();
    }

    /**
     * The same filing, acknowledged.
     *
     * @param on the day the board wrote back
     * @return a new filing; this one is unchanged
     * @throws BoardError when it is already acknowledged, or the day is before it was sent
     */
    public Filing acknowledge(LocalDate on) {
        if (isAcknowledged()) {
            throw new BoardError(number + " is already acknowledged");
        }
        return new Filing(number, kind, lift, filed, about, filedBy, Optional.of(on));
    }

    /**
     * How far past the incident's deadline it went out, which the board totals up.
     *
     * @param incident the incident it answers
     * @return the whole seconds past the due time, zero when it was on time
     */
    public Duration lateness(Incident incident) {
        LocalDateTime due = incident.dueBy();
        if (!filed.isAfter(due)) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(ChronoUnit.SECONDS.between(due, filed));
    }

    /**
     * Whether it went out after the incident's deadline.
     *
     * @param incident the incident it answers
     * @return true when there is any lateness
     */
    public boolean wasLate(Incident incident) {
        return !lateness(incident).isZero();
    }

    /** Such as {@code UT-2025-0001 incident, JP, 2025-01-15}. */
    @Override
    public String toString() {
        return number + " " + kind.value() + ", " + lift + ", " + filed.toLocalDate();
    }
}
