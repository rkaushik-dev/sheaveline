"""What a machine is due, read off its record book and its meters.

The awkward part is the anchor, and there are three cases rather than two. A
monthly job done four days late is still a monthly job, and its next one is
counted from when it was due rather than from when it was finally signed off, or
the whole ladder walks later every month until the annual inspection has drifted
into the summer. A job done so late that it fell out of grace restarts the
chain, because at that point the old schedule is fiction. And a job done early
also restarts it, counted from the day the work was actually done, because
nobody gets credit for a cycle they skipped past.

A job on two clocks is due when either of them says so, and the two are asked
separately because a machine that ran three hundred days and a machine that ran
thirty will hit them in a different order.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from sheaveline.ops.meters import MeterHistory
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.upkeep.interval import Basis, Requirement, UpkeepError
from sheaveline.upkeep.library import library_for, requirement
from sheaveline.upkeep.record import RecordBook


@dataclass(frozen=True)
class DueItem:
    """One job, and every clock it is measured against."""

    job: Requirement
    due_on: date | None
    due_at_hours: Decimal | None
    due_at_cycles: int | None
    ever_done: bool

    def calendar_overdue(self, on: date) -> bool:
        return self.due_on is not None and on > self.due_on

    def hours_overdue(self, hours: Decimal | None) -> bool:
        if self.due_at_hours is None or hours is None:
            return False
        return hours > self.due_at_hours

    def cycles_overdue(self, cycles: int | None) -> bool:
        if self.due_at_cycles is None or cycles is None:
            return False
        return cycles > self.due_at_cycles

    def is_overdue(
        self,
        on: date,
        hours: Decimal | None = None,
        cycles: int | None = None,
    ) -> bool:
        return self.calendar_overdue(on) or self.hours_overdue(hours) or self.cycles_overdue(cycles)

    def fired(
        self,
        on: date,
        hours: Decimal | None = None,
        cycles: int | None = None,
    ) -> frozenset[Basis]:
        """Which clock or clocks took it past due, which is what the note says."""
        which = set()
        if self.calendar_overdue(on):
            which.add(Basis.CALENDAR)
        if self.hours_overdue(hours):
            which.add(Basis.HOURS)
        if self.cycles_overdue(cycles):
            which.add(Basis.CYCLES)
        return frozenset(which)

    def days_until(self, on: date) -> int | None:
        if self.due_on is None:
            return None
        return (self.due_on - on).days

    def __str__(self) -> str:
        when = self.due_on.isoformat() if self.due_on is not None else "on the meter"
        return f"{self.job.code} due {when}"


@dataclass(frozen=True)
class UpkeepSchedule:
    """One machine's ladder, anchored on when it was built."""

    lift: LiftCode
    lift_class: LiftClass
    commissioned: date
    book: RecordBook
    meter: MeterHistory | None = None

    def __post_init__(self) -> None:
        if self.book.lift != self.lift:
            raise UpkeepError(f"that record book is for {self.book.lift}")
        if self.meter is not None and self.meter.lift != self.lift:
            raise UpkeepError(f"that meter history is for {self.meter.lift}")
        for record in self.book.records:
            if record.done < self.commissioned:
                raise UpkeepError(f"{record.code} was done before {self.lift} was built")

    def jobs(self) -> tuple[Requirement, ...]:
        return library_for(self.lift_class)

    def anchor_for(self, code: str) -> date:
        """Walk the chain forward, keeping the schedule where it was meant to be."""
        job = requirement(code)
        anchor = self.commissioned
        for record in self.book.for_code(code):
            due = job.interval.due_date(anchor)
            grace_end = job.interval.last_day_of_grace(anchor)
            if due is None or grace_end is None or record.done < due or record.done > grace_end:
                anchor = record.done
            else:
                anchor = due
        return anchor

    def due_item(self, code: str) -> DueItem:
        job = requirement(code)
        if not job.applies_to(self.lift_class):
            raise UpkeepError(f"{code} is not a job on a {self.lift_class.value}")
        records = self.book.for_code(code)
        last = records[-1] if records else None
        due_on = job.interval.due_date(self.anchor_for(code))
        due_hours = None
        due_cycles = None
        if last is not None and last.hours is not None:
            due_hours = job.interval.due_hours(last.hours)
        if last is not None and last.cycles is not None:
            due_cycles = job.interval.due_cycles(last.cycles)
        return DueItem(job, due_on, due_hours, due_cycles, ever_done=last is not None)

    def all_due(self) -> tuple[DueItem, ...]:
        return tuple(self.due_item(job.code) for job in self.jobs())

    def current_hours(self) -> Decimal | None:
        return None if self.meter is None else self.meter.latest().hours

    def current_cycles(self) -> int | None:
        return None if self.meter is None else self.meter.latest().cycles

    def overdue(self, on: date) -> tuple[DueItem, ...]:
        hours = self.current_hours()
        cycles = self.current_cycles()
        return tuple(item for item in self.all_due() if item.is_overdue(on, hours, cycles))

    def overdue_critical(self, on: date) -> tuple[DueItem, ...]:
        return tuple(item for item in self.overdue(on) if item.job.critical)

    def never_done(self) -> tuple[DueItem, ...]:
        return tuple(item for item in self.all_due() if not item.ever_done)

    def due_within(self, on: date, days: int) -> tuple[DueItem, ...]:
        found = []
        for item in self.all_due():
            remaining = item.days_until(on)
            if remaining is not None and 0 <= remaining <= days:
                found.append(item)
        return tuple(found)

    def is_current(self, on: date) -> bool:
        return not self.overdue_critical(on)

    def __str__(self) -> str:
        return f"{self.lift} ladder, {len(self.jobs())} jobs"
