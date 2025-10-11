"""The register, which is the answer to what the department still owes the board.

An incident and a filing are separate records on purpose. The incident happened
whether or not anybody wrote to the board about it, and the gap between the two
is exactly the thing an inspector looks for. So the register holds incidents
with a filing hanging off each one, and the filing can be missing.

The question it exists to answer is asked every Monday morning: what is due, what
is late, and what goes in this month's return.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime

from sheaveline.board.filing import Filing, FilingKind, FilingSeries
from sheaveline.board.incident import BoardError, Incident
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import LiftCode


@dataclass(frozen=True)
class ReportedIncident:
    """One incident and the filing that answered for it, if there is one."""

    incident: Incident
    filing: Filing | None = None

    def __post_init__(self) -> None:
        if self.filing is not None and self.filing.lift != self.incident.lift:
            raise BoardError("that filing is against another machine")
        if self.filing is not None and self.filing.kind is not FilingKind.INCIDENT:
            raise BoardError("an incident is answered by an incident filing")

    def is_filed(self) -> bool:
        return self.filing is not None

    def is_overdue(self, now: datetime) -> bool:
        if self.filing is None:
            return now > self.incident.due_by()
        return self.filing.was_late(self.incident)

    def lateness(self) -> Duration:
        if self.filing is None:
            raise BoardError("nothing was filed, so there is no lateness yet")
        return self.filing.lateness(self.incident)

    def outstanding_for(self, now: datetime) -> Duration:
        """How long this has been sitting unfiled, which is the Monday morning number."""
        if self.filing is not None:
            return Duration(0)
        if now < self.incident.at:
            raise BoardError("that moment is before the incident")
        return Duration.from_seconds(int((now - self.incident.at).total_seconds()))

    def __str__(self) -> str:
        if self.filing is None:
            return f"{self.incident}, not filed"
        return f"{self.incident}, filed as {self.filing.number}"


@dataclass(frozen=True)
class BoardRegister:
    """Every incident on the hill, and what has been sent about each one."""

    series: FilingSeries
    reported: tuple[ReportedIncident, ...] = ()

    @classmethod
    def of(
        cls,
        series: FilingSeries,
        reported: Sequence[ReportedIncident] = (),
    ) -> BoardRegister:
        ordered = tuple(sorted(reported, key=lambda entry: entry.incident.at))
        return cls(series, ordered)

    def record(self, incident: Incident) -> BoardRegister:
        return BoardRegister.of(self.series, [*self.reported, ReportedIncident(incident)])

    def file(self, incident: Incident, filed: datetime, filed_by: str) -> BoardRegister:
        """Take a number and hang it on the incident, which is the only way to file."""
        held = [entry for entry in self.reported if entry.incident == incident]
        if not held:
            raise BoardError("that incident is not in the register")
        if held[0].is_filed():
            raise BoardError("that incident has already been filed")
        series, filing = self.series.file(
            FilingKind.INCIDENT,
            incident.lift,
            filed,
            incident.narrative,
            filed_by,
        )
        others = [entry for entry in self.reported if entry.incident != incident]
        return BoardRegister.of(series, [*others, ReportedIncident(incident, filing)])

    def unfiled(self) -> tuple[ReportedIncident, ...]:
        return tuple(entry for entry in self.reported if not entry.is_filed())

    def overdue(self, now: datetime) -> tuple[ReportedIncident, ...]:
        return tuple(entry for entry in self.reported if entry.is_overdue(now))

    def late_filings(self) -> tuple[ReportedIncident, ...]:
        return tuple(
            entry for entry in self.reported if entry.is_filed() and not entry.lateness().is_zero()
        )

    def for_lift(self, lift: LiftCode) -> tuple[ReportedIncident, ...]:
        return tuple(entry for entry in self.reported if entry.incident.lift == lift)

    def due_next(self, now: datetime) -> ReportedIncident | None:
        """The unfiled incident with the least time left on it."""
        waiting = [entry for entry in self.unfiled() if entry.incident.due_by() >= now]
        if not waiting:
            return None
        return min(waiting, key=lambda entry: entry.incident.due_by())

    def monthly_return(self, year: int, month: int) -> tuple[ReportedIncident, ...]:
        """Everything that only ever needed a line in the month's return."""
        return tuple(
            entry
            for entry in self.reported
            if entry.incident.goes_in_the_monthly_return()
            and entry.incident.at.year == year
            and entry.incident.at.month == month
        )

    def holding_machines(self) -> frozenset[LiftCode]:
        """Machines with an incident against them that stops the lift running."""
        return frozenset(
            entry.incident.lift
            for entry in self.reported
            if entry.incident.holds_the_machine() and not entry.is_filed()
        )

    def __len__(self) -> int:
        return len(self.reported)
