package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.InspectionRecord;
import java.time.LocalDate;
import java.util.List;

/**
 * One job signed off.
 *
 * @param lift the machine
 * @param code the job in the manual
 * @param done the day it was done
 * @param by who did it
 * @param clean whether it found nothing
 * @param defects what it found
 */
public record InspectionOut(
        @LiftCodeText LiftCode lift, String code, LocalDate done, String by, boolean clean, List<DefectOut> defects) {

    /** A record's shape. */
    public InspectionOut {
        defects = List.copyOf(defects);
    }

    /**
     * A record as the tablet shows it.
     *
     * @param record the record
     * @return its shape
     */
    public static InspectionOut of(InspectionRecord record) {
        return new InspectionOut(
                record.lift(),
                record.code(),
                record.done(),
                record.by(),
                record.wasClean(),
                record.defects().stream().map(DefectOut::of).toList());
    }
}
