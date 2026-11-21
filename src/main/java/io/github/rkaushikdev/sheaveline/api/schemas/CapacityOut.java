package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Speed;

/**
 * What the machine carries uphill in an hour, three ways.
 *
 * @param carriers how many carriers are on the rope
 * @param speed the rope speed
 * @param occupancy the share of seats filled, as the caller wrote it
 * @param carriersPerHour carriers into the top terminal in an hour
 * @param seatsPerHour seats into it
 * @param personsPerHour people into it, at that occupancy
 * @param verticalFeetPerHour those people times the vertical rise
 */
public record CapacityOut(
        int carriers,
        @SpeedText Speed speed,
        String occupancy,
        int carriersPerHour,
        int seatsPerHour,
        int personsPerHour,
        long verticalFeetPerHour) {}
