package io.github.rkaushikdev.sheaveline.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;

/**
 * Sunrise, sunset and the daylight the department is allowed to work in.
 *
 * <p>Two rules read this and neither of them is about comfort. A lift may not load in the dark
 * unless the line is lit, and an evacuation has to be finished before the light goes, which means
 * the latest hour a crew may start one is a calculation and not a policy. Both need real times for
 * this hill on this date, so the solar position is computed rather than looked up.
 *
 * <p>The arithmetic is the standard low precision solar position the weather service publishes. It
 * is good to about a minute at this latitude, which is finer than any decision made from it. It runs
 * in IEEE 754 doubles through {@link StrictMath} so every machine prints the same minute.
 */
public final class Daylight {

    /** The zenith angle of the sun's upper limb at sunrise and sunset, refraction included. */
    public static final double SUNRISE_ZENITH = 90.833;

    /** The zenith angle at the edge of civil twilight. */
    public static final double CIVIL_TWILIGHT_ZENITH = 96.0;

    /** Minutes of clock time per degree of longitude. */
    public static final int MINUTES_PER_DEGREE = 4;

    private Daylight() {}

    /**
     * Whether summer time is in force under the United States rules: from the second Sunday in
     * March up to the first Sunday in November.
     *
     * @param day the date
     * @return true while the clocks are an hour ahead
     */
    public static boolean inSummerTime(LocalDate day) {
        LocalDate start = nthSunday(day.getYear(), 3, 2);
        LocalDate end = nthSunday(day.getYear(), 11, 1);
        return !day.isBefore(start) && day.isBefore(end);
    }

    private static LocalDate nthSunday(int year, int month, int nth) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .plusWeeks(nth - 1L);
    }

    static double fractionalYear(LocalDate day) {
        int daysInYear = Year.isLeap(day.getYear()) ? 366 : 365;
        return 2 * Math.PI / daysInYear * (day.getDayOfYear() - 1 + 0.5);
    }

    static double equationOfTime(double gamma) {
        return 229.18
                * (0.000075
                        + 0.001868 * StrictMath.cos(gamma)
                        - 0.032077 * StrictMath.sin(gamma)
                        - 0.014615 * StrictMath.cos(2 * gamma)
                        - 0.040849 * StrictMath.sin(2 * gamma));
    }

    static double declination(double gamma) {
        return 0.006918
                - 0.399912 * StrictMath.cos(gamma)
                + 0.070257 * StrictMath.sin(gamma)
                - 0.006758 * StrictMath.cos(2 * gamma)
                + 0.000907 * StrictMath.sin(2 * gamma)
                - 0.002697 * StrictMath.cos(3 * gamma)
                + 0.00148 * StrictMath.sin(3 * gamma);
    }
}
