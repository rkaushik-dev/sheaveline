package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One machine cleared to carry down, for a stated stretch of the season, for a reason somebody
 * wrote down.
 *
 * @param lift the machine
 * @param reason why riders come down on it
 * @param granted the first day it holds
 * @param grantedBy who gave it
 * @param expires the last day it holds
 * @param escort who rides with a rider that needs an escort; empty for any other reason
 */
public record DownhillAuthorisation(
        LiftCode lift,
        DownhillReason reason,
        LocalDate granted,
        String grantedBy,
        LocalDate expires,
        Optional<String> escort) {

    /**
     * A signed authorisation that runs forwards, escorted exactly when its reason needs it.
     *
     * @throws OperationsError when it is unsigned, expires before it is given, lacks an escort its
     *     reason needs, or names an escort its reason does not carry
     */
    public DownhillAuthorisation {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(granted, "granted");
        Objects.requireNonNull(grantedBy, "grantedBy");
        Objects.requireNonNull(expires, "expires");
        Objects.requireNonNull(escort, "escort");
        if (grantedBy.strip().isEmpty()) {
            throw new OperationsError("an authorisation is signed by whoever gave it");
        }
        if (expires.isBefore(granted)) {
            throw new OperationsError("an authorisation cannot expire before it is given");
        }
        if (reason.needsAnEscort() && escort.map(String::strip).orElse("").isEmpty()) {
            throw new OperationsError("a " + reason.value() + " ride is escorted");
        }
        if (!reason.needsAnEscort() && !escort.orElse("").isEmpty()) {
            throw new OperationsError("a " + reason.value() + " ride does not carry an escort");
        }
    }

    /**
     * An authorisation for a reason that rides without an escort.
     *
     * @param lift the machine
     * @param reason why riders come down on it
     * @param granted the first day it holds
     * @param grantedBy who gave it
     * @param expires the last day it holds
     */
    public DownhillAuthorisation(
            LiftCode lift, DownhillReason reason, LocalDate granted, String grantedBy, LocalDate expires) {
        this(lift, reason, granted, grantedBy, expires, Optional.empty());
    }

    /**
     * Whether the authorisation holds on a day.
     *
     * @param on the day
     * @return true from the day it was given to the day it expires, both included
     */
    public boolean isCurrent(LocalDate on) {
        return !on.isBefore(granted) && !on.isAfter(expires);
    }

    /**
     * Days from one day to the expiry.
     *
     * @param on the day
     * @return the days left, negative once it has lapsed
     */
    public long daysLeft(LocalDate on) {
        return ChronoUnit.DAYS.between(on, expires);
    }

    /** Such as {@code JP downhill for sightseeing, to 2025-04-20}. */
    @Override
    public String toString() {
        return lift + " downhill for " + reason.value() + ", to " + expires;
    }
}
