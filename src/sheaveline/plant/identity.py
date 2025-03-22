"""The identifiers that appear on paperwork, and the rules they have to keep.

Four things on this hill get written on a form by hand and then typed in again
later, which is why they are types and not strings. A lift has a code the crew
paints on the terminal. A tower is that code and a number, and towers sort by
number rather than by text, so tower ten does not come between one and two. A
haul rope carries the mill's serial and the year it was drawn. A filing with the
board gets a number the board issues, and those run in sequence with no gaps.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Final

from sheaveline.errors import ParseError, RangeError

MAX_TOWER: Final = 99
FIRST_FILING_YEAR: Final = 1970

_LIFT_CODE = re.compile(r"^[A-Z][A-Z0-9]{1,7}$")
_TOWER = re.compile(r"^(?P<lift>[A-Z][A-Z0-9]{1,7})[ /-]?T(?P<number>\d{1,2})$", re.IGNORECASE)
_ROPE = re.compile(r"^(?P<mill>[A-Z]{2,4})-(?P<year>\d{4})-(?P<serial>\d{4,6})$", re.IGNORECASE)
_FILING = re.compile(r"^(?P<state>[A-Z]{2})-(?P<year>\d{4})-(?P<sequence>\d{4})$", re.IGNORECASE)


@dataclass(frozen=True, order=True)
class LiftCode:
    """What the crew paints on the terminal, upper case and never blank."""

    value: str

    def __post_init__(self) -> None:
        if not _LIFT_CODE.match(self.value):
            raise ParseError(self.value, "a lift code")

    @classmethod
    def parse(cls, text: str) -> LiftCode:
        return cls(text.strip().upper())

    def tower(self, number: int) -> TowerRef:
        return TowerRef(self, number)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class TowerRef:
    """A tower, which is a lift and a number counted up the line from the drive."""

    lift: LiftCode
    number: int

    def __post_init__(self) -> None:
        if not 1 <= self.number <= MAX_TOWER:
            raise RangeError("tower number", self.number, 1, MAX_TOWER)

    @classmethod
    def parse(cls, text: str) -> TowerRef:
        match = _TOWER.match(text.strip())
        if match is None:
            raise ParseError(text, "a tower reference")
        return cls(LiftCode.parse(match.group("lift")), int(match.group("number")))

    def next_up(self) -> TowerRef:
        return TowerRef(self.lift, self.number + 1)

    def sort_key(self) -> tuple[str, int]:
        """Towers sort by number, so ten does not land between one and two."""
        return (self.lift.value, self.number)

    def __lt__(self, other: TowerRef) -> bool:
        return self.sort_key() < other.sort_key()

    def __le__(self, other: TowerRef) -> bool:
        return self.sort_key() <= other.sort_key()

    def __gt__(self, other: TowerRef) -> bool:
        return self.sort_key() > other.sort_key()

    def __ge__(self, other: TowerRef) -> bool:
        return self.sort_key() >= other.sort_key()

    def __str__(self) -> str:
        return f"{self.lift}-T{self.number}"


@dataclass(frozen=True, order=True)
class RopeSerial:
    """The mill mark on a haul rope, which follows it for its whole life."""

    mill: str
    year: int
    serial: str

    def __post_init__(self) -> None:
        if not 2 <= len(self.mill) <= 4 or not self.mill.isalpha():
            raise ParseError(self.mill, "a mill code")
        if not 1900 <= self.year <= 2100:
            raise RangeError("rope year", self.year, 1900, 2100)
        if not 4 <= len(self.serial) <= 6 or not self.serial.isdigit():
            raise ParseError(self.serial, "a rope serial")

    @classmethod
    def parse(cls, text: str) -> RopeSerial:
        match = _ROPE.match(text.strip())
        if match is None:
            raise ParseError(text, "a rope serial")
        return cls(
            match.group("mill").upper(),
            int(match.group("year")),
            match.group("serial"),
        )

    def age_in(self, year: int) -> int:
        if year < self.year:
            raise RangeError("year", year, self.year, "later")
        return year - self.year

    def __str__(self) -> str:
        return f"{self.mill}-{self.year}-{self.serial}"


@dataclass(frozen=True, order=True)
class FilingNumber:
    """A number the board issues. Sequences run per state and per year with no gaps."""

    state: str
    year: int
    sequence: int

    def __post_init__(self) -> None:
        if len(self.state) != 2 or not self.state.isalpha():
            raise ParseError(self.state, "a state code")
        if not FIRST_FILING_YEAR <= self.year <= 2100:
            raise RangeError("filing year", self.year, FIRST_FILING_YEAR, 2100)
        if not 1 <= self.sequence <= 9999:
            raise RangeError("filing sequence", self.sequence, 1, 9999)

    @classmethod
    def parse(cls, text: str) -> FilingNumber:
        match = _FILING.match(text.strip())
        if match is None:
            raise ParseError(text, "a filing number")
        return cls(
            match.group("state").upper(),
            int(match.group("year")),
            int(match.group("sequence")),
        )

    @classmethod
    def first_of(cls, state: str, year: int) -> FilingNumber:
        return cls(state.upper(), year, 1)

    def next_in_sequence(self) -> FilingNumber:
        if self.sequence == 9999:
            raise RangeError("filing sequence", self.sequence + 1, 1, 9999)
        return FilingNumber(self.state, self.year, self.sequence + 1)

    def follows(self, earlier: FilingNumber) -> bool:
        """True when this number is the very next one, which is what gapless means."""
        return (
            self.state == earlier.state
            and self.year == earlier.year
            and self.sequence == earlier.sequence + 1
        )

    def __str__(self) -> str:
        return f"{self.state}-{self.year}-{self.sequence:04d}"
