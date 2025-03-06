from datetime import date

import pytest

from sheaveline.schedule.season import (
    DayType,
    HolidayPeriod,
    Season,
    SeasonError,
)


def build_season(**overrides):
    fields = {
        "label": "2024/25",
        "opens": date(2024, 11, 29),
        "closes": date(2025, 4, 20),
        "preview_from": date(2024, 11, 23),
        "spring_from": date(2025, 3, 24),
        "holidays": (
            HolidayPeriod("christmas", date(2024, 12, 21), date(2025, 1, 1)),
            HolidayPeriod("presidents", date(2025, 2, 15), date(2025, 2, 23)),
        ),
        "closures": frozenset({date(2025, 1, 14)}),
    }
    fields.update(overrides)
    return Season(**fields)


def test_a_day_outside_the_season_is_closed():
    season = build_season()
    assert season.day_type(date(2024, 11, 1)) is DayType.CLOSED
    assert season.day_type(date(2025, 5, 1)) is DayType.CLOSED
    assert not season.covers(date(2025, 5, 1))


def test_a_scheduled_closure_beats_everything_else():
    season = build_season()
    assert season.day_type(date(2025, 1, 14)) is DayType.CLOSED
    assert not season.is_operating(date(2025, 1, 14))


def test_preview_days_run_but_are_not_public():
    season = build_season()
    day = date(2024, 11, 25)
    assert season.day_type(day) is DayType.PREVIEW
    assert season.is_operating(day)
    assert not season.is_public(day)


def test_a_holiday_beats_the_weekend():
    season = build_season()
    saturday = date(2024, 12, 28)
    assert saturday.weekday() == 5
    assert season.day_type(saturday) is DayType.HOLIDAY


def test_a_holiday_beats_spring():
    season = build_season(spring_from=date(2025, 2, 1))
    assert season.day_type(date(2025, 2, 18)) is DayType.HOLIDAY


def test_spring_beats_the_weekend():
    season = build_season()
    saturday = date(2025, 3, 29)
    assert saturday.weekday() == 5
    assert season.day_type(saturday) is DayType.SPRING


def test_an_ordinary_weekend_and_an_ordinary_weekday():
    season = build_season()
    assert season.day_type(date(2025, 1, 11)) is DayType.WEEKEND
    assert season.day_type(date(2025, 1, 15)) is DayType.MIDWEEK


def test_the_first_and_last_day_of_the_season_are_in_it():
    season = build_season()
    assert season.first_day() == date(2024, 11, 23)
    assert season.day_type(date(2024, 11, 23)) is DayType.PREVIEW
    assert season.day_type(date(2024, 11, 29)) is not DayType.CLOSED
    assert season.day_type(date(2025, 4, 20)) is DayType.SPRING


def test_without_a_preview_the_season_starts_on_opening_day():
    season = build_season(preview_from=None)
    assert season.first_day() == date(2024, 11, 29)
    assert season.day_type(date(2024, 11, 25)) is DayType.CLOSED


def test_holidays_answer_which_one_a_day_falls_in():
    season = build_season()
    holiday = season.holiday_on(date(2024, 12, 24))
    assert holiday is not None
    assert holiday.name == "christmas"
    assert holiday.days() == 12
    assert season.holiday_on(date(2025, 1, 20)) is None


def test_the_operating_day_count_skips_closures():
    season = build_season()
    assert season.length() == 148
    assert len(list(season.days())) == 149


def test_day_numbers_count_operating_days_from_one():
    season = build_season()
    assert season.day_number(date(2024, 11, 23)) == 1
    assert season.day_number(date(2024, 11, 29)) == 7
    assert season.day_number(date(2025, 4, 20)) == season.length()


def test_a_closed_day_has_no_number():
    season = build_season()
    with pytest.raises(SeasonError):
        season.day_number(date(2025, 1, 14))


def test_days_remaining_counts_what_is_still_to_come():
    season = build_season()
    assert season.days_remaining(date(2025, 4, 20)) == 0
    assert season.days_remaining(date(2024, 11, 23)) == season.length() - 1


def test_public_days_are_the_operating_days_less_the_preview():
    season = build_season()
    assert len(list(season.public_days())) == season.length() - 6


def test_a_season_that_closes_before_it_opens_is_refused():
    with pytest.raises(SeasonError):
        build_season(opens=date(2025, 4, 1), closes=date(2025, 3, 1))


def test_a_preview_after_opening_day_is_refused():
    with pytest.raises(SeasonError):
        build_season(preview_from=date(2024, 12, 5))


def test_spring_outside_the_season_is_refused():
    with pytest.raises(SeasonError):
        build_season(spring_from=date(2025, 5, 5))


def test_a_holiday_outside_the_season_is_refused():
    with pytest.raises(SeasonError):
        build_season(holidays=(HolidayPeriod("july", date(2025, 7, 1), date(2025, 7, 4)),))


def test_a_holiday_that_ends_before_it_starts_is_refused():
    with pytest.raises(SeasonError):
        HolidayPeriod("backwards", date(2025, 1, 5), date(2025, 1, 1))


def test_day_types_know_whether_the_hill_is_open():
    assert not DayType.CLOSED.is_operating
    assert DayType.PREVIEW.is_operating
    assert not DayType.PREVIEW.is_public
    assert DayType.MIDWEEK.is_public


def test_a_season_prints_its_dates():
    assert str(build_season()) == "2024/25 (2024-11-29 to 2025-04-20)"
