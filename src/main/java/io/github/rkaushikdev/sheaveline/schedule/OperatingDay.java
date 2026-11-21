package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One date, what kind of day it is, and the hours it can actually hold.
 *
 * @param day the date
 * @param dayType what kind of day it is
 * @param published the window on the sign, when the day has one
 * @param window the window the light allows, when there is one at all
 * @param dusk civil dusk that day
 * @param clearsIn how long the machine takes to clear
 */
public record OperatingDay(
        LocalDate day,
        DayType dayType,
        Optional<OperatingWindow> published,
        Optional<OperatingWindow> window,
        TimeOfDay dusk,
        Duration clearsIn) {

    /** An operating day, nothing left null. */
    public OperatingDay {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(dayType, "dayType");
        Objects.requireNonNull(published, "published");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(dusk, "dusk");
        Objects.requireNonNull(clearsIn, "clearsIn");
    }

    /**
     * Whether the lift loads at all.
     *
     * @return true when the day has a window
     */
    public boolean isOperating() {
        return window.isPresent();
    }

    /**
     * Whether the light took time off the published day.
     *
     * @return true when the window's last load is earlier than the sign's
     */
    public boolean isClipped() {
        if (published.isEmpty() || window.isEmpty()) {
            return false;
        }
        return window.get().lastLoad().compareTo(published.get().lastLoad()) < 0;
    }

    /**
     * How much loading time the light took.
     *
     * @return the window's last load to the sign's, or zero
     */
    public Duration lost() {
        if (published.isEmpty() || window.isEmpty()) {
            return Duration.ZERO;
        }
        return window.get().lastLoad().until(published.get().lastLoad());
    }

    /**
     * Whether there is not enough light to load at all, which happens at the solstice.
     *
     * @return true when the sign has hours and the light allows none
     */
    public boolean isImpossible() {
        return published.isPresent() && window.isEmpty();
    }

    /** Such as {@code 2024-12-21 holiday, 8:30 to 15:10, swept by 16:30, clipped 50:00}. */
    @Override
    public String toString() {
        if (window.isEmpty()) {
            String state = published.isPresent() ? "no light to load" : "closed";
            return day + " " + dayType.value() + ", " + state;
        }
        String note = isClipped() ? ", clipped " + lost().formatClock() : "";
        return day + " " + dayType.value() + ", " + window.get() + note;
    }
}
