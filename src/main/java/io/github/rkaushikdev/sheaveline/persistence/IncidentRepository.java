package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.board.Filing;
import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.persistence.models.FilingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.IncidentRow;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Incidents, by the machine and the moment, and the board filings made for them. */
public final class IncidentRepository extends Repository {

    /** The state whose board the incidents are filed with. */
    public static final String STATE = "UT";

    /**
     * An incident with the id the routes address it by, and the board number it was filed under.
     *
     * @param id the incident's id
     * @param incident the incident
     * @param filingNumber the board number, or empty while it is unfiled
     */
    public record Numbered(int id, Incident incident, Optional<FilingNumber> filingNumber) {

        /** An incident and its numbers. */
        public Numbered {
            Objects.requireNonNull(incident, "incident");
            Objects.requireNonNull(filingNumber, "filingNumber");
        }
    }

    /**
     * The incidents in a session.
     *
     * @param session the session
     */
    public IncidentRepository(Session session) {
        super(session);
    }

    /**
     * Write an incident.
     *
     * @param incident the incident
     */
    public void add(Incident incident) {
        session().persist(toRow(incident));
    }

    /**
     * Write an incident and hand back the id the routes address it by.
     *
     * @param incident the incident
     * @return its id
     */
    public int record(Incident incident) {
        IncidentRow row = toRow(incident);
        session().persist(row);
        session().flush();
        return row.getId();
    }

    /**
     * An incident by its id.
     *
     * @param incidentId the id
     * @return the incident, its id and its board number
     * @throws StorageError when there is no such incident
     */
    public Numbered get(int incidentId) {
        return numbered(row(incidentId));
    }

    /**
     * One machine's incidents, oldest first, with their ids and board numbers.
     *
     * @param code the machine
     * @return the incidents
     */
    public List<Numbered> numberedForLift(LiftCode code) {
        return rowsForLift(code).stream().map(IncidentRepository::numbered).toList();
    }

    /**
     * The incidents not yet filed with the board, oldest first, with their ids.
     *
     * @return the incidents
     */
    public List<Numbered> numberedUnfiled() {
        return many(query("select i from IncidentRow i where i.filingNumber is null order by i.at", IncidentRow.class))
                .stream()
                .map(row -> new Numbered(row.getId(), toDomain(row), Optional.empty()))
                .toList();
    }

    /**
     * Take the next board number, write the filing and hang it on the incident.
     *
     * @param incidentId the incident
     * @param kind what is being filed
     * @param filed when it was filed
     * @param filedBy who filed it
     * @return the filing
     * @throws StorageError when there is no such incident, or it is already filed
     */
    public Filing file(int incidentId, FilingKind kind, LocalDateTime filed, String filedBy) {
        IncidentRow row = row(incidentId);
        if (row.getFilingNumber().isPresent()) {
            throw new StorageError("incident " + incidentId + " is already filed");
        }
        FilingNumber number = Numbering.takeNumber(session(), STATE, filed.getYear());
        session().persist(new FilingRow(number, kind, row.getLiftCode(), filed, row.getNarrative(), filedBy));
        session().flush();
        row.setFilingNumber(number);
        session().flush();
        return new Filing(number, kind, row.getLiftCode(), filed, row.getNarrative(), filedBy);
    }

    /**
     * One machine's incidents, oldest first.
     *
     * @param code the machine
     * @return the incidents
     */
    public List<Incident> forLift(LiftCode code) {
        return rowsForLift(code).stream().map(IncidentRepository::toDomain).toList();
    }

    /**
     * The incidents not yet filed with the board.
     *
     * @return the incidents
     */
    public List<Incident> unfiled() {
        return many(query("select i from IncidentRow i where i.filingNumber is null", IncidentRow.class)).stream()
                .map(IncidentRepository::toDomain)
                .toList();
    }

    /**
     * The incidents that happened on the days from one to another, both included, oldest first.
     *
     * @param start the first day
     * @param end the last day
     * @return the incidents
     */
    public List<Incident> between(LocalDate start, LocalDate end) {
        return many(query("select i from IncidentRow i order by i.at", IncidentRow.class)).stream()
                .filter(row -> {
                    LocalDate day = row.getAt().toLocalDate();
                    return !day.isBefore(start) && !day.isAfter(end);
                })
                .map(IncidentRepository::toDomain)
                .toList();
    }

    private IncidentRow row(int incidentId) {
        return fetch(IncidentRow.class, incidentId, "there is no incident " + incidentId);
    }

    private List<IncidentRow> rowsForLift(LiftCode code) {
        return many(query("select i from IncidentRow i where i.liftCode = :code order by i.at", IncidentRow.class)
                .setParameter("code", code));
    }

    private static IncidentRow toRow(Incident incident) {
        return new IncidentRow(
                incident.lift(),
                incident.at(),
                incident.kind(),
                incident.injury(),
                incident.people(),
                incident.narrative(),
                incident.reportedBy());
    }

    private static Numbered numbered(IncidentRow row) {
        return new Numbered(row.getId(), toDomain(row), row.getFilingNumber());
    }

    private static Incident toDomain(IncidentRow row) {
        return new Incident(
                row.getLiftCode(),
                row.getAt(),
                row.getKind(),
                row.getInjury(),
                row.getPeople(),
                row.getNarrative(),
                row.getReportedBy());
    }
}
