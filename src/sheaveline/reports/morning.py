"""The morning report, which is the one page the mountain manager reads at seven.

It answers one question a machine: is it opening, and if not, what is stopping
it. Everything else on the page is there because somebody asked why once and
having the answer already printed saved a telephone call.

The rendering takes gathered facts rather than reaching for a database. That is
not tidiness for its own sake: the report has to be printable from last Tuesday's
records, and a renderer that goes and looks things up can only ever print today.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date

from sheaveline.plant.identity import LiftCode
from sheaveline.reports.forms import Align, Column, Report, Table
from sheaveline.upkeep.status import ServiceStatus
from sheaveline.upkeep.workorder import WorkOrder
from sheaveline.weather.hold import Hold


@dataclass(frozen=True)
class MachineLine:
    """One machine's morning, gathered by whoever walked it."""

    lift: LiftCode
    name: str
    status: ServiceStatus
    sheet_walked: bool
    blockers: tuple[str, ...] = ()
    holds: tuple[Hold, ...] = ()
    orders: tuple[WorkOrder, ...] = ()
    upkeep_due: tuple[str, ...] = ()

    def opening(self) -> bool:
        """Everything has to be true at once, which is the point of the page."""
        return (
            self.status.may_carry_public
            and self.sheet_walked
            and not self.blockers
            and not self.holds
        )

    def verdict(self) -> str:
        if self.opening():
            return "opening"
        return "held" if self.holds and not self.blockers else "not opening"

    def reasons(self) -> tuple[str, ...]:
        found = list(self.blockers)
        if not self.sheet_walked:
            found.append("morning walk not signed")
        found.extend(f"{one.reason.value} hold" for one in self.holds)
        if not self.status.may_carry_public:
            found.append(self.status.value)
        return tuple(found)


@dataclass(frozen=True)
class Morning:
    """Everything the office gathered for one day."""

    day: date
    resort: str
    snow: str
    machines: tuple[MachineLine, ...] = field(default_factory=tuple)

    @classmethod
    def of(cls, day: date, resort: str, snow: str, machines: Sequence[MachineLine]) -> Morning:
        return cls(day, resort, snow, tuple(machines))

    def opening(self) -> tuple[MachineLine, ...]:
        return tuple(one for one in self.machines if one.opening())

    def held(self) -> tuple[MachineLine, ...]:
        return tuple(one for one in self.machines if not one.opening())

    def urgent_orders(self) -> tuple[WorkOrder, ...]:
        return tuple(
            order
            for machine in self.machines
            for order in machine.orders
            if order.priority.stops_the_machine
        )

    def render(self) -> Report:
        page = Report()
        page.heading(f"{self.resort} morning report")
        page.field("day", self.day.isoformat())
        page.field("settled snow", self.snow)
        page.field("machines opening", f"{len(self.opening())} of {len(self.machines)}")

        page.section("the machines")
        page.table(
            Table.of(
                [
                    Column("lift", 5),
                    Column("name", 20),
                    Column("status", 15),
                    Column("verdict", 12),
                    Column("orders", 6, Align.RIGHT),
                    Column("due", 6, Align.RIGHT),
                ],
                [
                    (
                        str(one.lift),
                        one.name,
                        one.status.value,
                        one.verdict(),
                        len(one.orders),
                        len(one.upkeep_due),
                    )
                    for one in self.machines
                ],
            )
        )

        stopped = self.held()
        if stopped:
            page.section("what is stopping them")
            for machine in stopped:
                page.line(f"{machine.lift} {machine.name}")
                for reason in machine.reasons():
                    page.line(f"    {reason}")

        urgent = self.urgent_orders()
        if urgent:
            page.section("work orders that stop a machine")
            page.table(
                Table.of(
                    [
                        Column("no", 4, Align.RIGHT),
                        Column("lift", 5),
                        Column("where", 12),
                        Column("raised", 11),
                        Column("detail", 42),
                    ],
                    [
                        (
                            order.number,
                            str(order.lift),
                            order.where or "",
                            order.raised.isoformat(),
                            order.detail,
                        )
                        for order in urgent
                    ],
                )
            )

        due = [(machine.lift, code) for machine in self.machines for code in machine.upkeep_due]
        if due:
            page.section("falling due")
            page.table(
                Table.of(
                    [Column("lift", 5), Column("job", 6)],
                    [(str(lift), code) for lift, code in due],
                )
            )

        page.blank()
        page.line(f"printed for {self.resort}, {self.day.isoformat()}")
        return page

    def text(self) -> str:
        return self.render().text()
