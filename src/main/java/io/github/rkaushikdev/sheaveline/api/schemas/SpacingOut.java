package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;

/**
 * How far apart the carriers are and how often one comes into the terminal, against the floor the
 * machine's class allows.
 *
 * @param carriers how many carriers are on the rope
 * @param speed the rope speed
 * @param spacing the distance from one carrier to the next
 * @param interval how often one arrives
 * @param classFloor the shortest interval the class allows
 * @param meetsTheFloor whether the interval is at least that
 * @param shortfall by how much it falls short, nothing when it does not
 * @param mostCarriers the most carriers the floor allows on the rope
 */
public record SpacingOut(
        int carriers,
        @SpeedText Speed speed,
        @LengthFeet Length spacing,
        @DurationText Duration interval,
        @DurationText Duration classFloor,
        boolean meetsTheFloor,
        @DurationText Duration shortfall,
        int mostCarriers) {}
