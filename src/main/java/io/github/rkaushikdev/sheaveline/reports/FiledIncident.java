package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One incident and what became of it.
 *
 * @param incident the incident
 * @param filing the number the board filed it under, once it has been filed
 * @param filedOn the day the filing went in, when that is known
 */
public record FiledIncident(Incident incident, Optional<FilingNumber> filing, Optional<LocalDate> filedOn) {

    /** A filed incident. */
    public FiledIncident {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(filing, "filing");
        Objects.requireNonNull(filedOn, "filedOn");
    }

    /**
     * An incident and its filing, with no record of the day it went in.
     *
     * @param incident the incident
     * @param filing the filing number, when it has been filed
     */
    public FiledIncident(Incident incident, Optional<FilingNumber> filing) {
        this(incident, filing, Optional.empty());
    }

    /**
     * Whether the board has a number for it.
     *
     * @return true once it is filed
     */
    public boolean isFiled() {
        return filing.isPresent();
    }

    /**
     * Whether the filing went in after the day the incident was due by.
     *
     * @return true when it went in late; false when it went in on time or nobody knows when it went
     *     in
     */
    public boolean wasLate() {
        return filedOn.isPresent() && filedOn.get().isAfter(incident.dueBy().toLocalDate());
    }
}
