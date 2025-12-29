"""The exception family every part of the package raises.

One root so a caller can catch everything from here without catching the
standard library, and narrow classes underneath so the http surface can map a
failure onto a status code without reading messages.
"""

from __future__ import annotations


class SheavelineError(Exception):
    """Root of every error this package raises."""


class MeasureError(SheavelineError, ValueError):
    """A measured quantity was built or parsed wrong.

    Also a ValueError, because that is what it is, and because pydantic only
    turns a ValueError into a field level complaint. Anything else escapes the
    model and reaches the client as a five hundred.
    """


class ParseError(MeasureError):
    """Text did not read as the measure it was supposed to be."""

    def __init__(self, text: str, wanted: str) -> None:
        self.text = text
        self.wanted = wanted
        super().__init__(f"cannot read {text!r} as {wanted}")


class RangeError(MeasureError):
    """A measure fell outside the range the domain allows."""

    def __init__(self, what: str, value: object, low: object, high: object) -> None:
        self.what = what
        self.value = value
        self.low = low
        self.high = high
        super().__init__(f"{what} {value} is outside {low} to {high}")


class UnitMismatch(MeasureError):
    """Two measures of different kinds were combined."""

    def __init__(self, left: str, right: str) -> None:
        self.left = left
        self.right = right
        super().__init__(f"cannot combine {left} with {right}")
