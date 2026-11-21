package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The published hours, checked against the light.
 *
 * <p>The timetable says the quad loads until four. In late December the sun is behind the ridge at
 * ten past five, and the rule nobody writes on the sign is that you do not put a chair on the line
 * unless there is enough daylight left to get everybody off it if the drive stops on the way up.
 *
 * <p>So the window a machine actually gets is the published one clipped by the light, and how much
 * light is enough depends on how long that machine takes to evacuate, which depends on how many
 * teams are on the hill, which is a staffing question being answered by a calendar. The three of
 * them decide each other, and this is where they meet.
 *
 * <p>Nothing here publishes anything. It says what the hours would have to be, and somebody in the
 * office still decides whether to shorten the day or put another team on.
 *
 * @param season the season
 * @param hours the published timetable
 * @param site where the hill is
 * @param clearsIn how long the machine takes to clear the line
 */
public record SeasonCalendar(Season season, HoursTable hours, Site site, Duration clearsIn) {

    /**
     * A calendar for a drilled machine.
     *
     * @throws SeasonError when the clearing time is zero
     */
    public SeasonCalendar {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(clearsIn, "clearsIn");
        if (clearsIn.isZero()) {
            throw new SeasonError("a machine that clears instantly has not been drilled");
        }
    }

    /**
     * The last moment a chair may go on and still be cleared before dark.
     *
     * <p>Nothing on a day where the clearing does not fit in the daylight at all, which on a machine
     * that takes four hours and a solstice that gives nine is not the hypothetical it sounds like.
     *
     * @param day the date
     * @return the latest last load, or empty
     */
    public Optional<TimeOfDay> latestLastLoad(LocalDate day) {
        try {
            return Optional.of(site.latestStart(day, clearsIn));
        } catch (DaylightError noRoom) {
            return Optional.empty();
        }
    }

    /**
     * The published window, clipped so the sweep still finishes in the light.
     *
     * @param day the date
     * @return the window, or empty on a closed day or a day with no light to load in
     */
    public Optional<OperatingWindow> windowOn(LocalDate day) {
        Optional<OperatingWindow> found = hours.windowOn(season, day);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OperatingWindow published = found.get();
        Optional<TimeOfDay> latest = latestLastLoad(day);
        if (latest.isEmpty() || latest.get().compareTo(published.firstLoad()) < 0) {
            return Optional.empty();
        }
        TimeOfDay last = latest.get();
        if (last.compareTo(published.lastLoad()) >= 0) {
            return found;
        }
        TimeOfDay sweep = published.sweepBy().compareTo(last) >= 0 ? published.sweepBy() : last;
        return Optional.of(new OperatingWindow(published.firstLoad(), last, sweep));
    }

    /**
     * Everything the calendar knows about one date.
     *
     * @param wanted the date
     * @return the day
     */
    public OperatingDay day(LocalDate wanted) {
        return new OperatingDay(
                wanted,
                season.dayType(wanted),
                hours.windowOn(season, wanted),
                windowOn(wanted),
                site.civilDusk(wanted),
                clearsIn);
    }

    /**
     * Every date of the season.
     *
     * @return the days in order
     */
    public List<OperatingDay> days() {
        return season.days().stream().map(this::day).toList();
    }

    /**
     * The days the lift loads.
     *
     * @return those days in order
     */
    public List<OperatingDay> operatingDays() {
        return days().stream().filter(OperatingDay::isOperating).toList();
    }

    /**
     * Every day the light takes loading time off, which is what the sign is wrong about.
     *
     * @return those days in order
     */
    public List<OperatingDay> clippedDays() {
        return days().stream().filter(OperatingDay::isClipped).toList();
    }

    /**
     * The days with published hours and no light to load in.
     *
     * @return those days in order
     */
    public List<OperatingDay> impossibleDays() {
        return days().stream().filter(OperatingDay::isImpossible).toList();
    }

    /**
     * How long the lift loads over the season.
     *
     * @return the total loading time
     */
    public Duration loadingTime() {
        Duration total = Duration.ZERO;
        for (OperatingDay one : operatingDays()) {
            total = total.plus(one.window().orElseThrow().loadingTime());
        }
        return total;
    }

    /**
     * How much loading time the light takes over the season.
     *
     * @return the total lost
     */
    public Duration lostToTheLight() {
        Duration total = Duration.ZERO;
        for (OperatingDay one : clippedDays()) {
            total = total.plus(one.lost());
        }
        return total;
    }

    /**
     * The day the light takes the most from, the earliest of them on a tie.
     *
     * @return that day, or empty when no day is clipped
     */
    public Optional<OperatingDay> worstDay() {
        OperatingDay worst = null;
        for (OperatingDay one : clippedDays()) {
            if (worst == null || one.lost().seconds() > worst.lost().seconds()) {
                worst = one;
            }
        }
        return Optional.ofNullable(worst);
    }

    /**
     * The same season read against a different evacuation time.
     *
     * <p>Putting a second team on the hill halves the clearing time and hands back loading time on
     * every short day, which is the trade the office is actually making when it decides how many
     * people to roster in December.
     *
     * @param clears how long the machine would take to clear
     * @return the calendar with that clearing time
     */
    public SeasonCalendar withEvacuation(Duration clears) {
        return new SeasonCalendar(season, hours, site, clears);
    }

    /** Such as {@code 2024/25: 148 operating days, 31 short of light}. */
    @Override
    public String toString() {
        return season.label() + ": " + operatingDays().size() + " operating days, "
                + clippedDays().size() + " short of light";
    }
}
