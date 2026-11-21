package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Where the hill is, which is all the solar arithmetic needs.
 *
 * @param name what the place is called, for the refusals
 * @param latitude degrees north, minus ninety to ninety
 * @param longitude degrees east, minus one eighty to one eighty
 * @param standardOffsetMinutes the standard clock offset from universal time
 * @param observesSummerTime whether the clocks go forward an hour in summer
 */
public record Site(
        String name, double latitude, double longitude, int standardOffsetMinutes, boolean observesSummerTime) {

    /**
     * A site with coordinates on the globe.
     *
     * @throws RangeError when either coordinate is off the globe
     */
    public Site {
        Objects.requireNonNull(name, "name");
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new RangeError("latitude", latitude, -90, 90);
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new RangeError("longitude", longitude, -180, 180);
        }
    }

    /**
     * A site that observes summer time.
     *
     * @param name what the place is called
     * @param latitude degrees north
     * @param longitude degrees east
     * @param standardOffsetMinutes the standard clock offset
     */
    public Site(String name, double latitude, double longitude, int standardOffsetMinutes) {
        this(name, latitude, longitude, standardOffsetMinutes, true);
    }

    /**
     * The clock offset in force on that date, summer time included.
     *
     * @param day the date
     * @return minutes from universal time
     */
    public int offsetMinutes(LocalDate day) {
        if (observesSummerTime && Daylight.inSummerTime(day)) {
            return standardOffsetMinutes + 60;
        }
        return standardOffsetMinutes;
    }

    /**
     * Sunrise on the local clock.
     *
     * @param day the date
     * @return the minute the sun's upper limb clears the horizon
     * @throws DaylightError when the sun neither rises nor sets that day
     */
    public TimeOfDay sunrise(LocalDate day) {
        return event(day, Daylight.SUNRISE_ZENITH, true);
    }

    /**
     * Sunset on the local clock.
     *
     * @param day the date
     * @return the minute the sun goes
     * @throws DaylightError when the sun neither rises nor sets that day
     */
    public TimeOfDay sunset(LocalDate day) {
        return event(day, Daylight.SUNRISE_ZENITH, false);
    }

    /**
     * The start of civil twilight in the morning.
     *
     * @param day the date
     * @return civil dawn
     */
    public TimeOfDay civilDawn(LocalDate day) {
        return event(day, Daylight.CIVIL_TWILIGHT_ZENITH, true);
    }

    /**
     * The end of civil twilight in the evening.
     *
     * @param day the date
     * @return civil dusk
     */
    public TimeOfDay civilDusk(LocalDate day) {
        return event(day, Daylight.CIVIL_TWILIGHT_ZENITH, false);
    }

    /**
     * How long the sun is up.
     *
     * @param day the date
     * @return sunrise to sunset
     */
    public Duration daylight(LocalDate day) {
        return sunrise(day).until(sunset(day));
    }

    /**
     * Whether the sun is up at that moment.
     *
     * @param day the date
     * @param moment the time
     * @return true from sunrise to sunset, both included
     */
    public boolean isDaylight(LocalDate day, TimeOfDay moment) {
        return sunrise(day).compareTo(moment) <= 0 && moment.compareTo(sunset(day)) <= 0;
    }

    /**
     * The last minute a job of that length can begin and still end in the light.
     *
     * @param day the date
     * @param needed how long the job takes, in whole minutes
     * @return the latest start
     * @throws DaylightError when the job is longer than the day up to sunset
     */
    public TimeOfDay latestStart(LocalDate day, Duration needed) {
        TimeOfDay finishBy = sunset(day);
        long minutes = needed.seconds() / 60;
        if (finishBy.minutes() < minutes) {
            throw new DaylightError(needed.formatHms() + " does not fit in the daylight of " + day);
        }
        return new TimeOfDay((int) (finishBy.minutes() - minutes));
    }

    /**
     * Whether a job started then finishes before sunset.
     *
     * @param day the date
     * @param start when it begins
     * @param needed how long it takes
     * @return true when it starts in the light and ends by sunset
     */
    public boolean fitsInDaylight(LocalDate day, TimeOfDay start, Duration needed) {
        if (!isDaylight(day, start)) {
            return false;
        }
        return start.minutes() + needed.seconds() / 60 <= sunset(day).minutes();
    }

    private TimeOfDay event(LocalDate day, double zenith, boolean rising) {
        double gamma = Daylight.fractionalYear(day);
        double equation = Daylight.equationOfTime(gamma);
        double declination = Daylight.declination(gamma);
        double lat = Math.toRadians(latitude);
        double argument = StrictMath.cos(Math.toRadians(zenith)) / (StrictMath.cos(lat) * StrictMath.cos(declination))
                - StrictMath.tan(lat) * StrictMath.tan(declination);
        if (!(argument >= -1 && argument <= 1)) {
            throw new DaylightError("the sun neither rises nor sets at " + name + " on " + day);
        }
        double hourAngle = Math.toDegrees(StrictMath.acos(argument));
        if (!rising) {
            hourAngle = -hourAngle;
        }
        double utcMinutes = 720 - Daylight.MINUTES_PER_DEGREE * (longitude + hourAngle) - equation;
        double local = utcMinutes + offsetMinutes(day);
        return new TimeOfDay(Math.floorMod((long) Math.rint(local), TimeOfDay.MINUTES_PER_DAY));
    }
}
