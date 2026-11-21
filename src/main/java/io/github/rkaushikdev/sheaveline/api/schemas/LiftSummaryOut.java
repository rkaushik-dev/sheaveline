package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;

/**
 * The five things a list page shows about a machine.
 *
 * @param code its code
 * @param name its name
 * @param liftClass the class the board files it under
 * @param designSpeed the rope speed it was designed for
 * @param carriersOnLine how many carriers are on the rope
 */
public record LiftSummaryOut(
        @LiftCodeText LiftCode code, String name, String liftClass, @SpeedText Speed designSpeed, int carriersOnLine) {}
