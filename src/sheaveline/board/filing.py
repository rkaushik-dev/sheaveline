"""Filings, and the numbers the board issues them under.

The board's numbers run in sequence per state and per year with no gaps, and a
gap is not a filing mistake, it is a question at the audit about what was
withdrawn and why. So the series is the thing that hands out numbers, and it
refuses to skip one.

A filing also records whether it was late, and lateness is measured against the
incident rather than against the filing clerk's day. A deropement at half past
eleven on a Saturday is due at half past one on the Saturday, and Monday morning
is not an answer.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, datetime
from enum import Enum

from sheaveline.board.incident import BoardError, Incident
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import FilingNumber, LiftCode


class FilingKind(Enum):
    """What a filing is about."""

    INCIDENT = "incident"
    ANNUAL_INSPECTION = "annual inspection"
    CAPACITY = "capacity certificate"
    MODIFICATION = "modification"
    ROPE_CHANGE = "rope change"
    MONTHLY_RETURN = "monthly return"

    @property
    def is_routine(self) -> bool:
        return self in {FilingKind.MONTHLY_RETURN, FilingKind.ANNUAL_INSPECTION}

    @property
    def needs_an_acknowledgement(self) -> bool:
        """The board writes back on anything that changes what a machine may do."""
        return self in {
            FilingKind.CAPACITY,
            FilingKind.MODIFICATION,
            FilingKind.ROPE_CHANGE,
        }


@dataclass(frozen=True)
class Filing:
    """One thing sent to the board, under one number."""

    number: FilingNumber
    kind: FilingKind
    lift: LiftCode
    filed: datetime
    about: str
    filed_by: str
    acknowledged: date | None = None

    def __post_init__(self) -> None:
        if not self.about.strip():
            raise BoardError(f"{self.number} says nothing")
        if not self.filed_by.strip():
            raise BoardError(f"{self.number} is signed by whoever sent it")
        if self.acknowledged is not None and self.acknowledged < self.filed.date():
            raise BoardError(f"{self.number} was acknowledged before it was sent")

    def is_acknowledged(self) -> bool:
        return self.acknowledged is not None

    def is_outstanding(self) -> bool:
        return self.kind.needs_an_acknowledgement and not self.is_acknowledged()

    def acknowledge(self, on: date) -> Filing:
        if self.is_acknowledged():
            raise BoardError(f"{self.number} is already acknowledged")
        return Filing(
            number=self.number,
            kind=self.kind,
            lift=self.lift,
            filed=self.filed,
            about=self.about,
            filed_by=self.filed_by,
            acknowledged=on,
        )

    def lateness(self, incident: Incident) -> Duration:
        """How far past the deadline it went out, which the board totals up."""
        due = incident.due_by()
        if self.filed <= due:
            return Duration(0)
        return Duration.from_seconds(int((self.filed - due).total_seconds()))

    def was_late(self, incident: Incident) -> bool:
        return not self.lateness(incident).is_zero()

    def __str__(self) -> str:
        return f"{self.number} {self.kind.value}, {self.lift}, {self.filed:%Y-%m-%d}"


@dataclass(frozen=True)
class FilingSeries:
    """Every filing under one state and one year, in issue order."""

    state: str
    year: int
    filings: tuple[Filing, ...] = ()

    def __post_init__(self) -> None:
        for filing in self.filings:
            if filing.number.state != self.state.upper() or filing.number.year != self.year:
                raise BoardError(f"{filing.number} does not belong in {self.state} {self.year}")
        numbers = [filing.number.sequence for filing in self.filings]
        if len(set(numbers)) != len(numbers):
            raise BoardError("two filings share a number")

    @classmethod
    def of(cls, state: str, year: int, filings: Sequence[Filing] = ()) -> FilingSeries:
        ordered = tuple(sorted(filings, key=lambda filing: filing.number.sequence))
        return cls(state.upper(), year, ordered)

    def next_number(self) -> FilingNumber:
        if not self.filings:
            return FilingNumber.first_of(self.state, self.year)
        return self.filings[-1].number.next_in_sequence()

    def is_gapless(self) -> bool:
        expected = 1
        for filing in self.filings:
            if filing.number.sequence != expected:
                return False
            expected += 1
        return True

    def missing_numbers(self) -> tuple[int, ...]:
        if not self.filings:
            return ()
        held = {filing.number.sequence for filing in self.filings}
        highest = max(held)
        return tuple(number for number in range(1, highest + 1) if number not in held)

    def file(
        self,
        kind: FilingKind,
        lift: LiftCode,
        filed: datetime,
        about: str,
        filed_by: str,
    ) -> tuple[FilingSeries, Filing]:
        """Take the next number and record it, which is the only way to get one."""
        if filed.year != self.year:
            raise BoardError(f"a {filed.year} filing does not go in the {self.year} series")
        entry = Filing(self.next_number(), kind, lift, filed, about, filed_by)
        return FilingSeries.of(self.state, self.year, [*self.filings, entry]), entry

    def for_lift(self, lift: LiftCode) -> tuple[Filing, ...]:
        return tuple(filing for filing in self.filings if filing.lift == lift)

    def of_kind(self, kind: FilingKind) -> tuple[Filing, ...]:
        return tuple(filing for filing in self.filings if filing.kind is kind)

    def outstanding(self) -> tuple[Filing, ...]:
        return tuple(filing for filing in self.filings if filing.is_outstanding())

    def acknowledge(self, number: FilingNumber, on: date) -> FilingSeries:
        found = [filing for filing in self.filings if filing.number == number]
        if not found:
            raise BoardError(f"{number} is not in this series")
        others = [filing for filing in self.filings if filing.number != number]
        return FilingSeries.of(self.state, self.year, [*others, found[0].acknowledge(on)])

    def __len__(self) -> int:
        return len(self.filings)
