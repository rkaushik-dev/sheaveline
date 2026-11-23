package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.weather.Hold;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.WeatherHold;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Whether the weather holds a machine, with the reading it was made against.
 *
 * <p>The reading comes back with the answer, because the first question an operator asks after
 * being told the machine is held is which reading said so, and the second is how old it is.
 *
 * @param lift the machine
 * @param held whether it is held
 * @param mayRun whether it may carry the public
 * @param needsATimer whether a hold runs on a clock rather than lifting on a better reading
 * @param runSlow whether it runs, slowed because the wind is close to its limit
 * @param slowSpeed the rope speed it is slowed to, while it runs slow
 * @param gustLimit the gust its class may run in
 * @param crosswindLimit the crosswind its class may run in, when the class has one
 * @param crosswind the crosswind on the line
 * @param readingAge how old the reading is
 * @param reading the reading
 * @param reasons why it is held
 * @param slowing why it runs slow
 * @param openHolds the holds the day book still has open on the machine, and whether each may come
 *     off
 */
public record HoldOut(
        @LiftCodeText LiftCode lift,
        boolean held,
        boolean mayRun,
        @JsonProperty("needs_a_timer") boolean needsATimer,
        boolean runSlow,
        @SpeedText Optional<Speed> slowSpeed,
        @WindText Speed gustLimit,
        @WindText Optional<Speed> crosswindLimit,
        @WindText Speed crosswind,
        @DurationText Duration readingAge,
        ObservationOut reading,
        List<HoldReasonOut> reasons,
        List<HoldReasonOut> slowing,
        List<OpenHoldOut> openHolds) {

    /** A hold's shape. */
    public HoldOut {
        reasons = List.copyOf(reasons);
        slowing = List.copyOf(slowing);
        openHolds = List.copyOf(openHolds);
    }

    /**
     * A hold as the tablet shows it.
     *
     * @param decision the decision
     * @param now the moment it is, which the reading's age is measured from; a reading stamped after
     *     it is no age at all
     * @return its shape, with no open holds read
     */
    public static HoldOut of(WeatherHold decision, LocalDateTime now) {
        return of(decision, now, List.of());
    }

    /**
     * A hold as the tablet shows it, with the day book's open holds on the machine.
     *
     * @param decision the decision
     * @param now the moment it is
     * @param openHolds the open holds, already read against the reading
     * @return its shape
     */
    public static HoldOut of(WeatherHold decision, LocalDateTime now, List<OpenHoldOut> openHolds) {
        Observation seen = decision.observation();
        return new HoldOut(
                decision.lift().code(),
                decision.isHeld(),
                decision.mayRun(),
                decision.needsATimer(),
                decision.runsSlow(),
                decision.slowSpeed(),
                decision.gustLimit(),
                decision.crosswindLimit(),
                decision.crosswind(),
                now.isBefore(seen.taken()) ? Duration.ZERO : seen.ageAt(now),
                ObservationOut.of(seen),
                shapes(decision.holds()),
                shapes(decision.slowings()),
                openHolds);
    }

    private static List<HoldReasonOut> shapes(List<Hold> holds) {
        return holds.stream().map(HoldReasonOut::of).toList();
    }
}
