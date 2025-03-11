from datetime import date, time

import pytest

from sheaveline.errors import ParseError, RangeError
from sheaveline.measures.duration import Duration
from sheaveline.schedule.hours import HoursTable, OperatingWindow, TimeOfDay
from sheaveline.schedule.season import DayType, HolidayPeriod, Season

SEASON = Season(
    label="2024/25",
    opens=date(2024, 11, 29),
    closes=date(2025, 4, 20),
    preview_from=date(2024, 11, 23),
    spring_from=date(2025, 3, 24),
    holidays=(HolidayPeriod("christmas", date(2024, 12, 21), date(2025, 1, 1)),),
    closures=frozenset({date(2025, 1, 14)}),
)

MIDWEEK = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm")
HOLIDAY = OperatingWindow.parse("8:30", "4:00 pm", "4:30 pm")
SPRING = OperatingWindow.parse("9:00", "3:00 pm", "3:30 pm")
TABLE = HoursTable(
    default=MIDWEEK,
    by_day_type={DayType.HOLIDAY: HOLIDAY, DayType.SPRING: SPRING},
)


def test_minutes_since_midnight_are_the_base_unit():
    assert TimeOfDay.at(9).minutes == 540
    assert TimeOfDay.at(15, 45).minutes == 945
    assert TimeOfDay(945).hour == 15
    assert TimeOfDay(945).minute == 45


@pytest.mark.parametrize(
    ("text", "minutes"),
    [
        ("9:00", 540),
        ("08:30", 510),
        ("15:45", 945),
        ("4:15 pm", 975),
        ("4:15PM", 975),
        ("12:00 am", 0),
        ("12:30 pm", 750),
    ],
)
def test_parse_reads_the_shapes_a_sign_uses(text, minutes):
    assert TimeOfDay.parse(text).minutes == minutes


@pytest.mark.parametrize("text", ["", "noon", "24:00", "9:60", "0:15 pm", "930"])
def test_parse_refuses_what_is_not_a_time(text):
    with pytest.raises(ParseError):
        TimeOfDay.parse(text)


def test_a_time_of_day_stays_inside_the_day():
    with pytest.raises(RangeError):
        TimeOfDay(-1)
    with pytest.raises(RangeError):
        TimeOfDay(1440)
    with pytest.raises(RangeError):
        TimeOfDay(9.5)


def test_adding_a_duration_moves_along_the_same_day():
    assert TimeOfDay.at(9).plus(Duration.from_minutes(45)) == TimeOfDay.at(9, 45)
    assert TimeOfDay.at(16).minus(Duration.from_minutes(30)) == TimeOfDay.at(15, 30)


def test_a_window_that_runs_past_midnight_is_refused():
    with pytest.raises(RangeError):
        TimeOfDay.at(23, 30).plus(Duration.from_hours(1))
    with pytest.raises(RangeError):
        TimeOfDay.at(0, 30).minus(Duration.from_hours(1))


def test_the_interval_between_two_times():
    assert TimeOfDay.at(9).until(TimeOfDay.at(15, 45)) == Duration.from_minutes(405)
    with pytest.raises(RangeError):
        TimeOfDay.at(15).until(TimeOfDay.at(9))


def test_conversions_to_and_from_the_standard_library():
    assert TimeOfDay.from_time(time(15, 45)) == TimeOfDay.at(15, 45)
    assert TimeOfDay.at(15, 45).to_time() == time(15, 45)


def test_ordering_and_equality():
    early = TimeOfDay.at(9)
    late = TimeOfDay.at(16)
    assert early < late
    assert late > early
    assert early <= TimeOfDay.at(9)
    assert early >= TimeOfDay.at(9)
    assert early != late
    assert early != 540
    assert len({early, TimeOfDay(540)}) == 1


def test_formatting():
    assert TimeOfDay.at(9, 5).format_clock() == "9:05"
    assert TimeOfDay.at(15, 45).format_meridiem() == "3:45 pm"
    assert TimeOfDay.at(0, 30).format_meridiem() == "12:30 am"
    assert TimeOfDay.at(12, 30).format_meridiem() == "12:30 pm"
    assert repr(TimeOfDay.at(9)) == "TimeOfDay(540)"
    assert str(TimeOfDay.at(9)) == "9:00"


def test_a_window_knows_when_it_is_loading_and_when_it_is_running():
    assert MIDWEEK.is_loading(TimeOfDay.at(12))
    assert not MIDWEEK.is_loading(TimeOfDay.at(16))
    assert MIDWEEK.is_running(TimeOfDay.at(16))
    assert not MIDWEEK.is_running(TimeOfDay.at(16, 30))
    assert not MIDWEEK.is_loading(TimeOfDay.at(8, 30))


def test_a_window_reports_its_two_spans():
    assert MIDWEEK.loading_time() == Duration.from_minutes(405)
    assert MIDWEEK.sweep_allowance() == Duration.from_minutes(30)


def test_a_window_has_to_run_forwards():
    with pytest.raises(RangeError):
        OperatingWindow.parse("15:00", "9:00", "16:00")
    with pytest.raises(RangeError):
        OperatingWindow.parse("9:00", "15:45", "15:30")


def test_the_table_picks_the_window_for_the_day():
    assert TABLE.window_on(SEASON, date(2025, 1, 15)) == MIDWEEK
    assert TABLE.window_on(SEASON, date(2024, 12, 24)) == HOLIDAY
    assert TABLE.window_on(SEASON, date(2025, 4, 1)) == SPRING


def test_a_weekend_falls_back_to_the_default_when_it_is_not_listed():
    assert TABLE.window_on(SEASON, date(2025, 1, 11)) == MIDWEEK


def test_a_closed_day_has_no_window():
    assert TABLE.window_on(SEASON, date(2025, 1, 14)) is None
    assert TABLE.window_on(SEASON, date(2025, 6, 1)) is None
    assert TABLE.window_for_type(DayType.CLOSED) is None


def test_a_preview_day_still_gets_a_window():
    assert TABLE.window_on(SEASON, date(2024, 11, 25)) == MIDWEEK


def test_a_window_prints_all_three_times():
    assert str(MIDWEEK) == "9:00 to 15:45, swept by 16:15"
