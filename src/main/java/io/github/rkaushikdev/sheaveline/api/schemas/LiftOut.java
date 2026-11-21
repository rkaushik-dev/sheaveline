package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.List;

/**
 * What the installation looks like on the wire: one machine in full.
 *
 * <p>Read shapes only. The department does not create a chairlift over http, and pretending
 * otherwise would mean a body with four hundred fields in it that nobody could fill in correctly.
 * Machines are loaded from the survey through the command line; this surface answers questions
 * about them.
 *
 * <p>Every derived figure is computed here rather than stored, so the tablet at the terminal cannot
 * be looking at a ride time that was right in 2019.
 *
 * @param code its code
 * @param name its name
 * @param liftClass the class the board files it under
 * @param bearing the bearing of the line, bottom to top
 * @param designSpeed the rope speed it was designed for
 * @param commissioned the day it first ran
 * @param horizontalLength the horizontal length of the line
 * @param verticalRise how much the line climbs
 * @param slopeLength the length of the line along the slope
 * @param rideTime how long a ride takes at the design speed
 * @param ropeLengthAgrees whether the rope on it is the length the geometry calls for
 * @param terminals the bottom terminal, then the top
 * @param towers the towers, up the line
 * @param fleet the carriers, summarised
 * @param rope the haul rope
 * @param spans the spans, up the line
 */
public record LiftOut(
        @LiftCodeText LiftCode code,
        String name,
        String liftClass,
        @BearingText Bearing bearing,
        @SpeedText Speed designSpeed,
        LocalDate commissioned,
        @LengthFeet Length horizontalLength,
        @LengthFeet Length verticalRise,
        @LengthFeet Length slopeLength,
        @DurationText Duration rideTime,
        boolean ropeLengthAgrees,
        List<TerminalOut> terminals,
        List<TowerOut> towers,
        FleetOut fleet,
        LiftRopeOut rope,
        List<SpanOut> spans) {

    /** A machine's shape. */
    public LiftOut {
        terminals = List.copyOf(terminals);
        towers = List.copyOf(towers);
        spans = List.copyOf(spans);
    }

    /**
     * The machine as the tablet shows it.
     *
     * @param lift the machine
     * @return its shape
     */
    public static LiftOut of(Lift lift) {
        return new LiftOut(
                lift.code(),
                lift.name(),
                lift.liftClass().value(),
                lift.bearing(),
                lift.designSpeed(),
                lift.commissioned(),
                lift.horizontalLength(),
                lift.verticalRise(),
                lift.slopeLength(),
                lift.rideTime(),
                lift.ropeLengthAgrees(),
                List.of(TerminalOut.of(lift.bottom()), TerminalOut.of(lift.top())),
                lift.towers().towers().stream().map(TowerOut::of).toList(),
                FleetOut.of(lift.carriers()),
                LiftRopeOut.of(lift.rope()),
                lift.spans().spans().stream().map(SpanOut::of).toList());
    }

    /**
     * The machine as a list page shows it.
     *
     * @param lift the machine
     * @return its summary
     */
    public static LiftSummaryOut summaryOf(Lift lift) {
        return new LiftSummaryOut(
                lift.code(), lift.name(), lift.liftClass().value(), lift.designSpeed(), lift.carriersOnLine());
    }
}
