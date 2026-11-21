package io.github.rkaushikdev.sheaveline.api.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.WeatherHold;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
 * @param gustLimit the gust its class may run in
 * @param crosswindLimit the crosswind its class may run in, when the class has one
 * @param crosswind the crosswind on the line
 * @param readingAge how old the reading is
 * @param reading the reading
 * @param reasons why it is held
 */
public record HoldOut(
        @LiftCodeText LiftCode lift,
        boolean held,
        boolean mayRun,
        @JsonProperty("needs_a_timer") boolean needsATimer,
        @WindText Speed gustLimit,
        @WindText Optional<Speed> crosswindLimit,
        @WindText Speed crosswind,
        @DurationText Duration readingAge,
        ObservationOut reading,
        List<HoldReasonOut> reasons) {

    /** A hold's shape. */
    public HoldOut {
        reasons = List.copyOf(reasons);
    }

    /**
     * A hold as the tablet shows it.
     *
     * @param decision the decision
     * @param now the moment it is, which the reading's age is measured from
     * @return its shape
     */
    public static HoldOut of(WeatherHold decision, LocalDateTime now) {
        Observation seen = decision.observation();
        Duration age = now.isBefore(seen.taken())
                ? Duration.ZERO
                : Duration.ofSeconds(ChronoUnit.SECONDS.between(seen.taken(), now));
        return new HoldOut(
                decision.lift().code(),
                decision.isHeld(),
                decision.mayRun(),
                decision.needsATimer(),
                decision.gustLimit(),
                decision.crosswindLimit(),
                decision.crosswind(),
                age,
                ObservationOut.of(seen),
                decision.holds().stream().map(HoldReasonOut::of).toList());
    }
}
