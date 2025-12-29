import pytest
from pydantic import BaseModel, ValidationError

from sheaveline.api.schemas.measures import (
    AngleText,
    BearingText,
    ClockText,
    DurationText,
    FilingNumberText,
    LengthFeet,
    LengthInches,
    LiftCodeText,
    LoadText,
    RopeSerialText,
    SpeedText,
    TemperatureText,
    TorqueText,
    TowerRefText,
    WindText,
)
from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.torque import Torque
from sheaveline.plant.identity import FilingNumber, LiftCode, RopeSerial, TowerRef
from sheaveline.schedule.hours import TimeOfDay


class Everything(BaseModel):
    station: LengthFeet
    diameter: LengthInches
    tension: LoadText
    speed: SpeedText
    wind: WindText
    ride: DurationText
    deviation: AngleText
    axis: BearingText
    reading: TemperatureText
    torque: TorqueText
    sweep: ClockText
    lift: LiftCodeText
    tower: TowerRefText
    rope: RopeSerialText
    filing: FilingNumberText


def sample() -> dict[str, str]:
    return {
        "station": "4800 ft",
        "diameter": "1.378 in",
        "tension": "34,500 lb",
        "speed": "1000 fpm",
        "wind": "24 mph",
        "ride": "8:30",
        "deviation": "1.75 deg",
        "axis": "72",
        "reading": "-12 F",
        "torque": "450 in lb",
        "sweep": "16:15",
        "lift": "JP",
        "tower": "JP-T4",
        "rope": "FAT-2019-04412",
        "filing": "UT-2025-0147",
    }


def test_every_field_parses_into_the_measure_it_names():
    read = Everything(**sample())
    assert read.station == Length.from_feet(4800)
    assert read.diameter == Length.from_inches("1.378")
    assert read.tension == Load.from_pounds(34500)
    assert read.speed == Speed.from_feet_per_minute(1000)
    assert read.wind == Speed.from_miles_per_hour(24)
    assert read.ride == Duration.from_hms(0, 8, 30)
    assert read.deviation == Angle.from_degrees("1.75")
    assert read.axis == Bearing.from_degrees(72)
    assert read.reading == Temperature.from_degrees_f(-12)
    assert read.torque == Torque.from_inch_pounds(450)
    assert read.sweep == TimeOfDay.at(16, 15)
    assert read.lift == LiftCode("JP")
    assert read.tower == TowerRef.parse("JP-T4")
    assert read.rope == RopeSerial.parse("FAT-2019-04412")
    assert read.filing == FilingNumber("UT", 2025, 147)


def test_a_measure_already_built_passes_straight_through():
    read = Everything(**{**sample(), "station": Length.from_feet(1200)})
    assert read.station == Length.from_feet(1200)


def test_the_json_comes_back_as_the_strings_a_person_writes():
    body = Everything(**sample()).model_dump()
    assert body["station"] == "4800.00 ft"
    assert body["diameter"] == '1.378"'
    assert body["tension"] == "34,500 lb"
    assert body["speed"] == "1000 fpm"
    assert body["wind"] == "24 mph"
    assert body["ride"] == "0:08:30"
    assert body["axis"] == "72 deg"
    assert body["sweep"] == "16:15"
    assert body["tower"] == "JP-T4"


def test_nothing_on_the_wire_is_ever_a_float():
    body = Everything(**sample()).model_dump()
    assert all(isinstance(value, str) for value in body.values())


def test_a_field_that_does_not_read_is_refused_rather_than_believed():
    with pytest.raises(ValidationError):
        Everything(**{**sample(), "station": "quite a long way"})
    with pytest.raises(ValidationError):
        Everything(**{**sample(), "speed": "very fast"})
    with pytest.raises(ValidationError):
        Everything(**{**sample(), "lift": "1A"})


def test_a_length_sent_in_inches_where_feet_were_wanted_still_reads():
    """A bare number is inches, and the field says which face it prints."""
    read = Everything(**{**sample(), "station": "148 ft"})
    assert read.station == Length.from_feet(148)


def test_the_schema_says_the_fields_are_strings():
    schema = Everything.model_json_schema()
    for name in ("station", "tension", "speed", "tower"):
        assert schema["properties"][name]["type"] == "string"


def test_the_schema_carries_an_example_a_person_can_copy():
    schema = Everything.model_json_schema()
    assert schema["properties"]["station"]["examples"] == ["4800.00 ft"]
    assert schema["properties"]["rope"]["examples"] == ["FAT-2019-04412"]


def test_the_round_trip_through_json_comes_back_the_same():
    before = Everything(**sample())
    after = Everything.model_validate_json(before.model_dump_json())
    assert after.station == before.station
    assert after.tension == before.tension
    assert after.rope == before.rope
    assert after.reading == before.reading


def test_a_wind_and_a_rope_speed_are_the_same_type_with_different_faces():
    read = Everything(**{**sample(), "wind": "1000 fpm"})
    assert read.wind == Speed.from_feet_per_minute(1000)
    assert read.model_dump()["wind"] == "11 mph"
