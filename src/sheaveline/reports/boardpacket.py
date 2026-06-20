"""The annual packet, which is the one document that leaves the department.

Everything else in this package is written for somebody who works here. This is
written for five people on a state board who see the hill once a year, and it has
to answer their questions in the order they ask them: did anything happen, was it
reported on time, are the machines being looked after, and can you get people off
them.

Two things are deliberately not on the page. There is no commentary, because a
number with a sentence attached to it is a number somebody is arguing about
before they have read it. And there is no total availability figure across the
hill, because a surface tow that ran three hundred hours and a detachable quad
that ran two thousand do not average into anything a person should act on.

Like the morning page, it renders what it was handed. Gathering is somebody
else's job, so the packet for the year before last still prints.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal

from sheaveline.board.incident import Incident
from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import FilingNumber, LiftCode
from sheaveline.reports.forms import Align, Column, Report, Table
from sheaveline.upkeep.status import ServiceStatus


@dataclass(frozen=True)
class MachineYear:
    """One machine's year, as the board is told about it."""

    lift: LiftCode
    name: str
    days_open: int
    scheduled: Duration
    chargeable: Duration
    availability: Decimal
    status: ServiceStatus
    ladder_overdue: int
    open_defects: int
    rope_serial: str
    rope_verdict: str
    evacuation_time: Duration | None = None

    def is_clean(self) -> bool:
        """Nothing outstanding, which is the line the board is hoping to read."""
        return self.ladder_overdue == 0 and self.open_defects == 0 and self.status.may_carry_public

    def concerns(self) -> tuple[str, ...]:
        found = []
        if not self.status.may_carry_public:
            found.append(f"{self.lift} is {self.status.value}")
        if self.ladder_overdue:
            found.append(f"{self.lift} has {self.ladder_overdue} inspections overdue")
        if self.open_defects:
            found.append(f"{self.lift} has {self.open_defects} defects open")
        if self.rope_verdict != "serviceable":
            found.append(f"{self.lift} rope {self.rope_serial} is {self.rope_verdict}")
        return tuple(found)


@dataclass(frozen=True)
class FiledIncident:
    """One incident and what became of it."""

    incident: Incident
    filing: FilingNumber | None
    filed_on: date | None = None

    def is_filed(self) -> bool:
        return self.filing is not None

    def was_late(self) -> bool:
        if self.filed_on is None:
            return False
        return self.filed_on > self.incident.due_by().date()


@dataclass(frozen=True)
class BoardPacket:
    """One season, written up for the people who licence it."""

    season: str
    resort: str
    prepared: date
    prepared_by: str
    machines: tuple[MachineYear, ...] = field(default_factory=tuple)
    incidents: tuple[FiledIncident, ...] = field(default_factory=tuple)

    @classmethod
    def of(
        cls,
        season: str,
        resort: str,
        prepared: date,
        prepared_by: str,
        machines: Sequence[MachineYear] = (),
        incidents: Sequence[FiledIncident] = (),
    ) -> BoardPacket:
        return cls(
            season=season,
            resort=resort,
            prepared=prepared,
            prepared_by=prepared_by,
            machines=tuple(sorted(machines, key=lambda one: one.lift.value)),
            incidents=tuple(sorted(incidents, key=lambda one: one.incident.at)),
        )

    def clean_machines(self) -> tuple[MachineYear, ...]:
        return tuple(one for one in self.machines if one.is_clean())

    def concerns(self) -> tuple[str, ...]:
        return tuple(line for one in self.machines for line in one.concerns())

    def unfiled(self) -> tuple[FiledIncident, ...]:
        return tuple(one for one in self.incidents if not one.is_filed())

    def late_filings(self) -> tuple[FiledIncident, ...]:
        return tuple(one for one in self.incidents if one.was_late())

    def days_open(self) -> int:
        """The busiest machine's days, which is how long the hill was open."""
        return max((one.days_open for one in self.machines), default=0)

    def is_clean(self) -> bool:
        return not self.concerns() and not self.unfiled() and not self.late_filings()

    def render(self) -> Report:
        page = Report()
        page.heading(f"annual packet {self.season}")
        page.field("operator", self.resort)
        page.field("prepared", self.prepared.isoformat())
        page.field("by", self.prepared_by)
        page.field("machines", len(self.machines))
        page.field("days open", self.days_open())
        page.field("incidents", len(self.incidents))

        page.section("machines")
        page.table(
            Table.of(
                [
                    Column("lift", 5),
                    Column("name", 16),
                    Column("days", 5, Align.RIGHT),
                    Column("avail", 7, Align.RIGHT),
                    Column("status", 15),
                    Column("due", 4, Align.RIGHT),
                    Column("defects", 8, Align.RIGHT),
                ],
                [
                    (
                        str(one.lift),
                        one.name,
                        one.days_open,
                        f"{one.availability * 100:.1f}%",
                        one.status.value,
                        one.ladder_overdue,
                        one.open_defects,
                    )
                    for one in self.machines
                ],
            )
        )

        page.section("ropes")
        page.table(
            Table.of(
                [Column("lift", 5), Column("serial", 18), Column("verdict", 16)],
                [(str(one.lift), one.rope_serial, one.rope_verdict) for one in self.machines],
            )
        )

        page.section("evacuation")
        page.table(
            Table.of(
                [Column("lift", 5), Column("name", 20), Column("clears in", 12, Align.RIGHT)],
                [
                    (
                        str(one.lift),
                        one.name,
                        "not drilled"
                        if one.evacuation_time is None
                        else one.evacuation_time.format_clock(),
                    )
                    for one in self.machines
                ],
            )
        )
        self._incident_pages(page)
        return page

    def _incident_pages(self, page: Report) -> None:
        page.section("incidents")
        if not self.incidents:
            page.line("nothing was reportable this season")
        else:
            page.table(
                Table.of(
                    [
                        Column("lift", 5),
                        Column("happened", 17),
                        Column("what", 18),
                        Column("injury", 12),
                        Column("filed as", 15),
                    ],
                    [
                        (
                            str(one.incident.lift),
                            f"{one.incident.at:%Y-%m-%d %H:%M}",
                            one.incident.kind.value,
                            one.incident.injury.value,
                            "NOT FILED" if one.filing is None else str(one.filing),
                        )
                        for one in self.incidents
                    ],
                )
            )
        page.section("outstanding")
        concerns = [
            *self.concerns(),
            *[
                f"incident on {one.incident.at:%Y-%m-%d} has not been filed"
                for one in self.unfiled()
            ],
            *[f"filing {one.filing} went in after the deadline" for one in self.late_filings()],
        ]
        if not concerns:
            page.line("nothing outstanding")
            return
        for line in concerns:
            page.line(f"  {line}")

    def text(self) -> str:
        return self.render().text()
