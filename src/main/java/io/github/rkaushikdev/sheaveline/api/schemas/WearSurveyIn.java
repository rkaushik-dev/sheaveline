package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * A rope walk, as the mechanic types it on the tablet standing next to the rope with the caliper
 * still in their hand.
 *
 * <p>Everything it carries is a measure written as text, and the readings are a list rather than a
 * fixed number of them because nobody knows in advance how many stations a walk will find worth
 * writing down.
 *
 * @param taken the day it was walked
 * @param inspector who walked it
 * @param diameters the caliper readings, at least one
 * @param lays the lay readings, none when not sent
 */
public record WearSurveyIn(
        @NotNull LocalDate taken,
        @NotNull @Size(min = 1) String inspector,
        @NotNull @Size(min = 1) List<@NotNull @Valid DiameterReadingIn> diameters,
        List<@NotNull @Valid LayReadingIn> lays) {

    /** A walk, with no lay readings when none were sent. */
    public WearSurveyIn {
        lays = lays == null ? List.of() : lays;
    }

    /**
     * The walk in the domain's own terms.
     *
     * @param rope the rope it was walked on
     * @return the survey
     * @throws io.github.rkaushikdev.sheaveline.ropework.RopeworkError when the rope could not have
     *     been walked that way
     */
    public WearSurvey toDomain(HaulRope rope) {
        return WearSurvey.of(
                rope,
                taken,
                inspector,
                diameters.stream().map(DiameterReadingIn::toDomain).toList(),
                lays.stream().map(LayReadingIn::toDomain).toList());
    }
}
