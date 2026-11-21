package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.board.Filing;
import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;

/**
 * A filing with the board, and whether it went out in time.
 *
 * @param number the number it went under
 * @param kind what it is about
 * @param lift the machine
 * @param filed when it went out
 * @param about what it says
 * @param filedBy who sent it
 * @param late whether it went out after the incident's deadline
 * @param lateness by how much
 */
public record FilingOut(
        @FilingNumberText FilingNumber number,
        String kind,
        @LiftCodeText LiftCode lift,
        LocalDateTime filed,
        String about,
        String filedBy,
        boolean late,
        @DurationText Duration lateness) {

    /**
     * A filing as the tablet shows it.
     *
     * @param filing the filing
     * @param incident the incident it is about
     * @return its shape
     */
    public static FilingOut of(Filing filing, Incident incident) {
        return new FilingOut(
                filing.number(),
                filing.kind().value(),
                filing.lift(),
                filing.filed(),
                filing.about(),
                filing.filedBy(),
                filing.wasLate(incident),
                filing.lateness(incident));
    }
}
