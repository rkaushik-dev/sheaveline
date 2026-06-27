"""When the rope runs out, worked out from the walks rather than from the calendar.

A verdict answers whether the rope may run today. It is the wrong question in
September, because a haul rope is eighteen weeks from the mill and the purchase
order has to go in before anybody can see the wear that will justify it. What the
department needs in September is when the rope is going to reach the condemning
diameter, and the only honest source for that is the walks already in the file.

The projection is a straight line through the worst station's readings, fitted on
rope travel and not on days. A rope that ran three hundred hours in a thin winter
and a thousand in a good one has not aged at the same rate, and dating the trend
by the calendar makes a quiet season look like the rope stopped wearing.

Two readings is the least that can define a line and it is not enough to trust
one, so the forecast says how many walks it was drawn from and refuses outright
under two. A department reading a projection off two points should know that is
what it is doing.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal

from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.wear import RopeworkError, WearSurvey

LEAD_TIME_DAYS = 126
LEAST_WALKS = 2


@dataclass(frozen=True)
class WearPoint:
    """One walk reduced to the number the trend is drawn through."""

    taken: date
    smallest: Length

    def __str__(self) -> str:
        return f"{self.taken.isoformat()} {self.smallest.format_inches(3)}"


@dataclass(frozen=True)
class WearTrend:
    """A straight line through the worst station on every walk in the file."""

    rope: HaulRope
    points: tuple[WearPoint, ...]

    def __post_init__(self) -> None:
        if len(self.points) < LEAST_WALKS:
            raise RopeworkError(
                f"a trend needs {LEAST_WALKS} walks and this rope has {len(self.points)}"
            )
        days = [one.taken for one in self.points]
        if len(set(days)) != len(days):
            raise RopeworkError("two walks on the same day cannot both be the trend")

    @classmethod
    def of(cls, rope: HaulRope, surveys: Sequence[WearSurvey]) -> WearTrend:
        for survey in surveys:
            if survey.rope.serial != rope.serial:
                raise RopeworkError(f"that walk is on {survey.rope.serial}")
        points = [
            WearPoint(taken=one.taken, smallest=one.smallest().mean())
            for one in sorted(surveys, key=lambda one: one.taken)
        ]
        return cls(rope, tuple(points))

    def first(self) -> WearPoint:
        return self.points[0]

    def last(self) -> WearPoint:
        return self.points[-1]

    def walks(self) -> int:
        return len(self.points)

    def days_covered(self) -> int:
        return (self.last().taken - self.first().taken).days

    def worn(self) -> Length:
        """How much diameter has gone since the first walk in the file."""
        return self.first().smallest - self.last().smallest

    def left(self) -> Length:
        """How much there is between where it is now and where it comes off."""
        return self.last().smallest - self.rope.spec.condemning_diameter()

    def mils_a_day(self) -> Decimal:
        covered = self.days_covered()
        if covered <= 0:
            raise RopeworkError("the walks in the file are all on one day")
        return Decimal(self.worn().mils) / covered

    def is_wearing(self) -> bool:
        """A rope that has not measurably lost anything cannot be projected."""
        return self.worn().mils > 0


@dataclass(frozen=True)
class Retirement:
    """When the rope comes off, and whether that leaves time to buy the next one."""

    trend: WearTrend
    on: date
    lead_time: int = LEAD_TIME_DAYS

    def __post_init__(self) -> None:
        if self.lead_time < 0:
            raise RopeworkError("a lead time is not negative")

    def is_projectable(self) -> bool:
        return self.trend.is_wearing() and self.trend.days_covered() > 0

    def days_left(self) -> int | None:
        """Nothing rather than a number, when the file will not carry a line."""
        if not self.is_projectable():
            return None
        left = self.trend.left()
        if left.mils <= 0:
            return 0
        return int(Decimal(left.mils) / self.trend.mils_a_day())

    def comes_off(self) -> date | None:
        remaining = self.days_left()
        if remaining is None:
            return None
        return self.trend.last().taken + timedelta(days=remaining)

    def order_by(self) -> date | None:
        """The last day a purchase order can go in and still beat the wear."""
        wanted = self.comes_off()
        if wanted is None:
            return None
        return wanted - timedelta(days=self.lead_time)

    def is_overdue_to_order(self) -> bool:
        wanted = self.order_by()
        return wanted is not None and self.on >= wanted

    def survives(self, until: date) -> bool | None:
        """Whether the rope reaches a day, which is what a season plan asks."""
        wanted = self.comes_off()
        if wanted is None:
            return None
        return wanted > until

    def confidence(self) -> str:
        """How much of a line the file actually carries, said in one word."""
        walks = self.trend.walks()
        if walks >= 5 and self.trend.days_covered() >= 365:
            return "settled"
        if walks >= 3:
            return "provisional"
        return "two points"

    def __str__(self) -> str:
        wanted = self.comes_off()
        if wanted is None:
            return f"{self.trend.rope.serial} is not wearing measurably yet"
        return (
            f"{self.trend.rope.serial} reaches condemning diameter "
            f"{wanted.isoformat()}, order by {self.order_by()}"
        )


def forecast(
    rope: HaulRope,
    surveys: Sequence[WearSurvey],
    on: date,
    lead_time: int = LEAD_TIME_DAYS,
) -> Retirement:
    return Retirement(WearTrend.of(rope, surveys), on, lead_time)


def orderable(
    ropes: Sequence[tuple[HaulRope, Sequence[WearSurvey]]],
    on: date,
    lead_time: int = LEAD_TIME_DAYS,
) -> tuple[Retirement, ...]:
    """Every rope whose replacement should already be on order, soonest first."""
    found = []
    for rope, surveys in ropes:
        if len(surveys) < LEAST_WALKS:
            continue
        made = forecast(rope, surveys, on, lead_time)
        if made.is_overdue_to_order():
            found.append(made)
    return tuple(sorted(found, key=lambda one: one.comes_off() or date.max))
