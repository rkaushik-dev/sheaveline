package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Paged;
import io.github.rkaushikdev.sheaveline.api.Paging;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.schemas.FilingIn;
import io.github.rkaushikdev.sheaveline.api.schemas.FilingOut;
import io.github.rkaushikdev.sheaveline.api.schemas.IncidentIn;
import io.github.rkaushikdev.sheaveline.api.schemas.IncidentOut;
import io.github.rkaushikdev.sheaveline.api.schemas.OutstandingOut;
import io.github.rkaushikdev.sheaveline.board.Filing;
import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.persistence.IncidentRepository;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidents and what has been sent about them.
 *
 * <p>The route worth reading is the outstanding list. It is the one the mountain manager opens on a
 * Monday, and it answers in the order the deadlines fall rather than the order things happened,
 * because the deropement at four on Saturday is more urgent than the twisted ankle from Thursday
 * whatever the timestamps say.
 */
@RestController
@Tag(name = "board")
public class BoardController {

    private final Settings settings;

    /**
     * The board routes for one running copy.
     *
     * @param settings its settings
     */
    public BoardController(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Record an incident, and hear at once how long there is to tell the board.
     *
     * @param code the machine
     * @param body what happened
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the incident with its deadline
     */
    @PostMapping("/lifts/{code}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.OPERATOR)
    public IncidentOut recordIncident(
            @PathVariable String code, @Valid @RequestBody IncidentIn body, UnitOfWork work, Lookup look) {
        LiftCode lift = look.liftCode(code);
        Incident incident = body.toDomain(lift);
        int rowId = work.incidents().record(incident);
        work.commit();
        return IncidentOut.of(rowId, incident);
    }

    /**
     * The incidents on one machine, in the order they happened. Paged, because the register is
     * never cleared down.
     *
     * @param code the machine
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page
     */
    @GetMapping("/lifts/{code}/incidents")
    @Requires(Role.READER)
    public Paged<IncidentOut> listForLift(
            @PathVariable String code, UnitOfWork work, Lookup look, @ParameterObject Paging paging) {
        LiftCode lift = look.liftCode(code);
        return Paged.of(
                work.incidents().numberedForLift(lift).stream()
                        .map(one -> IncidentOut.of(one.id(), one.incident(), one.filingNumber()))
                        .toList(),
                paging);
    }

    /**
     * Every incident not yet filed, the soonest deadline first.
     *
     * @param now the moment to measure from; the start of the service's today when not given
     * @param work the request's unit of work
     * @return the list
     */
    @GetMapping("/incidents/outstanding")
    @Requires(Role.READER)
    public List<OutstandingOut> outstanding(@RequestParam Optional<LocalDateTime> now, UnitOfWork work) {
        LocalDateTime moment = now.orElseGet(() -> settings.today().atStartOfDay());
        List<OutstandingOut> lines = new ArrayList<>();
        for (IncidentRepository.Numbered one : work.incidents().numberedUnfiled()) {
            Incident incident = one.incident();
            Duration waited = moment.isBefore(incident.at())
                    ? Duration.ZERO
                    : Duration.ofSeconds(ChronoUnit.SECONDS.between(incident.at(), moment));
            lines.add(new OutstandingOut(
                    one.id(),
                    incident.lift(),
                    incident.at(),
                    incident.kind().value(),
                    incident.dueBy(),
                    moment.isAfter(incident.dueBy()),
                    waited));
        }
        lines.sort(Comparator.comparing(OutstandingOut::dueBy));
        return lines;
    }

    /**
     * One incident.
     *
     * @param incidentId its id
     * @param look the request's lookup
     * @return the incident
     */
    @GetMapping("/incidents/{incident_id}")
    @Requires(Role.READER)
    public IncidentOut readIncident(@PathVariable("incident_id") int incidentId, Lookup look) {
        IncidentRepository.Numbered found = look.incident(incidentId);
        return IncidentOut.of(incidentId, found.incident(), found.filingNumber());
    }

    /**
     * File an incident with the board. It takes the next number in the year's sequence, and goes
     * once.
     *
     * @param incidentId the incident
     * @param body when it went out and who sent it
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the filing, and whether it was late
     */
    @PostMapping("/incidents/{incident_id}/filing")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.MANAGER)
    public FilingOut fileIncident(
            @PathVariable("incident_id") int incidentId,
            @Valid @RequestBody FilingIn body,
            UnitOfWork work,
            Lookup look) {
        IncidentRepository.Numbered found = look.incident(incidentId);
        if (found.filingNumber().isPresent()) {
            throw new StorageError("incident " + incidentId + " was filed as "
                    + found.filingNumber().get());
        }
        Filing filing = work.incidents().file(incidentId, FilingKind.INCIDENT, body.filed(), body.filedBy());
        work.commit();
        return FilingOut.of(filing, found.incident());
    }
}
