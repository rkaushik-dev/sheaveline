from datetime import date

import pytest

from sheaveline.errors import RangeError
from sheaveline.measures.duration import Duration
from sheaveline.schedule.daylight import (
    DaylightError,
    Site,
    in_summer_time,
)
from sheaveline.schedule.hours import TimeOfDay

# The summit terminal, forty one degrees north in the Wasatch.
SUMMIT = Site("summit", 41.216, -111.857, -420)
POLE = Site("pole", 78.0, 15.0, 60, observes_summer_time=False)


@pytest.mark.parametrize(
    ("day", "sunrise", "sunset"),
    [
        (date(2024, 12, 21), "7:50", "17:01"),
        (date(2025, 1, 15), "7:51", "17:22"),
        (date(2025, 3, 20), "7:32", "19:39"),
        (date(2025, 4, 20), "6:41", "20:12"),
        (date(2025, 6, 21), "5:54", "21:03"),
    ],
)
def test_sunrise_and_sunset_match_the_almanac(day, sunrise, sunset):
    assert SUMMIT.sunrise(day) == TimeOfDay.parse(sunrise)
    assert SUMMIT.sunset(day) == TimeOfDay.parse(sunset)


def test_the_shortest_day_of_the_season_is_around_the_solstice():
    shortest = min(
        (SUMMIT.daylight(date(2024, 12, day)).seconds for day in range(18, 26)),
    )
    assert SUMMIT.daylight(date(2024, 12, 21)).seconds == shortest


def test_civil_twilight_brackets_the_sun():
    day = date(2025, 1, 15)
    assert SUMMIT.civil_dawn(day) < SUMMIT.sunrise(day)
    assert SUMMIT.civil_dusk(day) > SUMMIT.sunset(day)
    assert SUMMIT.civil_dawn(day) == TimeOfDay.parse("7:20")
    assert SUMMIT.civil_dusk(day) == TimeOfDay.parse("17:52")


def test_daylight_is_the_span_between_the_two():
    assert SUMMIT.daylight(date(2025, 1, 15)) == Duration.from_hms(9, 31)
    assert SUMMIT.daylight(date(2025, 6, 21)) > SUMMIT.daylight(date(2024, 12, 21))


def test_the_clock_moves_in_march_and_back_in_november():
    assert not in_summer_time(date(2025, 3, 8))
    assert in_summer_time(date(2025, 3, 9))
    assert in_summer_time(date(2025, 11, 1))
    assert not in_summer_time(date(2025, 11, 2))
    assert not in_summer_time(date(2025, 1, 1))


def test_summer_time_shifts_the_reported_times():
    standard = Site("standard", 41.216, -111.857, -420, observes_summer_time=False)
    day = date(2025, 4, 20)
    assert SUMMIT.sunrise(day).minutes - standard.sunrise(day).minutes == 60


def test_a_site_reports_the_offset_in_force():
    assert SUMMIT.offset_minutes(date(2025, 1, 15)) == -420
    assert SUMMIT.offset_minutes(date(2025, 4, 20)) == -360


def test_whether_a_moment_is_in_the_light():
    day = date(2025, 1, 15)
    assert SUMMIT.is_daylight(day, TimeOfDay.at(12))
    assert not SUMMIT.is_daylight(day, TimeOfDay.at(7))
    assert not SUMMIT.is_daylight(day, TimeOfDay.at(18))


def test_the_latest_start_leaves_room_to_finish_before_dark():
    day = date(2025, 1, 15)
    assert SUMMIT.latest_start(day, Duration.from_hours(3)) == TimeOfDay.at(14, 22)
    assert SUMMIT.latest_start(day, Duration.from_minutes(0)) == SUMMIT.sunset(day)


def test_a_job_longer_than_the_day_has_no_start():
    with pytest.raises(DaylightError):
        SUMMIT.latest_start(date(2024, 12, 21), Duration.from_hours(20))


def test_whether_a_job_fits_from_a_given_start():
    day = date(2025, 1, 15)
    assert SUMMIT.fits_in_daylight(day, TimeOfDay.at(14), Duration.from_hours(3))
    assert not SUMMIT.fits_in_daylight(day, TimeOfDay.at(15), Duration.from_hours(3))
    assert not SUMMIT.fits_in_daylight(day, TimeOfDay.at(6), Duration.from_hours(1))


def test_a_place_where_the_sun_does_not_set_is_refused_rather_than_guessed():
    with pytest.raises(DaylightError):
        POLE.sunrise(date(2025, 6, 21))
    with pytest.raises(DaylightError):
        POLE.sunset(date(2024, 12, 21))


def test_a_site_checks_its_own_coordinates():
    with pytest.raises(RangeError):
        Site("bad", 95.0, 0.0, 0)
    with pytest.raises(RangeError):
        Site("bad", 0.0, 200.0, 0)


def test_a_leap_year_does_not_shift_the_solstice_by_a_day():
    assert SUMMIT.sunset(date(2024, 12, 21)) == TimeOfDay.parse("17:01")
    assert SUMMIT.sunset(date(2023, 12, 21)) == TimeOfDay.parse("17:01")
