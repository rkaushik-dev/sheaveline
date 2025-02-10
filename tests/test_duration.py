from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, RangeError
from sheaveline.measures.duration import ZERO_DURATION, Duration


def test_seconds_are_the_base_unit():
    assert Duration.from_minutes(1).seconds == 60
    assert Duration.from_hours(2).seconds == 7200
    assert Duration.from_hms(1, 8, 30).seconds == 4110


def test_faces_read_back():
    ride = Duration.from_hms(0, 8, 30)
    assert ride.minutes == Decimal("8.5")
    assert Duration.from_hours(3).hours == 3


def test_a_duration_is_never_negative():
    with pytest.raises(RangeError):
        Duration(-1)
    with pytest.raises(RangeError):
        Duration.from_minutes(5) - Duration.from_minutes(9)


def test_the_constructor_wants_whole_seconds():
    with pytest.raises(RangeError):
        Duration(1.5)
    with pytest.raises(RangeError):
        Duration(True)
    with pytest.raises(RangeError):
        Duration.from_minutes("0.001")


@pytest.mark.parametrize(
    ("text", "seconds"),
    [
        ("8:30", 510),
        ("0:45", 45),
        ("1:08:30", 4110),
        ("12:00:00", 43200),
        ("90 s", 90),
        ("90s", 90),
        ("12 min", 720),
        ("2.5 hours", 9000),
        ("  3 hr ", 10800),
    ],
)
def test_parse_reads_the_shapes_a_log_uses(text, seconds):
    assert Duration.parse(text).seconds == seconds


@pytest.mark.parametrize("text", ["", "half an hour", "8:75", "1:75:00", "30", "-5 min"])
def test_parse_refuses_what_is_not_a_duration(text):
    with pytest.raises(ParseError):
        Duration.parse(text)


def test_clock_face_is_minutes_and_seconds():
    assert Duration.from_hms(0, 8, 30).format_clock() == "8:30"
    assert Duration.from_hms(1, 8, 30).format_clock() == "68:30"
    assert Duration.from_seconds(7).format_clock() == "0:07"


def test_hms_face_carries_the_hours():
    assert Duration.from_hms(1, 8, 30).format_hms() == "1:08:30"
    assert str(Duration.from_seconds(45)) == "0:00:45"
    assert Duration.from_hms(2, 3, 4).split_hms() == (2, 3, 4)


def test_arithmetic_and_ordering():
    short = Duration.from_minutes(8)
    long = Duration.from_minutes(12)
    assert short + long == Duration.from_minutes(20)
    assert long - short == Duration.from_minutes(4)
    assert short < long
    assert long > short
    assert short <= Duration.from_minutes(8)
    assert short >= Duration.from_minutes(8)
    assert short != long
    assert len({short, Duration.from_seconds(480)}) == 1


def test_a_duration_never_equals_a_bare_number():
    assert Duration.from_minutes(1) != 60


def test_scaling_rounds_to_the_nearest_second():
    assert Duration.from_minutes(10).scaled("1.5") == Duration.from_minutes(15)
    assert Duration.from_seconds(7).scaled("0.5") == Duration.from_seconds(4)


def test_zero_answers_for_itself():
    assert ZERO_DURATION.is_zero()
    assert not Duration.from_seconds(1).is_zero()
    assert repr(ZERO_DURATION) == "Duration.from_seconds(0)"
