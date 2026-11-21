package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.evac.StagingPlan;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.util.List;
import java.util.Optional;

/**
 * The evacuation plan on the wire, for the tablet the duty manager carries.
 *
 * <p>The beat sheet goes back in the order the teams work it, top to bottom, because that is the
 * order somebody reads it out over the radio.
 *
 * @param lift the machine
 * @param workable whether every carrier on the line can be cleared
 * @param clearsIn from the call out to the busiest team finishing, when anybody was staged
 * @param serialTime what it would take with the beats worked one after another, when anybody was
 *     staged
 * @param spread the busiest team's work less the quietest's, when anybody was staged
 * @param carriers how many carriers are on the line
 * @param stranded how many nobody can reach
 * @param beats one beat per team staged
 * @param refusals why the plan falls short, in sentences
 */
public record StagingOut(
        @LiftCodeText LiftCode lift,
        boolean workable,
        @DurationText Optional<Duration> clearsIn,
        @DurationText Optional<Duration> serialTime,
        @DurationText Optional<Duration> spread,
        int carriers,
        int stranded,
        List<BeatOut> beats,
        List<String> refusals) {

    /** A plan's shape. */
    public StagingOut {
        beats = List.copyOf(beats);
        refusals = List.copyOf(refusals);
    }

    /**
     * A plan as the duty manager's tablet shows it.
     *
     * @param plan the plan
     * @return its shape
     */
    public static StagingOut of(StagingPlan plan) {
        boolean staged = !plan.beats().isEmpty();
        return new StagingOut(
                plan.profile().lift().code(),
                plan.isWorkable(),
                staged ? Optional.of(plan.clearsIn()) : Optional.empty(),
                staged ? Optional.of(plan.serialTime()) : Optional.empty(),
                staged ? Optional.of(plan.spread()) : Optional.empty(),
                plan.stops().size(),
                plan.stranded().size(),
                plan.beats().stream().map(BeatOut::of).toList(),
                plan.refusals());
    }
}
