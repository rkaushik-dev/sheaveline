"""Sunrise, sunset and the daylight the department is allowed to work in.

Two rules read this module and neither of them is about comfort. A lift may not
load in the dark unless the line is lit, and an evacuation has to be finished
before the light goes, which means the latest hour a crew may start one is a
calculation and not a policy. Both need real times for this hill on this date,
so the solar position is computed rather than looked up.

The arithmetic is the standard low precision solar position used by the weather
service. It is good to about a minute at this latitude, which is finer than any
decision made from it.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Final

from sheaveline.errors import RangeError, SheavelineError
from sheaveline.measures.duration import Duration
from sheaveline.schedule.hours import MINUTES_PER_DAY, TimeOfDay

SUNRISE_ZENITH: Final = 90.833
CIVIL_TWILIGHT_ZENITH: Final = 96.0
MINUTES_PER_DEGREE: Final = 4


class DaylightError(SheavelineError):
    """The sun does not rise or set on that date at that place."""


@dataclass(frozen=True)
class Site:
    """Where the hill is, which is all the solar arithmetic needs."""

    name: str
    latitude: float
    longitude: float
    standard_offset_minutes: int
    observes_summer_time: bool = True

    def __post_init__(self) -> None:
        if not -90 <= self.latitude <= 90:
            raise RangeError("latitude", self.latitude, -90, 90)
        if not -180 <= self.longitude <= 180:
            raise RangeError("longitude", self.longitude, -180, 180)

    def offset_minutes(self, day: date) -> int:
        """The clock offset in force on that date, summer time included."""
        if self.observes_summer_time and in_summer_time(day):
            return self.standard_offset_minutes + 60
        return self.standard_offset_minutes

    def sunrise(self, day: date) -> TimeOfDay:
        return self._event(day, SUNRISE_ZENITH, rising=True)

    def sunset(self, day: date) -> TimeOfDay:
        return self._event(day, SUNRISE_ZENITH, rising=False)

    def civil_dawn(self, day: date) -> TimeOfDay:
        return self._event(day, CIVIL_TWILIGHT_ZENITH, rising=True)

    def civil_dusk(self, day: date) -> TimeOfDay:
        return self._event(day, CIVIL_TWILIGHT_ZENITH, rising=False)

    def daylight(self, day: date) -> Duration:
        return self.sunrise(day).until(self.sunset(day))

    def is_daylight(self, day: date, moment: TimeOfDay) -> bool:
        return self.sunrise(day) <= moment <= self.sunset(day)

    def latest_start(self, day: date, needed: Duration) -> TimeOfDay:
        """The last minute a job of that length can begin and still end in the light."""
        finish_by = self.sunset(day)
        minutes = needed.seconds // 60
        if finish_by.minutes < minutes:
            raise DaylightError(
                f"{needed.format_hms()} does not fit in the daylight of {day.isoformat()}"
            )
        return TimeOfDay(finish_by.minutes - minutes)

    def fits_in_daylight(self, day: date, start: TimeOfDay, needed: Duration) -> bool:
        if not self.is_daylight(day, start):
            return False
        return start.minutes + needed.seconds // 60 <= self.sunset(day).minutes

    def _event(self, day: date, zenith: float, rising: bool) -> TimeOfDay:
        gamma = _fractional_year(day)
        equation = _equation_of_time(gamma)
        declination = _declination(gamma)
        latitude = math.radians(self.latitude)
        argument = math.cos(math.radians(zenith)) / (
            math.cos(latitude) * math.cos(declination)
        ) - math.tan(latitude) * math.tan(declination)
        if not -1 <= argument <= 1:
            raise DaylightError(
                f"the sun neither rises nor sets at {self.name} on {day.isoformat()}"
            )
        hour_angle = math.degrees(math.acos(argument))
        if not rising:
            hour_angle = -hour_angle
        utc_minutes = 720 - MINUTES_PER_DEGREE * (self.longitude + hour_angle) - equation
        local = utc_minutes + self.offset_minutes(day)
        return TimeOfDay(int(round(local)) % MINUTES_PER_DAY)


def in_summer_time(day: date) -> bool:
    """United States rules, second Sunday in March to first Sunday in November."""
    start = _nth_weekday(day.year, 3, weekday=6, nth=2)
    end = _nth_weekday(day.year, 11, weekday=6, nth=1)
    return start <= day < end


def _nth_weekday(year: int, month: int, weekday: int, nth: int) -> date:
    day = date(year, month, 1)
    day += timedelta(days=(weekday - day.weekday()) % 7)
    return day + timedelta(weeks=nth - 1)


def _fractional_year(day: date) -> float:
    day_of_year = day.timetuple().tm_yday
    days_in_year = 366 if _is_leap(day.year) else 365
    return 2 * math.pi / days_in_year * (day_of_year - 1 + 0.5)


def _is_leap(year: int) -> bool:
    return year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)


def _equation_of_time(gamma: float) -> float:
    return 229.18 * (
        0.000075
        + 0.001868 * math.cos(gamma)
        - 0.032077 * math.sin(gamma)
        - 0.014615 * math.cos(2 * gamma)
        - 0.040849 * math.sin(2 * gamma)
    )


def _declination(gamma: float) -> float:
    return (
        0.006918
        - 0.399912 * math.cos(gamma)
        + 0.070257 * math.sin(gamma)
        - 0.006758 * math.cos(2 * gamma)
        + 0.000907 * math.sin(2 * gamma)
        - 0.002697 * math.cos(3 * gamma)
        + 0.00148 * math.sin(3 * gamma)
    )
