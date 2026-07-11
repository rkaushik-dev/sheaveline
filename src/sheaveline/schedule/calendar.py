"""The published hours, checked against the light.

The timetable says the quad loads until four. In late December the sun is behind
the ridge at ten past five and civil dusk is not far behind it, and the rule
nobody writes on the sign is that you do not put a chair on the line unless there
is enough daylight left to get everybody off it if the drive stops on the way up.

So the window a machine actually gets is the published one clipped by the light,
and how much light is enough depends on how long that machine takes to evacuate,
which depends on how many teams are on the hill, which is a staffing question
being answered by a calendar. That is the part worth having in one place: the
three of them decide each other, and doing it by eye is how a lift ends up
loading at four on the twenty first of December.

Nothing here publishes anything. It says what the hours would have to be, and
somebody in the office still decides whether to shorten the day or put another
team on.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from datetime import date

from sheaveline.measures.duration import Duration
from sheaveline.schedule.daylight import DaylightError, Site
from sheaveline.schedule.hours import HoursTable, OperatingWindow, TimeOfDay
from sheaveline.schedule.season import DayType, Season, SeasonError


@dataclass(frozen=True)
class OperatingDay:
    """One date, what kind of day it is, and the hours it can actually hold."""

    day: date
    day_type: DayType
    published: OperatingWindow | None
    window: OperatingWindow | None
    dusk: TimeOfDay
    clears_in: Duration

    def is_operating(self) -> bool:
        return self.window is not None

    def is_clipped(self) -> bool:
        """The light took time off the published day."""
        if self.published is None or self.window is None:
            return False
        return self.window.last_load < self.published.last_load

    def lost(self) -> Duration:
        if self.published is None or self.window is None:
            return Duration(0)
        return self.window.last_load.until(self.published.last_load)

    def is_impossible(self) -> bool:
        """There is not enough light to load at all, which happens at the solstice."""
        return self.published is not None and self.window is None

    def __str__(self) -> str:
        if self.window is None:
            state = "no light to load" if self.published is not None else "closed"
            return f"{self.day.isoformat()} {self.day_type.value}, {state}"
        note = f", clipped {self.lost().format_clock()}" if self.is_clipped() else ""
        return f"{self.day.isoformat()} {self.day_type.value}, {self.window}{note}"


@dataclass(frozen=True)
class SeasonCalendar:
    """A season, a timetable, a site and how long the machine takes to clear."""

    season: Season
    hours: HoursTable
    site: Site
    clears_in: Duration

    def __post_init__(self) -> None:
        if self.clears_in.is_zero():
            raise SeasonError("a machine that clears instantly has not been drilled")

    def latest_last_load(self, day: date) -> TimeOfDay | None:
        """The last moment a chair may go on and still be cleared before dark.

        Nothing on a day where the clearing does not fit in the daylight at all,
        which on a machine that takes four hours and a solstice that gives nine
        is not the hypothetical it sounds like.
        """
        try:
            return self.site.latest_start(day, self.clears_in)
        except DaylightError:
            return None

    def window_on(self, day: date) -> OperatingWindow | None:
        """The published window, clipped so the sweep still finishes in the light."""
        published = self.hours.window_on(self.season, day)
        if published is None:
            return None
        latest = self.latest_last_load(day)
        if latest is None or latest < published.first_load:
            return None
        if latest >= published.last_load:
            return published
        return OperatingWindow(
            first_load=published.first_load,
            last_load=latest,
            sweep_by=max(published.sweep_by, latest),
        )

    def day(self, wanted: date) -> OperatingDay:
        return OperatingDay(
            day=wanted,
            day_type=self.season.day_type(wanted),
            published=self.hours.window_on(self.season, wanted),
            window=self.window_on(wanted),
            dusk=self.site.civil_dusk(wanted),
            clears_in=self.clears_in,
        )

    def days(self) -> Iterator[OperatingDay]:
        for wanted in self.season.days():
            yield self.day(wanted)

    def operating_days(self) -> tuple[OperatingDay, ...]:
        return tuple(one for one in self.days() if one.is_operating())

    def clipped_days(self) -> tuple[OperatingDay, ...]:
        """Every day the light takes loading time off, which is what the sign is wrong about."""
        return tuple(one for one in self.days() if one.is_clipped())

    def impossible_days(self) -> tuple[OperatingDay, ...]:
        return tuple(one for one in self.days() if one.is_impossible())

    def loading_time(self) -> Duration:
        total = Duration(0)
        for one in self.operating_days():
            if one.window is not None:
                total = total + one.window.loading_time()
        return total

    def lost_to_the_light(self) -> Duration:
        total = Duration(0)
        for one in self.clipped_days():
            total = total + one.lost()
        return total

    def worst_day(self) -> OperatingDay | None:
        clipped = self.clipped_days()
        if not clipped:
            return None
        return max(clipped, key=lambda one: one.lost().seconds)

    def with_evacuation(self, clears_in: Duration) -> SeasonCalendar:
        """The same season read against a different evacuation time.

        Putting a second team on the hill halves the clearing time and hands back
        loading time on every short day, which is the trade the office is
        actually making when it decides how many people to roster in December.
        """
        return SeasonCalendar(self.season, self.hours, self.site, clears_in)

    def __str__(self) -> str:
        return (
            f"{self.season.label}: {len(self.operating_days())} operating days, "
            f"{len(self.clipped_days())} short of light"
        )
