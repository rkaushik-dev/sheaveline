"""Angles, held as whole thousandths of a degree, and compass bearings.

Three different questions on this hill are the same quantity. The line has a
slope, written as a percent grade on the profile drawing and as degrees on the
tower schedule. A sheave assembly has a deviation angle, which is how far the
rope bends over it and is written to two places. The weather station reports
wind direction as a compass bearing, and the hold rules care about how that
bearing sits against the line.
"""

from __future__ import annotations

import math
import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, RangeError, UnitMismatch
from sheaveline.measures.length import Length

MILLIDEGREES_PER_DEGREE: Final = 1000
FULL_CIRCLE: Final = 360 * MILLIDEGREES_PER_DEGREE

COMPASS_POINTS: Final[dict[str, int]] = {
    "N": 0,
    "NNE": 22_500,
    "NE": 45_000,
    "ENE": 67_500,
    "E": 90_000,
    "ESE": 112_500,
    "SE": 135_000,
    "SSE": 157_500,
    "S": 180_000,
    "SSW": 202_500,
    "SW": 225_000,
    "WSW": 247_500,
    "W": 270_000,
    "WNW": 292_500,
    "NW": 315_000,
    "NNW": 337_500,
}

_DEGREES = re.compile(
    r"^\s*(?P<value>-?\d+(?:\.\d+)?)\s*(?:deg|degs|degree|degrees|d)?\s*$",
    re.IGNORECASE,
)
_GRADE = re.compile(r"^\s*(?P<value>-?\d+(?:\.\d+)?)\s*%\s*$")


class Angle:
    """A signed angle in whole millidegrees."""

    __slots__ = ("_millidegrees",)

    def __init__(self, millidegrees: int) -> None:
        if not isinstance(millidegrees, int) or isinstance(millidegrees, bool):
            raise UnitMismatch("Angle", type(millidegrees).__name__)
        self._millidegrees = millidegrees

    @property
    def millidegrees(self) -> int:
        return self._millidegrees

    @property
    def degrees(self) -> Decimal:
        return Decimal(self._millidegrees) / MILLIDEGREES_PER_DEGREE

    @property
    def radians(self) -> float:
        return math.radians(float(self.degrees))

    @property
    def percent_grade(self) -> Decimal:
        """Rise over run as a percentage, which is how a profile drawing reads."""
        return _quantize(Decimal(str(math.tan(self.radians) * 100)), 3)

    @classmethod
    def from_millidegrees(cls, millidegrees: int) -> Angle:
        return cls(millidegrees)

    @classmethod
    def from_degrees(cls, degrees: int | float | str | Decimal) -> Angle:
        return cls(_round_half_up(Decimal(str(degrees)) * MILLIDEGREES_PER_DEGREE))

    @classmethod
    def from_radians(cls, radians: float) -> Angle:
        return cls.from_degrees(Decimal(str(math.degrees(radians))))

    @classmethod
    def from_percent_grade(cls, percent: int | float | str | Decimal) -> Angle:
        return cls.from_radians(math.atan(float(Decimal(str(percent)) / 100)))

    @classmethod
    def from_rise_run(cls, rise: Length, run: Length) -> Angle:
        """The slope of a span, taken off the two numbers a survey gives."""
        if run.mils == 0:
            raise RangeError("run", run, "nonzero", "nonzero")
        return cls.from_radians(math.atan2(rise.mils, run.mils))

    @classmethod
    def parse(cls, text: str) -> Angle:
        grade = _GRADE.match(text)
        if grade is not None:
            return cls.from_percent_grade(grade.group("value"))
        degrees = _DEGREES.match(text)
        if degrees is None:
            raise ParseError(text, "an angle")
        return cls.from_degrees(degrees.group("value"))

    def format_degrees(self, places: int = 2) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.degrees.quantize(quant)} deg"

    def format_grade(self, places: int = 1) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.percent_grade.quantize(quant)}%"

    def __repr__(self) -> str:
        return f"Angle.from_millidegrees({self._millidegrees})"

    def __str__(self) -> str:
        return self.format_degrees()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Angle):
            return NotImplemented
        return self._millidegrees == other._millidegrees

    def __hash__(self) -> int:
        return hash(("Angle", self._millidegrees))

    def __lt__(self, other: Angle) -> bool:
        return self._millidegrees < other._millidegrees

    def __le__(self, other: Angle) -> bool:
        return self._millidegrees <= other._millidegrees

    def __gt__(self, other: Angle) -> bool:
        return self._millidegrees > other._millidegrees

    def __ge__(self, other: Angle) -> bool:
        return self._millidegrees >= other._millidegrees

    def __add__(self, other: Angle) -> Angle:
        if not isinstance(other, Angle):
            return NotImplemented
        return Angle(self._millidegrees + other._millidegrees)

    def __sub__(self, other: Angle) -> Angle:
        if not isinstance(other, Angle):
            return NotImplemented
        return Angle(self._millidegrees - other._millidegrees)

    def __neg__(self) -> Angle:
        return Angle(-self._millidegrees)

    def __abs__(self) -> Angle:
        return Angle(abs(self._millidegrees))

    def is_uphill(self) -> bool:
        return self._millidegrees > 0

    def is_flat(self) -> bool:
        return self._millidegrees == 0


class Bearing:
    """A compass bearing in whole millidegrees, always inside one circle."""

    __slots__ = ("_millidegrees",)

    def __init__(self, millidegrees: int) -> None:
        if not isinstance(millidegrees, int) or isinstance(millidegrees, bool):
            raise UnitMismatch("Bearing", type(millidegrees).__name__)
        self._millidegrees = millidegrees % FULL_CIRCLE

    @property
    def millidegrees(self) -> int:
        return self._millidegrees

    @property
    def degrees(self) -> Decimal:
        return Decimal(self._millidegrees) / MILLIDEGREES_PER_DEGREE

    @property
    def radians(self) -> float:
        return math.radians(float(self.degrees))

    @classmethod
    def from_degrees(cls, degrees: int | float | str | Decimal) -> Bearing:
        return cls(_round_half_up(Decimal(str(degrees)) * MILLIDEGREES_PER_DEGREE))

    @classmethod
    def from_point(cls, point: str) -> Bearing:
        key = point.strip().upper()
        if key not in COMPASS_POINTS:
            raise ParseError(point, "a compass point")
        return cls(COMPASS_POINTS[key])

    @classmethod
    def parse(cls, text: str) -> Bearing:
        stripped = text.strip()
        if stripped.upper() in COMPASS_POINTS:
            return cls.from_point(stripped)
        match = _DEGREES.match(stripped)
        if match is None:
            raise ParseError(text, "a bearing")
        return cls.from_degrees(match.group("value"))

    def nearest_point(self) -> str:
        """The compass point the station would have written down."""
        step = FULL_CIRCLE // len(COMPASS_POINTS)
        index = ((self._millidegrees + step // 2) % FULL_CIRCLE) // step
        return list(COMPASS_POINTS)[index]

    def reciprocal(self) -> Bearing:
        return Bearing(self._millidegrees + FULL_CIRCLE // 2)

    def difference(self, other: Bearing) -> Angle:
        """The shortest turn from this bearing to the other, signed clockwise."""
        raw = (other._millidegrees - self._millidegrees) % FULL_CIRCLE
        if raw > FULL_CIRCLE // 2:
            raw -= FULL_CIRCLE
        return Angle(raw)

    def separation(self, other: Bearing) -> Angle:
        """How far apart two bearings are, ignoring which way round."""
        return abs(self.difference(other))

    def format_degrees(self, places: int = 0) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.degrees.quantize(quant)} deg"

    def __repr__(self) -> str:
        return f"Bearing({self._millidegrees})"

    def __str__(self) -> str:
        return f"{self.format_degrees()} ({self.nearest_point()})"

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Bearing):
            return NotImplemented
        return self._millidegrees == other._millidegrees

    def __hash__(self) -> int:
        return hash(("Bearing", self._millidegrees))


ZERO_ANGLE: Final = Angle(0)


def _round_half_up(value: Decimal) -> int:
    if value < 0:
        return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def _quantize(value: Decimal, places: int) -> Decimal:
    return value.quantize(Decimal(1).scaleb(-places))
