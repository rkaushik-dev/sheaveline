"""Temperatures, held as whole tenths of a degree Fahrenheit.

The summit station reports in Fahrenheit to one place and the drive manuals
quote gearbox and hydraulic limits in Celsius, so both faces have to read back.
A temperature and a temperature difference are not the same thing, and the cold
weather rules add and subtract differences all day, so they are separate types
here. Adding two thermometer readings together is a mistake worth refusing.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, UnitMismatch

DECIDEGREES_PER_DEGREE: Final = 10
ABSOLUTE_ZERO_F: Final = Decimal("-459.67")

_TEMPERATURE = re.compile(
    r"""^\s*(?P<value>-?\d+(?:\.\d+)?)\s*
    (?:deg\s*)?(?P<unit>f|c|fahrenheit|celsius|centigrade)?\s*$""",
    re.VERBOSE | re.IGNORECASE,
)


class TemperatureDelta:
    """A difference between two readings, in tenths of a degree Fahrenheit."""

    __slots__ = ("_decidegrees",)

    def __init__(self, decidegrees_f: int) -> None:
        if not isinstance(decidegrees_f, int) or isinstance(decidegrees_f, bool):
            raise UnitMismatch("TemperatureDelta", type(decidegrees_f).__name__)
        self._decidegrees = decidegrees_f

    @property
    def decidegrees_f(self) -> int:
        return self._decidegrees

    @property
    def degrees_f(self) -> Decimal:
        return Decimal(self._decidegrees) / DECIDEGREES_PER_DEGREE

    @property
    def degrees_c(self) -> Decimal:
        """A span of Fahrenheit is five ninths as many Celsius, with no offset."""
        return self.degrees_f * 5 / 9

    @classmethod
    def from_degrees_f(cls, degrees: int | float | str | Decimal) -> TemperatureDelta:
        return cls(_round_half_up(Decimal(str(degrees)) * DECIDEGREES_PER_DEGREE))

    @classmethod
    def from_degrees_c(cls, degrees: int | float | str | Decimal) -> TemperatureDelta:
        return cls.from_degrees_f(Decimal(str(degrees)) * 9 / 5)

    def __repr__(self) -> str:
        return f"TemperatureDelta({self._decidegrees})"

    def __str__(self) -> str:
        return f"{_trim(self.degrees_f)} F"

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, TemperatureDelta):
            return NotImplemented
        return self._decidegrees == other._decidegrees

    def __hash__(self) -> int:
        return hash(("TemperatureDelta", self._decidegrees))

    def __lt__(self, other: TemperatureDelta) -> bool:
        return self._decidegrees < other._decidegrees

    def __le__(self, other: TemperatureDelta) -> bool:
        return self._decidegrees <= other._decidegrees

    def __gt__(self, other: TemperatureDelta) -> bool:
        return self._decidegrees > other._decidegrees

    def __ge__(self, other: TemperatureDelta) -> bool:
        return self._decidegrees >= other._decidegrees

    def __add__(self, other: TemperatureDelta) -> TemperatureDelta:
        if not isinstance(other, TemperatureDelta):
            return NotImplemented
        return TemperatureDelta(self._decidegrees + other._decidegrees)

    def __neg__(self) -> TemperatureDelta:
        return TemperatureDelta(-self._decidegrees)

    def __abs__(self) -> TemperatureDelta:
        return TemperatureDelta(abs(self._decidegrees))


class Temperature:
    """A thermometer reading in tenths of a degree Fahrenheit."""

    __slots__ = ("_decidegrees",)

    def __init__(self, decidegrees_f: int) -> None:
        if not isinstance(decidegrees_f, int) or isinstance(decidegrees_f, bool):
            raise UnitMismatch("Temperature", type(decidegrees_f).__name__)
        self._decidegrees = decidegrees_f

    @property
    def decidegrees_f(self) -> int:
        return self._decidegrees

    @property
    def degrees_f(self) -> Decimal:
        return Decimal(self._decidegrees) / DECIDEGREES_PER_DEGREE

    @property
    def degrees_c(self) -> Decimal:
        return (self.degrees_f - 32) * 5 / 9

    @classmethod
    def from_degrees_f(cls, degrees: int | float | str | Decimal) -> Temperature:
        return cls(_round_half_up(Decimal(str(degrees)) * DECIDEGREES_PER_DEGREE))

    @classmethod
    def from_degrees_c(cls, degrees: int | float | str | Decimal) -> Temperature:
        return cls.from_degrees_f(Decimal(str(degrees)) * 9 / 5 + 32)

    @classmethod
    def parse(cls, text: str) -> Temperature:
        """Read minus twelve, -12 F, 18F or -24 degC. A bare number is Fahrenheit."""
        match = _TEMPERATURE.match(text)
        if match is None:
            raise ParseError(text, "a temperature")
        unit = (match.group("unit") or "f").lower()
        value = match.group("value")
        if unit.startswith("c"):
            return cls.from_degrees_c(value)
        return cls.from_degrees_f(value)

    def plus(self, delta: TemperatureDelta) -> Temperature:
        return Temperature(self._decidegrees + delta.decidegrees_f)

    def minus(self, delta: TemperatureDelta) -> Temperature:
        return Temperature(self._decidegrees - delta.decidegrees_f)

    def is_freezing(self) -> bool:
        return self._decidegrees <= FREEZING.decidegrees_f

    def format_f(self, places: int = 1) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.degrees_f.quantize(quant)} F"

    def format_c(self, places: int = 1) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.degrees_c.quantize(quant)} C"

    def __repr__(self) -> str:
        return f"Temperature({self._decidegrees})"

    def __str__(self) -> str:
        return self.format_f()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Temperature):
            return NotImplemented
        return self._decidegrees == other._decidegrees

    def __hash__(self) -> int:
        return hash(("Temperature", self._decidegrees))

    def __lt__(self, other: Temperature) -> bool:
        return self._decidegrees < other._decidegrees

    def __le__(self, other: Temperature) -> bool:
        return self._decidegrees <= other._decidegrees

    def __gt__(self, other: Temperature) -> bool:
        return self._decidegrees > other._decidegrees

    def __ge__(self, other: Temperature) -> bool:
        return self._decidegrees >= other._decidegrees

    def __sub__(self, other: Temperature) -> TemperatureDelta:
        if not isinstance(other, Temperature):
            return NotImplemented
        return TemperatureDelta(self._decidegrees - other._decidegrees)

    def __add__(self, other: object) -> Temperature:
        raise UnitMismatch("Temperature", "Temperature")


FREEZING: Final = Temperature(320)


def _round_half_up(value: Decimal) -> int:
    if value < 0:
        return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def _trim(value: Decimal) -> str:
    text = f"{value.normalize():f}"
    return text if "." not in text else text.rstrip("0").rstrip(".")
