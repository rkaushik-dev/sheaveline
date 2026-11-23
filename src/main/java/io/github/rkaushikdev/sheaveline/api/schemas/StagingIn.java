package io.github.rkaushikdev.sheaveline.api.schemas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the duty manager sends to plan an evacuation: the teams on the hill this afternoon, and the
 * conditions.
 *
 * <p>The teams come up in the request rather than out of the database, because the teams on the hill
 * on any given afternoon are whoever turned up, and the shift board is not something this system
 * holds. What it does hold is the line, which is the half nobody can type in from memory.
 *
 * @param teams the teams, one to twelve
 * @param seatsFilled people in each carrier, three when not sent
 * @param snow the snow depth, two feet when not sent
 * @param tension the haul rope tension, 34,500 lb when not sent
 * @param ice the ice on the rope, measured out from its surface, none when not sent
 */
public record StagingIn(
        @NotNull @Size(min = 1, max = 12) List<@NotNull @Valid TeamIn> teams,
        @Min(1) @Max(8) Integer seatsFilled,
        String snow,
        String tension,
        String ice) {

    /** People in each carrier when nobody says. */
    public static final int DEFAULT_SEATS_FILLED = 3;

    /** The snow depth when nobody says. */
    public static final String DEFAULT_SNOW = "2 ft";

    /** The haul rope tension when nobody says. */
    public static final String DEFAULT_TENSION = "34500 lb";

    /** The ice on the rope when nobody says. */
    public static final String DEFAULT_ICE = "0 in";

    /** A plan's conditions, defaulted where nobody said. */
    public StagingIn {
        seatsFilled = seatsFilled == null ? DEFAULT_SEATS_FILLED : seatsFilled;
        snow = snow == null ? DEFAULT_SNOW : snow;
        tension = tension == null ? DEFAULT_TENSION : tension;
        ice = ice == null ? DEFAULT_ICE : ice;
    }
}
