package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.time.LocalDate;
import java.util.List;

/**
 * A rope walk with its verdict, which comes back in the same reply, because standing next to the
 * rope is exactly when somebody wants to know whether it stays on.
 *
 * @param serial the rope walked
 * @param taken the day it was walked
 * @param inspector who walked it
 * @param smallest the reading with the smallest mean diameter
 * @param readings every caliper reading, in the order taken
 * @param verdict what the walk says about the rope
 */
public record WearSurveyOut(
        @RopeSerialText RopeSerial serial,
        LocalDate taken,
        String inspector,
        DiameterReadingOut smallest,
        List<DiameterReadingOut> readings,
        String verdict) {

    /** A walk's shape. */
    public WearSurveyOut {
        readings = List.copyOf(readings);
    }

    /**
     * The walk as the tablet shows it.
     *
     * @param survey the walk
     * @param verdict what it says about the rope
     * @return its shape
     */
    public static WearSurveyOut of(WearSurvey survey, String verdict) {
        return new WearSurveyOut(
                survey.rope().serial(),
                survey.taken(),
                survey.inspector(),
                DiameterReadingOut.of(survey.smallest()),
                survey.diameters().stream().map(DiameterReadingOut::of).toList(),
                verdict);
    }
}
