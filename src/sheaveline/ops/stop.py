"""Stops, what kind they were, and what it takes to run again after one.

A lift stops a dozen times a day and almost all of it is nothing: somebody drops
a pole at the unload and the operator hits the button. What matters is telling
those apart from the handful that are not nothing, because the answer to what
happens next is completely different.

Three questions get asked of every stop and they do not have the same answer.
Does the board hear about it. Does somebody walk or ride the line before it runs
again. Does it count against the machine when the season's availability is
worked out. A power cut is not the machine's fault and does not count against
it, but the line still gets ridden before it turns, because carriers have been
sitting in the wind.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Final

from sheaveline.measures.duration import Duration
from sheaveline.ops.preop import OperationsError
from sheaveline.plant.identity import LiftCode

LONG_STOP: Final = Duration.from_minutes(20)


class StopKind(Enum):
    """Why the rope stopped."""

    OPERATOR = "operator stop"
    GATE = "loading gate"
    SAFETY_CIRCUIT = "safety circuit"
    ROPE_POSITION = "rope position"
    POWER = "power"
    WEATHER = "weather"
    EVACUATION = "evacuation"
    DEROPEMENT = "deropement"
    SCHEDULED = "scheduled"

    @property
    def is_reportable(self) -> bool:
        """The board hears about these whatever else happens."""
        return self in {StopKind.DEROPEMENT, StopKind.EVACUATION, StopKind.ROPE_POSITION}

    @property
    def always_needs_a_line_check(self) -> bool:
        return self in {
            StopKind.DEROPEMENT,
            StopKind.ROPE_POSITION,
            StopKind.EVACUATION,
            StopKind.SAFETY_CIRCUIT,
        }

    @property
    def counts_against_the_machine(self) -> bool:
        """A power cut and a wind hold are not the machine's fault."""
        return self not in {StopKind.POWER, StopKind.WEATHER, StopKind.SCHEDULED}


@dataclass(frozen=True)
class StopEvent:
    """One stop on one machine."""

    lift: LiftCode
    kind: StopKind
    stopped: datetime
    stopped_by: str
    note: str = ""
    restarted: datetime | None = None
    restarted_by: str | None = None
    line_checked: bool = False

    def __post_init__(self) -> None:
        if not self.stopped_by.strip():
            raise OperationsError("a stop is signed by whoever called it")
        if self.restarted is not None and self.restarted < self.stopped:
            raise OperationsError("a stop cannot end before it started")
        if (self.restarted is None) != (self.restarted_by is None):
            raise OperationsError("a restart has a time and a name, or neither")
        if self.kind.is_reportable and not self.note.strip():
            raise OperationsError(f"a {self.kind.value} is written up, not just logged")

    def is_open(self) -> bool:
        return self.restarted is None

    def length(self, now: datetime | None = None) -> Duration:
        finish = self.restarted or now
        if finish is None:
            raise OperationsError("an open stop needs a moment to be measured against")
        if finish < self.stopped:
            raise OperationsError("that moment is before the stop")
        return Duration.from_seconds(int((finish - self.stopped).total_seconds()))

    def needs_a_line_check(self, now: datetime | None = None) -> bool:
        """Some kinds always do, and any stop long enough to ice a grip does too."""
        if self.kind.always_needs_a_line_check:
            return True
        return self.length(now) >= LONG_STOP

    def may_restart(self, now: datetime) -> bool:
        if not self.is_open():
            return False
        if self.needs_a_line_check(now):
            return self.line_checked
        return True

    def restart(self, when: datetime, by: str, line_checked: bool = False) -> StopEvent:
        if not self.is_open():
            raise OperationsError("that stop has already been closed")
        checked = self.line_checked or line_checked
        restarted = StopEvent(
            lift=self.lift,
            kind=self.kind,
            stopped=self.stopped,
            stopped_by=self.stopped_by,
            note=self.note,
            restarted=when,
            restarted_by=by,
            line_checked=checked,
        )
        if restarted.needs_a_line_check() and not checked:
            raise OperationsError(f"a {self.kind.value} runs again only after a line check")
        return restarted

    def with_line_check(self) -> StopEvent:
        return StopEvent(
            lift=self.lift,
            kind=self.kind,
            stopped=self.stopped,
            stopped_by=self.stopped_by,
            note=self.note,
            restarted=self.restarted,
            restarted_by=self.restarted_by,
            line_checked=True,
        )

    def __str__(self) -> str:
        state = "running" if not self.is_open() else "still stopped"
        return f"{self.lift} {self.kind.value} at {self.stopped:%H:%M}, {state}"


@dataclass(frozen=True)
class StopLog:
    """Every stop on one machine over some window."""

    lift: LiftCode
    stops: tuple[StopEvent, ...] = ()

    def __post_init__(self) -> None:
        for stop in self.stops:
            if stop.lift != self.lift:
                raise OperationsError(f"{stop.lift} does not belong in a {self.lift} log")

    @classmethod
    def of(cls, lift: LiftCode, stops: Sequence[StopEvent] = ()) -> StopLog:
        ordered = tuple(sorted(stops, key=lambda stop: stop.stopped))
        return cls(lift, ordered)

    def of_kind(self, kind: StopKind) -> tuple[StopEvent, ...]:
        return tuple(stop for stop in self.stops if stop.kind is kind)

    def reportable(self) -> tuple[StopEvent, ...]:
        return tuple(stop for stop in self.stops if stop.kind.is_reportable)

    def open_stops(self) -> tuple[StopEvent, ...]:
        return tuple(stop for stop in self.stops if stop.is_open())

    def downtime(self) -> Duration:
        total = Duration(0)
        for stop in self.stops:
            if not stop.is_open():
                total = total + stop.length()
        return total

    def chargeable_downtime(self) -> Duration:
        """Only the stops that count against the machine."""
        total = Duration(0)
        for stop in self.stops:
            if not stop.is_open() and stop.kind.counts_against_the_machine:
                total = total + stop.length()
        return total

    def longest(self) -> StopEvent:
        closed = [stop for stop in self.stops if not stop.is_open()]
        if not closed:
            raise OperationsError("nothing in this log has been closed yet")
        return max(closed, key=lambda stop: stop.length().seconds)

    def availability(self, scheduled: Duration) -> Decimal:
        """Running time over scheduled time, with the machine's own stops taken off."""
        if scheduled.is_zero():
            raise OperationsError("availability needs a scheduled run time")
        lost = self.chargeable_downtime().seconds
        return Decimal(scheduled.seconds - lost) / scheduled.seconds

    def with_stop(self, stop: StopEvent) -> StopLog:
        return StopLog.of(self.lift, [*self.stops, stop])

    def __len__(self) -> int:
        return len(self.stops)
