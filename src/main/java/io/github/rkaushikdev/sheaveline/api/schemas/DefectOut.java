package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.upkeep.Defect;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Something a walk found, and whether it has been put right.
 *
 * @param where where on the machine
 * @param level how bad it is
 * @param detail what is wrong
 * @param raised the day it was found
 * @param cleared the day it was put right, once it has been
 * @param open whether it is still there
 */
public record DefectOut(
        String where, String level, String detail, LocalDate raised, Optional<LocalDate> cleared, boolean open) {

    /**
     * A defect as the tablet shows it.
     *
     * @param defect the defect
     * @return its shape
     */
    public static DefectOut of(Defect defect) {
        return new DefectOut(
                defect.where(),
                defect.level().value(),
                defect.detail(),
                defect.raised(),
                defect.cleared(),
                defect.isOpen());
    }
}
