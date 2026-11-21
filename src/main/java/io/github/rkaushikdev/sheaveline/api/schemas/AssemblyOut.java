package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.plant.SheaveAssembly;

/**
 * One sheave assembly on a tower.
 *
 * @param kind support, hold down or compression
 * @param sheaves how many sheaves it carries
 * @param deviation the angle the rope turns through it
 */
public record AssemblyOut(String kind, int sheaves, @AngleText Angle deviation) {

    /**
     * The assembly as the tablet shows it.
     *
     * @param assembly the assembly
     * @return its shape
     */
    public static AssemblyOut of(SheaveAssembly assembly) {
        return new AssemblyOut(assembly.kind().value(), assembly.sheaves(), assembly.deviation());
    }
}
