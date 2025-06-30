"""The hold register, and the rules about putting a machine back on line.

Stopping a lift is easy and takes one person. Starting it again is the part that
gets written down, because two different clocks run and only one of them is the
anemometer. A wind hold is over when a fresh reading says it is. A lightning
hold is over thirty minutes after the last strike inside the radius, and it does
not matter at all what the sky looks like at minute twenty nine.

So a hold that was opened for two reasons has to be cleared against both, and
the register is the only place that knows which reasons those were.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Final

from sheaveline.measures.duration import Duration
from sheaveline.plant.identity import LiftCode
from sheaveline.weather.hold import HoldReason, WeatherHold
from sheaveline.weather.observation import Observation, WeatherError

LIGHTNING_WAIT: Final = Duration.from_minutes(30)


@dataclass(frozen=True)
class HoldRecord:
    """One machine held for one reason, with who did it and when."""

    lift: LiftCode
    reason: HoldReason
    started: datetime
    started_by: str
    detail: str = ""
    ended: datetime | None = None
    ended_by: str | None = None

    def __post_init__(self) -> None:
        if not self.started_by.strip():
            raise WeatherError("a hold is signed by whoever called it")
        if self.ended is not None and self.ended < self.started:
            raise WeatherError("a hold cannot end before it started")
        if (self.ended is None) != (self.ended_by is None):
            raise WeatherError("a hold is closed by somebody at some time, or neither")

    def is_open(self) -> bool:
        return self.ended is None

    def length(self, now: datetime | None = None) -> Duration:
        finish = self.ended or now
        if finish is None:
            raise WeatherError("an open hold needs a moment to be measured against")
        if finish < self.started:
            raise WeatherError("that moment is before the hold started")
        return Duration.from_seconds(int((finish - self.started).total_seconds()))

    def closed(self, when: datetime, by: str) -> HoldRecord:
        if not self.is_open():
            raise WeatherError(f"the {self.reason.value} hold on {self.lift} is already closed")
        return HoldRecord(
            lift=self.lift,
            reason=self.reason,
            started=self.started,
            started_by=self.started_by,
            detail=self.detail,
            ended=when,
            ended_by=by,
        )

    def __str__(self) -> str:
        state = "open" if self.is_open() else f"closed {self.ended:%H:%M}"
        return f"{self.lift} {self.reason.value} from {self.started:%H:%M}, {state}"


@dataclass(frozen=True)
class HoldRegister:
    """Every hold called this season, open and closed."""

    records: tuple[HoldRecord, ...] = ()

    @classmethod
    def of(cls, records: Sequence[HoldRecord] = ()) -> HoldRegister:
        ordered = tuple(sorted(records, key=lambda record: record.started))
        return cls(ordered)

    def open_holds(self, lift: LiftCode | None = None) -> tuple[HoldRecord, ...]:
        return tuple(
            record
            for record in self.records
            if record.is_open() and (lift is None or record.lift == lift)
        )

    def is_held(self, lift: LiftCode) -> bool:
        return bool(self.open_holds(lift))

    def open_reasons(self, lift: LiftCode) -> frozenset[HoldReason]:
        return frozenset(record.reason for record in self.open_holds(lift))

    def call(self, decision: WeatherHold, when: datetime, by: str) -> HoldRegister:
        """Open a record for every reason not already open on that machine."""
        already = self.open_reasons(decision.lift.code)
        fresh = [
            HoldRecord(
                lift=decision.lift.code,
                reason=hold.reason,
                started=when,
                started_by=by,
                detail=hold.detail,
            )
            for hold in decision.holds()
            if hold.reason not in already
        ]
        return HoldRegister.of([*self.records, *fresh])

    def may_clear(
        self,
        record: HoldRecord,
        now: datetime,
        reading: Observation,
        decision: WeatherHold,
    ) -> bool:
        """Whether that one hold can come off, which depends on which hold it is."""
        if not record.is_open():
            return False
        if record.reason is HoldReason.LIGHTNING:
            return self._lightning_served(now, reading)
        return record.reason not in decision.reasons()

    def _lightning_served(self, now: datetime, reading: Observation) -> bool:
        if reading.has_lightning():
            return False
        waited = now - reading.taken
        return waited >= timedelta(seconds=LIGHTNING_WAIT.seconds)

    def clear(self, record: HoldRecord, when: datetime, by: str) -> HoldRegister:
        if record not in self.records:
            raise WeatherError("that hold is not in this register")
        closed = record.closed(when, by)
        remaining = [other for other in self.records if other is not record]
        return HoldRegister.of([*remaining, closed])

    def longest(self) -> HoldRecord:
        closed = [record for record in self.records if not record.is_open()]
        if not closed:
            raise WeatherError("nothing in this register has been closed yet")
        return max(closed, key=lambda record: record.length().seconds)

    def by_reason(self, reason: HoldReason) -> tuple[HoldRecord, ...]:
        return tuple(record for record in self.records if record.reason is reason)

    def total_held(self, lift: LiftCode) -> Duration:
        total = Duration(0)
        for record in self.records:
            if record.lift == lift and not record.is_open():
                total = total + record.length()
        return total

    def __len__(self) -> int:
        return len(self.records)
