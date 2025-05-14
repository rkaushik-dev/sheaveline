"""Torque, held as whole inch pounds.

Two places on this hill are set with a torque wrench and they use different
units on the same wrench. Brake calipers are specified in pound feet on the
drive drawing. Grip bolts are specified in inch pounds on the grip manual, and
the European manuals give newton metres. Inch pounds is the smallest of the
three, so nothing is lost converting between them.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, RangeError, UnitMismatch

INCH_POUNDS_PER_POUND_FOOT: Final = 12
INCH_POUNDS_PER_NEWTON_METRE: Final = Decimal("8.85074579")

_TORQUE = re.compile(
    r"""^\s*(?P<value>-?\d+(?:\.\d+)?)\s*
    (?P<unit>in\s*lb|inch\s*pounds?|lb\s*ft|ft\s*lb|pound\s*feet|nm|newton\s*metres?)\s*$""",
    re.VERBOSE | re.IGNORECASE,
)


class Torque:
    """A tightening figure in whole inch pounds."""

    __slots__ = ("_inch_pounds",)

    def __init__(self, inch_pounds: int) -> None:
        if not isinstance(inch_pounds, int) or isinstance(inch_pounds, bool):
            raise UnitMismatch("Torque", type(inch_pounds).__name__)
        if inch_pounds < 0:
            raise RangeError("torque", inch_pounds, 0, "no limit")
        self._inch_pounds = inch_pounds

    @property
    def inch_pounds(self) -> int:
        return self._inch_pounds

    @property
    def pound_feet(self) -> Decimal:
        return Decimal(self._inch_pounds) / INCH_POUNDS_PER_POUND_FOOT

    @property
    def newton_metres(self) -> Decimal:
        return Decimal(self._inch_pounds) / INCH_POUNDS_PER_NEWTON_METRE

    @classmethod
    def from_inch_pounds(cls, value: int | float | str | Decimal) -> Torque:
        return cls(_round_half_up(Decimal(str(value))))

    @classmethod
    def from_pound_feet(cls, value: int | float | str | Decimal) -> Torque:
        return cls(_round_half_up(Decimal(str(value)) * INCH_POUNDS_PER_POUND_FOOT))

    @classmethod
    def from_newton_metres(cls, value: int | float | str | Decimal) -> Torque:
        return cls(_round_half_up(Decimal(str(value)) * INCH_POUNDS_PER_NEWTON_METRE))

    @classmethod
    def parse(cls, text: str) -> Torque:
        match = _TORQUE.match(text)
        if match is None:
            raise ParseError(text, "a torque")
        unit = re.sub(r"\s+", "", match.group("unit").lower())
        value = match.group("value")
        if unit.startswith(("lbft", "ftlb", "poundfeet")):
            return cls.from_pound_feet(value)
        if unit.startswith(("nm", "newton")):
            return cls.from_newton_metres(value)
        return cls.from_inch_pounds(value)

    def within(self, target: Torque, fraction: str | Decimal) -> bool:
        """Whether this reading sits inside a band round the figure it was set to."""
        margin = target.scaled(fraction)
        return abs(self._inch_pounds - target._inch_pounds) <= margin._inch_pounds

    def scaled(self, factor: int | float | str | Decimal) -> Torque:
        return Torque(_round_half_up(Decimal(self._inch_pounds) * Decimal(str(factor))))

    def format_pound_feet(self, places: int = 0) -> str:
        quant = Decimal(1).scaleb(-places)
        return f"{self.pound_feet.quantize(quant)} lb ft"

    def format_inch_pounds(self) -> str:
        return f"{self._inch_pounds} in lb"

    def __repr__(self) -> str:
        return f"Torque({self._inch_pounds})"

    def __str__(self) -> str:
        return self.format_inch_pounds()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Torque):
            return NotImplemented
        return self._inch_pounds == other._inch_pounds

    def __hash__(self) -> int:
        return hash(("Torque", self._inch_pounds))

    def __lt__(self, other: Torque) -> bool:
        return self._inch_pounds < other._inch_pounds

    def __le__(self, other: Torque) -> bool:
        return self._inch_pounds <= other._inch_pounds

    def __gt__(self, other: Torque) -> bool:
        return self._inch_pounds > other._inch_pounds

    def __ge__(self, other: Torque) -> bool:
        return self._inch_pounds >= other._inch_pounds

    def __sub__(self, other: Torque) -> Torque:
        if not isinstance(other, Torque):
            return NotImplemented
        return Torque(abs(self._inch_pounds - other._inch_pounds))

    def is_slack(self) -> bool:
        return self._inch_pounds == 0


def _round_half_up(value: Decimal) -> int:
    if value < 0:
        return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
