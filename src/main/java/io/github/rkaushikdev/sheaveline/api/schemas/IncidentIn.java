package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * What an operator types in after the machine has stopped.
 *
 * <p>Enumerations travel as the sentence a person reads rather than as the name the code uses, in
 * both directions, because these bodies are read by hand as often as they are parsed.
 *
 * @param at when it happened
 * @param kind what the machine did
 * @param injury what happened to the people, none when not sent
 * @param people how many people were involved, none when not sent
 * @param narrative what happened, in the reporter's words
 * @param reportedBy who reported it
 */
public record IncidentIn(
        @NotNull LocalDateTime at,
        @NotNull IncidentKind kind,
        InjuryLevel injury,
        @Min(0) int people,
        @NotNull @Size(min = 1) String narrative,
        @NotNull @Size(min = 1) String reportedBy) {

    /** An incident, nobody hurt when the injury was not sent. */
    public IncidentIn {
        injury = injury == null ? InjuryLevel.NONE : injury;
    }

    /**
     * The incident in the domain's own terms.
     *
     * @param lift the machine it happened on
     * @return the incident
     * @throws io.github.rkaushikdev.sheaveline.board.BoardError when it does not hold together
     */
    public Incident toDomain(LiftCode lift) {
        return new Incident(lift, at, kind, injury, people, narrative, reportedBy);
    }
}
