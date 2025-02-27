"""Wind as the summit station reports it, and what it does to a line.

The station sends a direction, a sustained speed and a gust. None of those is
the number the hold rules want. What matters is how much of the wind is trying
to push carriers sideways off the line, which depends on the angle between the
wind and the line, and what the wind does to exposed skin, which is why the
chill is here and not in the temperature module.
"""

from __future__ import annotations

import math
import re
from collections.abc import Callable
from dataclasses import dataclass
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError
from sheaveline.measures.angle import Bearing
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature

CHILL_CEILING_F: Final = Decimal("50")
CHILL_FLOOR_MPH: Final = Decimal("3")

_SHORTHAND = re.compile(
    r"""^\s*(?P<from>[A-Za-z]{1,3}|\d{1,3})\s+
    (?P<speed>\d+(?:\.\d+)?)\s*
    (?:g\s*(?P<gust>\d+(?:\.\d+)?))?\s*$""",
    re.VERBOSE | re.IGNORECASE,
)


@dataclass(frozen=True)
class Wind:
    """A wind observation. The bearing is where the wind comes from."""

    speed: Speed
    bearing: Bearing
    gust: Speed | None = None

    def __post_init__(self) -> None:
        if self.gust is not None and self.gust < self.speed:
            raise ParseError(f"gust {self.gust} below sustained {self.speed}", "a wind observation")

    @classmethod
    def from_mph(
        cls,
        mph: int | float | str | Decimal,
        bearing: str | Bearing,
        gust_mph: int | float | str | Decimal | None = None,
    ) -> Wind:
        where = bearing if isinstance(bearing, Bearing) else Bearing.parse(bearing)
        gust = None if gust_mph is None else Speed.from_miles_per_hour(gust_mph)
        return cls(Speed.from_miles_per_hour(mph), where, gust)

    @classmethod
    def parse(cls, text: str) -> Wind:
        """Read the station shorthand, SW 24G38 or 225 24."""
        match = _SHORTHAND.match(text)
        if match is None:
            raise ParseError(text, "a wind observation")
        return cls.from_mph(
            match.group("speed"),
            Bearing.parse(match.group("from")),
            match.group("gust"),
        )

    @property
    def peak(self) -> Speed:
        """The number a hold rule reads, which is the gust when there is one."""
        return self.speed if self.gust is None else self.gust

    def blowing_toward(self) -> Bearing:
        return self.bearing.reciprocal()

    def across(self, line: Bearing) -> Speed:
        """How much of the wind is trying to push carriers off the line."""
        return self._resolve(line, math.sin)

    def along(self, line: Bearing) -> Speed:
        """How much of the wind runs up or down the line."""
        return self._resolve(line, math.cos)

    def peak_across(self, line: Bearing) -> Speed:
        """The same question asked of the gust, which is what a hold reads."""
        return Wind(self.peak, self.bearing).across(line)

    def is_headwind(self, line: Bearing) -> bool:
        """True when the wind opposes a carrier travelling up the line."""
        return math.cos(self._offset(line)) < 0

    def _offset(self, line: Bearing) -> float:
        return line.difference(self.blowing_toward()).radians

    def _resolve(self, line: Bearing, part: Callable[[float], float]) -> Speed:
        component = abs(part(self._offset(line)))
        return Speed(int(round(self.speed.mils_per_minute * component)))

    def chill(self, temperature: Temperature) -> Temperature:
        """The national weather service wind chill, in Fahrenheit.

        The formula is only defined at or below fifty degrees with the wind
        above three miles an hour. Outside that the reading stands as it is.
        """
        degrees = temperature.degrees_f
        mph = self.speed.miles_per_hour
        if degrees > CHILL_CEILING_F or mph < CHILL_FLOOR_MPH:
            return temperature
        factor = float(mph) ** 0.16
        chilled = (
            Decimal("35.74")
            + Decimal("0.6215") * degrees
            - Decimal("35.75") * Decimal(str(factor))
            + Decimal("0.4275") * degrees * Decimal(str(factor))
        )
        return Temperature.from_degrees_f(chilled)

    def format_shorthand(self) -> str:
        sustained = self.speed.miles_per_hour.quantize(Decimal(1))
        if self.gust is None:
            return f"{self.bearing.nearest_point()} {sustained}"
        gust = self.gust.miles_per_hour.quantize(Decimal(1))
        return f"{self.bearing.nearest_point()} {sustained}G{gust}"

    def __str__(self) -> str:
        return self.format_shorthand()


CALM: Final = Wind(Speed(0), Bearing(0))
