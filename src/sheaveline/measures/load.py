"""Loads, held as whole pounds, and the per foot weight a rope carries.

Everything the line pulls against is written in pounds. Rope tension and
counterweight are tens of thousands of them, a design load is one hundred and
seventy per passenger, and a haul rope is quoted by its weight per foot. The
manuals that came with the machinery use kilograms and daN, so both faces have
to read back.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, RangeError, UnitMismatch
from sheaveline.measures.length import MILS_PER_FOOT, Length

POUNDS_PER_KILOGRAM: Final = Decimal("2.20462262184877581")
POUNDS_PER_KIP: Final = 1000
POUNDS_PER_TON: Final = 2000
MILLIPOUNDS_PER_POUND: Final = 1000

_LOAD = re.compile(
    r"""^\s*(?P<value>-?[\d,]+(?:\.\d+)?)\s*
    (?P<unit>lb|lbs|lbf|pound|pounds|kip|kips|kg|kilogram|kilograms|ton|tons)\s*$""",
    re.VERBOSE | re.IGNORECASE,
)


class Load:
    """A weight or a line pull in whole pounds."""

    __slots__ = ("_pounds",)

    def __init__(self, pounds: int) -> None:
        if not isinstance(pounds, int) or isinstance(pounds, bool):
            raise UnitMismatch("Load", type(pounds).__name__)
        self._pounds = pounds

    @property
    def pounds(self) -> int:
        return self._pounds

    @property
    def kips(self) -> Decimal:
        return Decimal(self._pounds) / POUNDS_PER_KIP

    @property
    def kilograms(self) -> Decimal:
        return Decimal(self._pounds) / POUNDS_PER_KILOGRAM

    @classmethod
    def from_pounds(cls, pounds: int | float | str | Decimal) -> Load:
        return cls(_round_half_up(Decimal(str(pounds))))

    @classmethod
    def from_kips(cls, kips: int | float | str | Decimal) -> Load:
        return cls(_round_half_up(Decimal(str(kips)) * POUNDS_PER_KIP))

    @classmethod
    def from_kilograms(cls, kilograms: int | float | str | Decimal) -> Load:
        return cls(_round_half_up(Decimal(str(kilograms)) * POUNDS_PER_KILOGRAM))

    @classmethod
    def from_tons(cls, tons: int | float | str | Decimal) -> Load:
        return cls(_round_half_up(Decimal(str(tons)) * POUNDS_PER_TON))

    @classmethod
    def parse(cls, text: str) -> Load:
        match = _LOAD.match(text)
        if match is None:
            raise ParseError(text, "a load")
        value = match.group("value").replace(",", "")
        unit = match.group("unit").lower()
        if unit.startswith("kip"):
            return cls.from_kips(value)
        if unit.startswith("k"):
            return cls.from_kilograms(value)
        if unit.startswith("ton"):
            return cls.from_tons(value)
        return cls.from_pounds(value)

    def over(self, distance: Length) -> LoadPerFoot:
        """Spread this weight evenly over a length, the way a rope quote reads."""
        if distance.mils == 0:
            raise RangeError("distance", distance, "nonzero", "nonzero")
        per_foot = Decimal(self._pounds) * MILS_PER_FOOT / distance.mils
        return LoadPerFoot(_round_half_up(per_foot * MILLIPOUNDS_PER_POUND))

    def format_pounds(self) -> str:
        return f"{self._pounds:,} lb"

    def __repr__(self) -> str:
        return f"Load.from_pounds({self._pounds})"

    def __str__(self) -> str:
        return self.format_pounds()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Load):
            return NotImplemented
        return self._pounds == other._pounds

    def __hash__(self) -> int:
        return hash(("Load", self._pounds))

    def __lt__(self, other: Load) -> bool:
        return self._pounds < other._pounds

    def __le__(self, other: Load) -> bool:
        return self._pounds <= other._pounds

    def __gt__(self, other: Load) -> bool:
        return self._pounds > other._pounds

    def __ge__(self, other: Load) -> bool:
        return self._pounds >= other._pounds

    def __add__(self, other: Load) -> Load:
        if not isinstance(other, Load):
            return NotImplemented
        return Load(self._pounds + other._pounds)

    def __sub__(self, other: Load) -> Load:
        if not isinstance(other, Load):
            return NotImplemented
        return Load(self._pounds - other._pounds)

    def __neg__(self) -> Load:
        return Load(-self._pounds)

    def scaled(self, factor: int | float | str | Decimal) -> Load:
        return Load(_round_half_up(Decimal(self._pounds) * Decimal(str(factor))))

    def ratio(self, other: Load) -> Decimal:
        if other._pounds == 0:
            raise RangeError("divisor load", other, "nonzero", "nonzero")
        return Decimal(self._pounds) / Decimal(other._pounds)


class LoadPerFoot:
    """A weight per foot of rope, held as whole thousandths of a pound."""

    __slots__ = ("_millipounds",)

    def __init__(self, millipounds_per_foot: int) -> None:
        if not isinstance(millipounds_per_foot, int) or isinstance(millipounds_per_foot, bool):
            raise UnitMismatch("LoadPerFoot", type(millipounds_per_foot).__name__)
        if millipounds_per_foot < 0:
            raise RangeError("weight per foot", millipounds_per_foot, 0, "no limit")
        self._millipounds = millipounds_per_foot

    @property
    def millipounds_per_foot(self) -> int:
        return self._millipounds

    @property
    def pounds_per_foot(self) -> Decimal:
        return Decimal(self._millipounds) / MILLIPOUNDS_PER_POUND

    @classmethod
    def from_pounds_per_foot(cls, value: int | float | str | Decimal) -> LoadPerFoot:
        return cls(_round_half_up(Decimal(str(value)) * MILLIPOUNDS_PER_POUND))

    def times(self, distance: Length) -> Load:
        """What a run of rope that long weighs."""
        pounds = (
            Decimal(self._millipounds) * distance.mils / (MILLIPOUNDS_PER_POUND * MILS_PER_FOOT)
        )
        return Load(_round_half_up(pounds))

    def __repr__(self) -> str:
        return f"LoadPerFoot({self._millipounds})"

    def __str__(self) -> str:
        return f"{_trim(self.pounds_per_foot)} lb/ft"

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, LoadPerFoot):
            return NotImplemented
        return self._millipounds == other._millipounds

    def __hash__(self) -> int:
        return hash(("LoadPerFoot", self._millipounds))

    def __lt__(self, other: LoadPerFoot) -> bool:
        return self._millipounds < other._millipounds

    def __gt__(self, other: LoadPerFoot) -> bool:
        return self._millipounds > other._millipounds


ZERO_LOAD: Final = Load(0)
PASSENGER_DESIGN_LOAD: Final = Load(170)


def _round_half_up(value: Decimal) -> int:
    if value < 0:
        return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def _trim(value: Decimal) -> str:
    text = f"{value.normalize():f}"
    return text if "." not in text else text.rstrip("0").rstrip(".")
