package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Tower;

/**
 * One tower on the line, with both of its assemblies.
 *
 * @param number counted up the line from the drive
 * @param reference what is stencilled on it, such as {@code JP-T1}
 * @param station how far along the line it stands
 * @param baseElevation the elevation of its footing
 * @param height from the footing to the crossarm
 * @param crossarmElevation the elevation of the crossarm, worked out
 * @param uphill the assembly on the uphill side
 * @param downhill the assembly on the downhill side
 */
public record TowerOut(
        int number,
        String reference,
        @LengthFeet Length station,
        @LengthFeet Length baseElevation,
        @LengthFeet Length height,
        @LengthFeet Length crossarmElevation,
        AssemblyOut uphill,
        AssemblyOut downhill) {

    /**
     * The tower as the tablet shows it.
     *
     * @param tower the tower
     * @return its shape
     */
    public static TowerOut of(Tower tower) {
        return new TowerOut(
                tower.number(),
                tower.ref().toString(),
                tower.station(),
                tower.baseElevation(),
                tower.height(),
                tower.crossarmElevation(),
                AssemblyOut.of(tower.uphill()),
                AssemblyOut.of(tower.downhill()));
    }
}
