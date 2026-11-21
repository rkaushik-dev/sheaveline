package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * An incident, with its deadline and the clock that set it.
 *
 * <p>The question everybody asks about an incident is not what happened, it is how long there is.
 * Whichever of the two clocks is shorter is named in the reply so nobody has to work it out from the
 * kind and the injury for themselves.
 *
 * @param id its id in the register
 * @param lift the machine
 * @param at when it happened
 * @param kind what the machine did
 * @param injury what happened to the people
 * @param people how many people were involved
 * @param narrative what happened, in the reporter's words
 * @param reportedBy who reported it
 * @param deadline how long the board allows to hear about it
 * @param deadlineDriver which clock set the deadline: the kind, the injury, or both
 * @param dueBy when the board must have heard
 * @param holdsTheMachine whether the machine stays stopped until it has been looked at
 * @param filing the number it was filed under, once it has been
 */
public record IncidentOut(
        int id,
        @LiftCodeText LiftCode lift,
        LocalDateTime at,
        String kind,
        String injury,
        int people,
        String narrative,
        String reportedBy,
        @DurationText Duration deadline,
        String deadlineDriver,
        LocalDateTime dueBy,
        boolean holdsTheMachine,
        @FilingNumberText Optional<FilingNumber> filing) {

    /**
     * An incident not yet filed, as the tablet shows it.
     *
     * @param id its id in the register
     * @param incident the incident
     * @return its shape
     */
    public static IncidentOut of(int id, Incident incident) {
        return of(id, incident, Optional.empty());
    }

    /**
     * An incident as the tablet shows it.
     *
     * @param id its id in the register
     * @param incident the incident
     * @param filing the number it was filed under, once it has been
     * @return its shape
     */
    public static IncidentOut of(int id, Incident incident, Optional<FilingNumber> filing) {
        return new IncidentOut(
                id,
                incident.lift(),
                incident.at(),
                incident.kind().value(),
                incident.injury().value(),
                incident.people(),
                incident.narrative(),
                incident.reportedBy(),
                incident.deadline(),
                incident.deadlineDriver(),
                incident.dueBy(),
                incident.holdsTheMachine(),
                filing);
    }
}
