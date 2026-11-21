package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import java.util.List;

/**
 * The carriers on a machine, summarised rather than listed.
 *
 * @param kind what the public rides in
 * @param seatsEach the seats in each
 * @param onLine how many are on the rope
 * @param withdrawn how many are off it
 * @param seatsOnLine the seats on the rope in all
 * @param missingNumbers the numbers nobody carries
 */
public record FleetOut(
        String kind, int seatsEach, int onLine, int withdrawn, int seatsOnLine, List<Integer> missingNumbers) {

    /** A fleet's shape. */
    public FleetOut {
        missingNumbers = List.copyOf(missingNumbers);
    }

    /**
     * The fleet as the tablet shows it.
     *
     * @param carriers the fleet
     * @return its shape
     */
    public static FleetOut of(CarrierSet carriers) {
        return new FleetOut(
                carriers.kind().value(),
                carriers.kind().seats(),
                carriers.onLine().size(),
                carriers.withdrawn().size(),
                carriers.seatsOnLine(),
                carriers.missingNumbers());
    }
}
