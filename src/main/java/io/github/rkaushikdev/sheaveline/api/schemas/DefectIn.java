package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.upkeep.Defect;
import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Something a walk found.
 *
 * @param where where on the machine
 * @param level how bad it is
 * @param detail what is wrong
 */
public record DefectIn(
        @NotNull @Size(min = 1) String where, @NotNull DefectLevel level, @NotNull @Size(min = 1) String detail) {

    /**
     * The defect in the domain's own terms.
     *
     * @param raised the day the walk found it
     * @return the defect
     * @throws io.github.rkaushikdev.sheaveline.upkeep.UpkeepError when it does not hold together
     */
    public Defect toDomain(LocalDate raised) {
        return new Defect(where, level, detail, raised);
    }
}
