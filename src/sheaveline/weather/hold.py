"""Whether the weather stops a lift, and which weather it was.

The answer is never just yes. The operator on the phone wants to know what is
holding the machine, because wind and lightning behave completely differently:
one of them is over when the anemometer says so and the other is over thirty
minutes after the last strike, whatever the anemometer says.

The limits used to live in this file as a chain of tests on the lift class. They
are in the policy table now, and what is left here is the six questions
themselves, which is the part that is actually logic. Adding a seventh question
is one entry in the tuple at the bottom.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
from typing import Final

from sheaveline.measures.speed import Speed
from sheaveline.plant.lift import Lift
from sheaveline.weather.observation import Observation
from sheaveline.weather.policy import (
    LIGHTNING_RADIUS,
    MINIMUM_VISIBILITY,
    ClassPolicy,
    policy_for,
)


class HoldReason(Enum):
    """What is holding the machine."""

    GUST = "gust"
    CROSSWIND = "crosswind"
    CHILL = "wind chill"
    ICING = "icing"
    LIGHTNING = "lightning"
    VISIBILITY = "visibility"

    @property
    def clears_on_reading(self) -> bool:
        """True when a fresh reading inside the limit is enough to run again."""
        return self is not HoldReason.LIGHTNING


@dataclass(frozen=True)
class Hold:
    """One reason a machine is not turning."""

    reason: HoldReason
    detail: str

    def __str__(self) -> str:
        return f"{self.reason.value}: {self.detail}"


@dataclass(frozen=True)
class WeatherHold:
    """One machine read against one observation."""

    lift: Lift
    observation: Observation

    @property
    def policy(self) -> ClassPolicy:
        return policy_for(self.lift.lift_class)

    def gust_limit(self) -> Speed:
        return self.policy.gust

    def crosswind_limit(self) -> Speed | None:
        return self.policy.crosswind

    def holds_on_ice(self) -> bool:
        return self.policy.holds_on_ice

    def crosswind(self) -> Speed:
        return self.observation.wind.peak_across(self.lift.bearing)

    def holds(self) -> tuple[Hold, ...]:
        found = []
        for rule in _RULES:
            hold = rule(self)
            if hold is not None:
                found.append(hold)
        return tuple(found)

    def is_held(self) -> bool:
        return bool(self.holds())

    def may_run(self) -> bool:
        return not self.is_held()

    def reasons(self) -> frozenset[HoldReason]:
        return frozenset(hold.reason for hold in self.holds())

    def needs_a_timer(self) -> bool:
        """A lightning hold runs on a clock rather than on the next reading."""
        return any(not reason.clears_on_reading for reason in self.reasons())

    def __str__(self) -> str:
        if self.may_run():
            return f"{self.lift.code} clear"
        return f"{self.lift.code} held: " + ", ".join(hold.reason.value for hold in self.holds())


Rule = Callable[[WeatherHold], "Hold | None"]


def _gust(check: WeatherHold) -> Hold | None:
    peak = check.observation.wind.peak
    limit = check.policy.gust
    if peak <= limit:
        return None
    return Hold(HoldReason.GUST, f"{peak.format_mph()} against a limit of {limit.format_mph()}")


def _crosswind(check: WeatherHold) -> Hold | None:
    limit = check.policy.crosswind
    if limit is None:
        return None
    across = check.crosswind()
    if across <= limit:
        return None
    return Hold(HoldReason.CROSSWIND, f"{across.format_mph()} across the line")


def _chill(check: WeatherHold) -> Hold | None:
    chill = check.observation.chill()
    if chill >= check.policy.chill_floor:
        return None
    return Hold(HoldReason.CHILL, f"{chill.format_f(0)} at the summit")


def _icing(check: WeatherHold) -> Hold | None:
    if not check.policy.holds_on_ice or not check.observation.icing():
        return None
    return Hold(HoldReason.ICING, "ice on the grips")


def _lightning(check: WeatherHold) -> Hold | None:
    struck = check.observation.lightning_within
    if struck is None or struck > LIGHTNING_RADIUS:
        return None
    return Hold(HoldReason.LIGHTNING, f"strike within {struck.feet:.0f} ft")


def _visibility(check: WeatherHold) -> Hold | None:
    seen = check.observation.visibility
    if seen >= MINIMUM_VISIBILITY:
        return None
    return Hold(HoldReason.VISIBILITY, f"{seen.feet:.0f} ft at the terminal")


_RULES: Final[tuple[Rule, ...]] = (
    _gust,
    _crosswind,
    _chill,
    _icing,
    _lightning,
    _visibility,
)
