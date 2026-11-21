package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.evac.Beat;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;

/**
 * One team's beat: where it parks and what it has to clear.
 *
 * @param team the team
 * @param stagedAt where it parks
 * @param carriers how many carriers are on its beat
 * @param walking how far it walks
 * @param busyFor how long the beat keeps it busy
 * @param reachesEverything whether its kit reaches every carrier on the beat
 */
public record BeatOut(
        String team,
        @LengthFeet Length stagedAt,
        int carriers,
        @LengthFeet Length walking,
        @DurationText Duration busyFor,
        boolean reachesEverything) {

    /**
     * A beat as the duty manager's tablet shows it.
     *
     * @param beat the beat
     * @return its shape
     */
    public static BeatOut of(Beat beat) {
        return new BeatOut(
                beat.team().name(),
                beat.stagedAt(),
                beat.stops().size(),
                beat.walking(),
                beat.work(),
                beat.reachesEverything());
    }
}
