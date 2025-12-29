"""Measures on the wire, as the strings a person writes on a form.

A length does not go over the wire as a number. It goes as "4800 ft" or
"1' 6\"", which is what the drawing says and what the tablet in the terminal
shows. That costs a parse either side and buys three things: nothing is ever a
float, a client that sends feet when the field wanted inches gets told rather
than silently believed, and the json is readable by the person the department
will eventually be arguing with.

Every one of these is the package's own parser wired into pydantic. There is no
second grammar here, which is the point.
"""

from __future__ import annotations

from typing import Annotated, Any

from pydantic import PlainSerializer, PlainValidator, WithJsonSchema

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.torque import Torque
from sheaveline.plant.identity import FilingNumber, LiftCode, RopeSerial, TowerRef
from sheaveline.schedule.hours import TimeOfDay


def _text(value: Any, kind: type, parse: Any) -> Any:
    if isinstance(value, kind):
        return value
    return parse(str(value))


def _length(value: Any) -> Length:
    return _text(value, Length, Length.parse)


def _load(value: Any) -> Load:
    return _text(value, Load, Load.parse)


def _speed(value: Any) -> Speed:
    return _text(value, Speed, Speed.parse)


def _duration(value: Any) -> Duration:
    return _text(value, Duration, Duration.parse)


def _angle(value: Any) -> Angle:
    return _text(value, Angle, Angle.parse)


def _bearing(value: Any) -> Bearing:
    return _text(value, Bearing, Bearing.parse)


def _temperature(value: Any) -> Temperature:
    return _text(value, Temperature, Temperature.parse)


def _torque(value: Any) -> Torque:
    return _text(value, Torque, Torque.parse)


def _clock(value: Any) -> TimeOfDay:
    return _text(value, TimeOfDay, TimeOfDay.parse)


def _lift_code(value: Any) -> LiftCode:
    return _text(value, LiftCode, LiftCode.parse)


def _tower_ref(value: Any) -> TowerRef:
    return _text(value, TowerRef, TowerRef.parse)


def _rope_serial(value: Any) -> RopeSerial:
    return _text(value, RopeSerial, RopeSerial.parse)


def _filing_number(value: Any) -> FilingNumber:
    return _text(value, FilingNumber, FilingNumber.parse)


def _wire(kind: type, validator: Any, render: Any, example: str) -> Any:
    return Annotated[
        kind,
        PlainValidator(validator),
        PlainSerializer(render, return_type=str),
        WithJsonSchema({"type": "string", "examples": [example]}),
    ]


LengthFeet = _wire(Length, _length, lambda one: f"{one.feet:.2f} ft", "4800.00 ft")
LengthInches = _wire(Length, _length, lambda one: one.format_inches(3), '1.378"')
LoadText = _wire(Load, _load, lambda one: one.format_pounds(), "34,500 lb")
SpeedText = _wire(Speed, _speed, lambda one: one.format_fpm(), "1000 fpm")
WindText = _wire(Speed, _speed, lambda one: one.format_mph(), "24 mph")
DurationText = _wire(Duration, _duration, lambda one: one.format_hms(), "0:08:30")
AngleText = _wire(Angle, _angle, lambda one: one.format_degrees(), "1.75 deg")
BearingText = _wire(Bearing, _bearing, lambda one: one.format_degrees(), "72 deg")
TemperatureText = _wire(Temperature, _temperature, lambda one: one.format_f(1), "-12.0 F")
TorqueText = _wire(Torque, _torque, lambda one: one.format_inch_pounds(), "450 in lb")
ClockText = _wire(TimeOfDay, _clock, lambda one: one.format_clock(), "15:45")
LiftCodeText = _wire(LiftCode, _lift_code, str, "JP")
TowerRefText = _wire(TowerRef, _tower_ref, str, "JP-T4")
RopeSerialText = _wire(RopeSerial, _rope_serial, str, "FAT-2019-04412")
FilingNumberText = _wire(FilingNumber, _filing_number, str, "UT-2025-0147")
