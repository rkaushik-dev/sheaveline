from decimal import Decimal

import pytest

from sheaveline.errors import ParseError, RangeError
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import STOPPED, Speed


def test_feet_per_minute_is_the_face_the_board_asks_for():
    assert Speed.from_feet_per_minute(500).feet_per_minute == 500
    assert Speed.from_feet_per_minute(1000).mils_per_minute == 12_000_000


def test_metres_per_second_is_the_face_the_manual_uses():
    quad = Speed.from_metres_per_second(5)
    assert quad.feet_per_minute.quantize(Decimal("0.1")) == Decimal("984.3")
    assert quad.metres_per_second.quantize(Decimal("0.001")) == Decimal("5.000")


def test_a_round_trip_through_either_face_comes_back():
    for fpm in (220, 450, 500, 1000):
        speed = Speed.from_feet_per_minute(fpm)
        assert Speed.from_metres_per_second(speed.metres_per_second) == speed


@pytest.mark.parametrize(
    ("text", "fpm"),
    [
        ("500 fpm", 500),
        ("500fpm", 500),
        ("450 ft/min", 450),
        ("220 feet per minute", 220),
    ],
)
def test_parse_reads_the_customary_face(text, fpm):
    assert Speed.parse(text).feet_per_minute == fpm


def test_parse_reads_the_metric_face():
    assert Speed.parse("2.5 m/s") == Speed.from_metres_per_second("2.5")
    assert Speed.parse("2.5 metres per second") == Speed.from_metres_per_second("2.5")
    assert Speed.parse("2.5mps") == Speed.from_metres_per_second("2.5")


@pytest.mark.parametrize("text", ["", "fast", "500", "500 mph", "5 m/min"])
def test_parse_refuses_what_is_not_a_speed(text):
    with pytest.raises(ParseError):
        Speed.parse(text)


def test_travel_answers_how_far_the_rope_went():
    speed = Speed.from_feet_per_minute(500)
    assert speed.travel(Duration.from_minutes(2)) == Length.from_feet(1000)
    assert speed.travel(Duration.from_seconds(30)) == Length.from_feet(250)


def test_time_to_cover_answers_the_ride_time():
    speed = Speed.from_feet_per_minute(500)
    assert speed.time_to_cover(Length.from_feet(4250)) == Duration.from_seconds(510)


def test_time_to_cover_refuses_a_stopped_rope():
    with pytest.raises(RangeError):
        STOPPED.time_to_cover(Length.from_feet(100))


def test_travel_and_time_to_cover_agree():
    speed = Speed.from_feet_per_minute(450)
    distance = Length.from_feet(3000)
    elapsed = speed.time_to_cover(distance)
    assert abs(speed.travel(elapsed).mils - distance.mils) < 12_000


def test_a_speed_is_never_negative():
    with pytest.raises(RangeError):
        Speed(-1)
    with pytest.raises(RangeError):
        Speed.from_feet_per_minute(-100)
    with pytest.raises(RangeError):
        Speed(4.5)


def test_ordering_and_equality():
    slow = Speed.from_feet_per_minute(220)
    fast = Speed.from_feet_per_minute(1000)
    assert slow < fast
    assert fast > slow
    assert slow <= Speed.from_feet_per_minute(220)
    assert slow >= Speed.from_feet_per_minute(220)
    assert slow != fast
    assert slow != 220
    assert len({slow, Speed.from_feet_per_minute(220)}) == 1


def test_scaling_is_how_a_reduced_speed_is_written():
    assert Speed.from_feet_per_minute(500).scaled("0.5") == Speed.from_feet_per_minute(250)


def test_formatting():
    assert Speed.from_feet_per_minute(500).format_fpm() == "500 fpm"
    assert str(Speed.from_feet_per_minute(1000)) == "1000 fpm"
    assert Speed.from_metres_per_second(5).format_mps() == "5.00 m/s"


def test_stopped_answers_for_itself():
    assert STOPPED.is_stopped()
    assert not Speed.from_feet_per_minute(1).is_stopped()
    assert STOPPED.travel(Duration.from_hours(1)).is_zero()
