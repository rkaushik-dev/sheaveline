"""Lengths, held as whole thousandths of an inch.

Everything on a ropeway drawing is a length and the drawings disagree about the
unit. Span lengths are feet, tower heights are feet and inches, rope diameter is
inches to three places and rope wear is read in thousandths. One integer base
unit carries all of them without a float creeping in, and a mil is small enough
that nothing the department measures needs a fraction of one.
"""

from __future__ import annotations

import re
from decimal import Decimal
from typing import Final

from sheaveline.errors import ParseError, RangeError, UnitMismatch

MILS_PER_INCH: Final = 1000
INCHES_PER_FOOT: Final = 12
MILS_PER_FOOT: Final = MILS_PER_INCH * INCHES_PER_FOOT

_FEET_INCHES = re.compile(
    r"""^\s*
    (?:(?P<feet>-?\d+)\s*(?:'|ft|feet|foot)\s*)?
    (?:(?P<inches>-?\d+(?:\.\d+)?)\s*(?:"|in|inch|inches)?\s*)?
    $""",
    re.VERBOSE | re.IGNORECASE,
)


class Length:
    """A length in whole mils. Immutable, exact, and comparable."""

    __slots__ = ("_mils",)

    def __init__(self, mils: int) -> None:
        if not isinstance(mils, int) or isinstance(mils, bool):
            raise UnitMismatch("Length", type(mils).__name__)
        self._mils = mils

    @property
    def mils(self) -> int:
        return self._mils

    @property
    def inches(self) -> Decimal:
        return Decimal(self._mils) / MILS_PER_INCH

    @property
    def feet(self) -> Decimal:
        return Decimal(self._mils) / MILS_PER_FOOT

    @classmethod
    def from_mils(cls, mils: int) -> Length:
        return cls(mils)

    @classmethod
    def from_inches(cls, inches: int | float | str | Decimal) -> Length:
        return cls(_round_half_up(Decimal(str(inches)) * MILS_PER_INCH))

    @classmethod
    def from_feet(cls, feet: int | float | str | Decimal) -> Length:
        return cls(_round_half_up(Decimal(str(feet)) * MILS_PER_FOOT))

    @classmethod
    def from_feet_inches(cls, feet: int, inches: int | float | str | Decimal = 0) -> Length:
        sign = -1 if feet < 0 else 1
        whole = abs(feet) * MILS_PER_FOOT + _round_half_up(Decimal(str(inches)) * MILS_PER_INCH)
        return cls(sign * whole)

    @classmethod
    def parse(cls, text: str) -> Length:
        """Read a drawing string. Accepts 148, 148 ft, 12'6", 1.378 in, 6 feet 3 inches."""
        match = _FEET_INCHES.match(text)
        if match is None or (match.group("feet") is None and match.group("inches") is None):
            raise ParseError(text, "a length")
        feet_text = match.group("feet")
        inches_text = match.group("inches")
        if feet_text is None:
            # A bare number with no unit is inches, which is how a rope diameter
            # is written and how a machinist reads an unlabelled dimension.
            return cls.from_inches(inches_text or "0")
        feet = int(feet_text)
        return cls.from_feet_inches(feet, inches_text or "0")

    def split_feet_inches(self) -> tuple[int, Decimal]:
        """Split into whole feet and the remaining inches, both signed alike."""
        sign = -1 if self._mils < 0 else 1
        magnitude = abs(self._mils)
        feet, rest = divmod(magnitude, MILS_PER_FOOT)
        return sign * feet, sign * (Decimal(rest) / MILS_PER_INCH)

    def format_feet_inches(self) -> str:
        feet, inches = self.split_feet_inches()
        return f"{feet}' {_trim(abs(inches))}\""

    def format_inches(self, places: int = 3) -> str:
        quant = Decimal(1).scaleb(-places)
        return f'{self.inches.quantize(quant)}"'

    def __repr__(self) -> str:
        return f"Length.from_mils({self._mils})"

    def __str__(self) -> str:
        return self.format_feet_inches()

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Length):
            return NotImplemented
        return self._mils == other._mils

    def __hash__(self) -> int:
        return hash(("Length", self._mils))

    def __lt__(self, other: Length) -> bool:
        return self._mils < other._mils

    def __le__(self, other: Length) -> bool:
        return self._mils <= other._mils

    def __gt__(self, other: Length) -> bool:
        return self._mils > other._mils

    def __ge__(self, other: Length) -> bool:
        return self._mils >= other._mils

    def __add__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self._mils + other._mils)

    def __sub__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self._mils - other._mils)

    def __neg__(self) -> Length:
        return Length(-self._mils)

    def __abs__(self) -> Length:
        return Length(abs(self._mils))

    def scaled(self, factor: int | float | str | Decimal) -> Length:
        return Length(_round_half_up(Decimal(self._mils) * Decimal(str(factor))))

    def ratio(self, other: Length) -> Decimal:
        if other._mils == 0:
            raise RangeError("divisor length", other, "nonzero", "nonzero")
        return Decimal(self._mils) / Decimal(other._mils)

    def is_zero(self) -> bool:
        return self._mils == 0


ZERO_LENGTH: Final = Length(0)


def _round_half_up(value: Decimal) -> int:
    """Round to the nearest mil, halves away from zero, the way a shop rounds."""
    if value < 0:
        return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def _trim(value: Decimal) -> str:
    text = f"{value.normalize():f}"
    return text if "." not in text else text.rstrip("0").rstrip(".")
