package io.github.rkaushikdev.sheaveline.api.schemas;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Optional;

/**
 * One team off the truck, with what is actually on it.
 *
 * @param name what the team is called
 * @param ladderFeet the ladder it carries, in feet, when it carries one
 * @param ropeFeet the rope it carries, in feet, when it carries one
 * @param people how many are on it, three when not sent
 */
public record TeamIn(
        @NotNull @Size(min = 1, max = 32) String name,
        Optional<@Min(1) @Max(60) Integer> ladderFeet,
        Optional<@Min(1) @Max(1000) Integer> ropeFeet,
        @Min(2) @Max(8) Integer people) {

    /** How many are on a team when nobody says. */
    public static final int DEFAULT_PEOPLE = 3;

    /** A team, three strong when nobody said. */
    public TeamIn {
        ladderFeet = ladderFeet == null ? Optional.empty() : ladderFeet;
        ropeFeet = ropeFeet == null ? Optional.empty() : ropeFeet;
        people = people == null ? DEFAULT_PEOPLE : people;
    }
}
