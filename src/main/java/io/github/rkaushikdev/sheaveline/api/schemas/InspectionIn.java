package io.github.rkaushikdev.sheaveline.api.schemas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What a mechanic signs off after walking one job.
 *
 * @param code the job in the manual, such as {@code W1}
 * @param done the day it was done
 * @param by who did it
 * @param hours the hour meter at the time, as the decimal it shows, when it was read
 * @param cycles the cycle counter at the time, when it was read
 * @param defects what it found, nothing when not sent
 */
public record InspectionIn(
        @NotNull @Size(min = 1, max = 4) String code,
        @NotNull LocalDate done,
        @NotNull @Size(min = 1) String by,
        Optional<String> hours,
        Optional<Long> cycles,
        List<@NotNull @Valid DefectIn> defects) {

    /** A sign off, with no readings or defects when none were sent. */
    public InspectionIn {
        hours = hours == null ? Optional.empty() : hours;
        cycles = cycles == null ? Optional.empty() : cycles;
        defects = defects == null ? List.of() : defects;
    }
}
