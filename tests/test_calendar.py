from datetime import date

import pytest

from hill import the_season, the_site
from sheaveline.measures.duration import Duration
from sheaveline.schedule.calendar import SeasonCalendar
from sheaveline.schedule.hours import HoursTable, OperatingWindow
from sheaveline.schedule.season import DayType, SeasonError

SOLSTICE = date(2024, 12, 21)
SPRING = date(2025, 4, 12)
MIDWEEK = OperatingWindow.parse("09:00", "16:00", "16:30")
HOLIDAY = OperatingWindow.parse("08:30", "16:30", "17:00")


def table():
    return HoursTable(default=MIDWEEK, by_day_type={DayType.HOLIDAY: HOLIDAY})


def calendar(clears_in=Duration.from_minutes(45)):
    return SeasonCalendar(the_season(), table(), the_site(), clears_in)


def test_a_machine_that_clears_instantly_has_not_been_drilled():
    with pytest.raises(SeasonError):
        SeasonCalendar(the_season(), table(), the_site(), Duration(0))


def test_a_closed_day_gets_no_window():
    made = calendar().day(date(2025, 6, 1))
    assert made.day_type is DayType.CLOSED
    assert not made.is_operating()
    assert not made.is_clipped()
    assert "closed" in str(made)


def test_a_long_spring_day_keeps_the_published_hours():
    made = calendar().day(SPRING)
    assert made.is_operating()
    assert made.window == made.published
    assert not made.is_clipped()
    assert made.lost().is_zero()


def test_the_solstice_loses_loading_time_to_the_light():
    made = calendar().day(SOLSTICE)
    assert made.is_operating()
    assert made.is_clipped()
    assert made.window.last_load < made.published.last_load
    assert not made.lost().is_zero()
    assert "clipped" in str(made)


def test_the_clipped_window_still_runs_forwards():
    made = calendar().day(SOLSTICE)
    assert made.window.first_load <= made.window.last_load <= made.window.sweep_by


def test_a_slower_evacuation_takes_more_of_the_short_days():
    quick = calendar(Duration.from_minutes(30)).day(SOLSTICE)
    slow = calendar(Duration.from_hours(3)).day(SOLSTICE)
    assert slow.window.last_load < quick.window.last_load
    assert slow.lost() > quick.lost()


def test_an_evacuation_longer_than_the_day_leaves_no_window_at_all():
    made = calendar(Duration.from_hours(20)).day(SOLSTICE)
    assert made.is_impossible()
    assert made.window is None
    assert "no light to load" in str(made)


def test_a_second_team_hands_the_loading_time_back():
    alone = calendar(Duration.from_hours(3))
    paired = alone.with_evacuation(Duration.from_minutes(90))
    assert paired.loading_time() > alone.loading_time()
    assert len(paired.clipped_days()) < len(alone.clipped_days())


def test_the_days_that_lose_light_are_listed_and_totalled():
    made = calendar(Duration.from_hours(2))
    clipped = made.clipped_days()
    assert clipped
    assert all(one.is_clipped() for one in clipped)
    assert made.lost_to_the_light().seconds == sum(one.lost().seconds for one in clipped)


def test_the_worst_day_is_the_one_that_loses_most():
    made = calendar(Duration.from_hours(2))
    worst = made.worst_day()
    assert worst is not None
    assert all(one.lost() <= worst.lost() for one in made.clipped_days())


def test_a_season_that_never_runs_short_has_no_worst_day():
    assert calendar(Duration.from_minutes(1)).worst_day() is None
    assert calendar(Duration.from_minutes(1)).lost_to_the_light().is_zero()


def test_every_operating_day_in_the_season_is_walked():
    made = calendar()
    assert len(made.operating_days()) == len(list(the_season().operating_days()))


def test_a_holiday_gets_the_holiday_hours_where_the_light_allows():
    season = the_season()
    holiday = next(one for one in season.days() if season.day_type(one) is DayType.HOLIDAY)
    made = SeasonCalendar(season, table(), the_site(), Duration.from_minutes(20)).day(holiday)
    assert made.published == HOLIDAY


def test_the_calendar_says_how_many_days_are_short_of_light():
    printed = str(calendar(Duration.from_hours(2)))
    assert "operating days" in printed
    assert "short of light" in printed


def test_no_day_is_clipped_past_its_own_first_load():
    for one in calendar(Duration.from_hours(4)).days():
        if one.window is None:
            continue
        assert one.window.last_load >= one.window.first_load
