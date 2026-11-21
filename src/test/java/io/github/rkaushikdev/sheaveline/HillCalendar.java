package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.schedule.HolidayPeriod;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import io.github.rkaushikdev.sheaveline.schedule.Site;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The site and the season of the hill the tests are written against. */
public final class HillCalendar {

    private HillCalendar() {}

    /**
     * Ogden Peak, forty one degrees north in the Wasatch, seven hours behind universal time.
     *
     * @return the site
     */
    public static Site theSite() {
        return new Site("Ogden Peak", 41.216, -111.857, -420);
    }

    /**
     * The 2024/25 winter: a week of preview, two holiday periods, spring from late March, and one
     * scheduled closure in January.
     *
     * @return the season
     */
    public static Season theSeason() {
        return new Season(
                "2024/25",
                LocalDate.of(2024, 11, 29),
                LocalDate.of(2025, 4, 20),
                Optional.of(LocalDate.of(2024, 11, 23)),
                Optional.of(LocalDate.of(2025, 3, 24)),
                List.of(
                        new HolidayPeriod("christmas", LocalDate.of(2024, 12, 21), LocalDate.of(2025, 1, 1)),
                        new HolidayPeriod("presidents", LocalDate.of(2025, 2, 15), LocalDate.of(2025, 2, 23))),
                Set.of(LocalDate.of(2025, 1, 14)));
    }
}
