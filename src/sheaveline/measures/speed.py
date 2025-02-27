"""Rope speed, held as whole mils per minute.

The department writes rope speed two ways and has to answer in either. The
drawings and the board's forms use feet per minute, and the manufacturer's
manuals use metres per second because the machinery came from the Alps. Mils
per minute is small enough that a round trip through either face comes back
where it started.

Wind is the same dimension and the summit station reports it in miles an hour,
so that face lives here too rather than in a second speed type.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, RangeError
from sheaveline.measures.duration import SECONDS_PER_MINUTE, Duration
from sheaveline.measures.length import MILS_PER_FOOT, Length

MILS_PER_METRE: Final = Decimal("39370.078740157480315")
MILS_PER_MILE: Final = 63_360_000
_MILS_PER_MINUTE_PER_MPS: Final = MILS_PER_METRE * SECONDS_PER_MINUTE
_MILS_PER_MINUTE_PER_MPH: Final = Decimal(MILS_PER_MILE) / 60

_SPEED = re.compile(
    r"""^\s*(?P<value>-?\d+(?:\.\d+)?)\s*
    (?P<unit>fpm|ft/min|feet\s*per\s*minute|m/s|mps|metres?\s*per\s*second
    |mph|mi/h|miles\s*per\s*hour)\s*$""",
    re.VERBOSE | re.IGNORECASE,
)


class Speed:
    """A rope or carrier speed in whole mils per minute."""

    __slots__ = ("_mils_per_minute",)

    def __init__(self, mils_per_minute: int) -> None:
        if not isinstance(mils_per_minute, int) or isinstance(mils_per_minute, bool):
            raise RangeError("speed", mils_per_minute, "a whole number of mils", "")
        if mils_per_minute < 0:
            raise RangeError("speed", mils_per_minute, 0, "no limit")
        self._mils_per_minute = mils_per_minute

    @property
    def mils_per_minute(self) -> int:
        return self._mils_per_minute

    @property
    def feet_per_minute(self) -> Decimal:
        return Decimal(self._mils_per_minute) / MILS_PER_FOOT

    @property
    def metres_per_second(self) -> Decimal:
        return Decimal(self._mils_per_minute) / _MILS_PER_MINUTE_PER_MPS

    @property
    def miles_per_hour(self) -> Decimal:
        return Decimal(self._mils_per_minute) / _MILS_PER_MINUTE_PER_MPH

    @classmethod
    def from_feet_per_minute(cls, fpm: int | float | str | Decimal) -> Speed:
        return cls(_round_half_up(Decimal(str(fpm)) * MILS_PER_FOOT))

    @classmethod
    def from_metres_per_second(cls, mps: int | float | str | Decimal) -> Speed:
        return cls(_round_half_up(Decimal(str(mps)) * _MILS_PER_MINUTE_PER_MPS))

    @classmethod
    def from_miles_per_hour(cls, mph: int | float | str | Decimal) -> Speed:
        return cls(_round_half_up(Decimal(str(mph)) * _MILS_PER_MINUTE_PER_MPH))

    @classmethod
    def from_mils_per_minute(cls, mils: int) -> Speed:
        return cls(mils)

    @classmethod
    def parse(cls, text: str) -> Speed:
        match = _SPEED.match(text)
        if match is None:
            raise ParseError(text, "a speed")
        unit = match.group("unit").lower()
        value = match.group("value")
        if unit.startswith(("m/s", "mps", "metre", "meter")):
            return cls.from_metres_per_second(value)
        if unit.startswith(("mph", "mi/h", "mile")):
            return cls.from_miles_per_hour(value)
        return cls.from_feet_per_minute(value)

    def travel(self, elapsed: Duration) -> Length:
        """How far the rope moves in the given time."""
        mils = Decimal(self._mils_per_minute) * elapsed.seconds / SECONDS_PER_MINUTE
        return Length(_round_half_up(mils))

    def time_to_cover(self, distance: Length) -> Duration:
        """How long the rope takes to carry a carrier over that distance."""
        if self._mils_per_minute == 0:
            raise RangeError("speed", 0, "greater than zero", "no limit")
        seconds = Decimal(distance.mils) * SECONDS_PER_MINUTE / self._mils_per_minute
        return Duration(int((seconds + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR")))

    def format_fpm(self, places: int = 0) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.feet_per_minute.quantize(quant)} fpm"

    def format_mps(self, places: int = 2) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.metres_per_second.quantize(quant)} m/s"

    def format_mph(self, places: int = 0) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.miles_per_hour.quantize(quant)} mph"

    def __repr__(self) -> str:
        return f"Speed.from_mils_per_minute({self._mils_per_minute})"

    def __str__(self) -> str:
        return self.format_fpm()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Speed):
            return NotImplemented
        return self._mils_per_minute == other._mils_per_minute

    def __hash__(self) -> int:
        return hash(("Speed", self._mils_per_minute))

    def __lt__(self, other: Speed) -> bool:
        return self._mils_per_minute < other._mils_per_minute

    def __le__(self, other: Speed) -> bool:
        return self._mils_per_minute <= other._mils_per_minute

    def __gt__(self, other: Speed) -> bool:
        return self._mils_per_minute > other._mils_per_minute

    def __ge__(self, other: Speed) -> bool:
        return self._mils_per_minute >= other._mils_per_minute

    def scaled(self, factor: int | float | str | Decimal) -> Speed:
        return Speed(_round_half_up(Decimal(self._mils_per_minute) * Decimal(str(factor))))

    def is_stopped(self) -> bool:
        return self._mils_per_minute == 0


STOPPED: Final = Speed(0)


def _round_half_up(value: Decimal) -> int:
    if value < 0:
        return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
