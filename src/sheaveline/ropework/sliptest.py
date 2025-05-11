"""Grip slip tests, and the sample size that changes when one fails.

A detachable grip is asked to do two contradictory things. It has to let go
cleanly in the terminal and it has to hold absolutely on the line, and the only
way to know it still does the second is to pull one until it slips and see where
it goes. So a sample of the fleet comes off every season and gets tested on the
bench.

The sample size is where this gets interesting. Ten percent of the fleet in an
ordinary season, never fewer than four grips. But a failure anywhere in the
sample means the sample was not big enough to trust, and the whole fleet gets
tested that season. So the rule that decides how many grips to test depends on
the result of testing them, which is why it is written here once and read
everywhere else.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Final

from sheaveline.measures.load import Load
from sheaveline.plant.carrier import CarrierSet, Grip
from sheaveline.plant.classes import GripType
from sheaveline.ropework.wear import RopeworkError

SAMPLE_FRACTION: Final = Decimal("0.10")
MINIMUM_SAMPLE: Final = 4
SLIP_TOLERANCE: Final = Decimal("0.15")


@dataclass(frozen=True)
class SlipTest:
    """One grip pulled on the bench until it moved."""

    carrier: int
    tested: date
    tester: str
    slipped_at: Load
    rated: Load

    def __post_init__(self) -> None:
        if self.carrier < 1:
            raise RopeworkError("a slip test names the carrier it came off")
        if not self.tester.strip():
            raise RopeworkError("a slip test is signed by whoever ran it")
        if self.slipped_at.pounds <= 0:
            raise RopeworkError("a grip that never moved has no slip figure")
        if self.rated.pounds <= 0:
            raise RopeworkError("a slip test is read against a rated figure")

    @classmethod
    def of(cls, carrier: int, tested: date, tester: str, slipped_at: Load, grip: Grip) -> SlipTest:
        if grip.kind is not GripType.DETACHABLE:
            raise RopeworkError("only a detachable grip goes on the slip bench")
        return cls(carrier, tested, tester, slipped_at, grip.rated_slip)

    def band(self) -> tuple[Load, Load]:
        margin = self.rated.scaled(SLIP_TOLERANCE)
        return (self.rated - margin, self.rated + margin)

    def deviation(self) -> Decimal:
        return (self.slipped_at - self.rated).ratio(self.rated)

    def passed(self) -> bool:
        low, high = self.band()
        return low <= self.slipped_at <= high

    def slipped_early(self) -> bool:
        return self.slipped_at < self.band()[0]

    def __str__(self) -> str:
        outcome = "pass" if self.passed() else "fail"
        return f"carrier {self.carrier} slipped at {self.slipped_at.format_pounds()}, {outcome}"


@dataclass(frozen=True)
class SlipProgramme:
    """A season of slip testing on one lift."""

    fleet: CarrierSet
    season_label: str
    tests: tuple[SlipTest, ...]
    previous_season_failed: bool = False

    def __post_init__(self) -> None:
        if not self.season_label.strip():
            raise RopeworkError("a slip programme names the season it belongs to")
        numbers = [test.carrier for test in self.tests]
        if len(set(numbers)) != len(numbers):
            raise RopeworkError("a carrier is slip tested once a season")
        on_line = {carrier.number for carrier in self.fleet}
        for number in numbers:
            if number not in on_line:
                raise RopeworkError(f"carrier {number} is not on this lift")

    @classmethod
    def of(
        cls,
        fleet: CarrierSet,
        season_label: str,
        tests: Sequence[SlipTest] = (),
        previous_season_failed: bool = False,
    ) -> SlipProgramme:
        return cls(fleet, season_label, tuple(tests), previous_season_failed)

    def fleet_size(self) -> int:
        return len(self.fleet.on_line())

    def ordinary_sample(self) -> int:
        wanted = int((Decimal(self.fleet_size()) * SAMPLE_FRACTION).to_integral_value("ROUND_UP"))
        return max(wanted, MINIMUM_SAMPLE)

    def required_sample(self) -> int:
        """Ten percent, unless something has failed, in which case all of them."""
        if self.previous_season_failed or self.failures():
            return self.fleet_size()
        return min(self.ordinary_sample(), self.fleet_size())

    def tested_count(self) -> int:
        return len(self.tests)

    def failures(self) -> tuple[SlipTest, ...]:
        return tuple(test for test in self.tests if not test.passed())

    def early_slips(self) -> tuple[SlipTest, ...]:
        return tuple(test for test in self.tests if test.slipped_early())

    def outstanding(self) -> int:
        return max(0, self.required_sample() - self.tested_count())

    def is_complete(self) -> bool:
        return self.outstanding() == 0

    def untested_carriers(self) -> tuple[int, ...]:
        done = {test.carrier for test in self.tests}
        return tuple(
            carrier.number for carrier in self.fleet.on_line() if carrier.number not in done
        )

    def with_test(self, test: SlipTest) -> SlipProgramme:
        return SlipProgramme.of(
            self.fleet,
            self.season_label,
            [*self.tests, test],
            self.previous_season_failed,
        )

    def __str__(self) -> str:
        return (
            f"{self.season_label} slip tests, "
            f"{self.tested_count()} of {self.required_sample()} done"
        )
