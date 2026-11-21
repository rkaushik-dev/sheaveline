package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One incident and the filing that answered for it, if there is one.
 *
 * @param incident the incident
 * @param filing the incident filing sent about it, once one has been
 */
public record ReportedIncident(Incident incident, Optional<Filing> filing) {

    /**
     * An incident and its filing.
     *
     * @throws BoardError when the filing is against another machine, or is not an incident filing
     */
    public ReportedIncident {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(filing, "filing");
        if (filing.isPresent() && !filing.get().lift().equals(incident.lift())) {
            throw new BoardError("that filing is against another machine");
        }
        if (filing.isPresent() && filing.get().kind() != FilingKind.INCIDENT) {
            throw new BoardError("an incident is answered by an incident filing");
        }
    }

    /**
     * An incident nothing has been filed about yet.
     *
     * @param incident the incident
     */
    public ReportedIncident(Incident incident) {
        this(incident, Optional.empty());
    }

    /**
     * An incident and the filing that answered it.
     *
     * @param incident the incident
     * @param filing the incident filing
     */
    public ReportedIncident(Incident incident, Filing filing) {
        this(incident, Optional.of(filing));
    }

    /**
     * Whether anything has been filed.
     *
     * @return true once there is a filing
     */
    public boolean isFiled() {
        return filing.isPresent();
    }

    /**
     * Whether the board heard, or will hear, too late.
     *
     * @param now the moment asked about, which only counts while nothing is filed
     * @return true when unfiled past the due time, or filed late
     */
    public boolean isOverdue(LocalDateTime now) {
        return filing.map(sent -> sent.wasLate(incident)).orElseGet(() -> now.isAfter(incident.dueBy()));
    }

    /**
     * How late the filing went out.
     *
     * @return the time past the due time, zero when it was on time
     * @throws BoardError when nothing has been filed
     */
    public Duration lateness() {
        return filing.map(sent -> sent.lateness(incident))
                .orElseThrow(() -> new BoardError("nothing was filed, so there is no lateness yet"));
    }

    /**
     * How long this has been sitting unfiled, which is the Monday morning number.
     *
     * @param now the moment asked about
     * @return the whole seconds since the incident, zero once it is filed
     * @throws BoardError when it is unfiled and the moment is before the incident
     */
    public Duration outstandingFor(LocalDateTime now) {
        if (isFiled()) {
            return Duration.ZERO;
        }
        if (now.isBefore(incident.at())) {
            throw new BoardError("that moment is before the incident");
        }
        return Duration.ofSeconds(ChronoUnit.SECONDS.between(incident.at(), now));
    }

    /** The incident's line, then {@code not filed} or {@code filed as} its number. */
    @Override
    public String toString() {
        return filing.map(sent -> incident + ", filed as " + sent.number()).orElseGet(() -> incident + ", not filed");
    }
}
