"""Whether the weather stops a lift, and which weather it was.

The answer is never just yes. The operator on the phone wants to know what is
holding the machine, because wind and lightning behave completely differently:
one of them is over when the anemometer says so and the other is over thirty
minutes after the last strike, whatever the anemometer says.

The limits are not the same for every machine either. A surface tow does not
care what the wind is doing across it, because there is nothing hanging off the
rope to swing. A tram with two enclosed cars will run in wind that would have
had a fixed grip triple shut for an hour.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.lift import Lift
from sheaveline.weather.observation import Observation

LIGHTNING_RADIUS: Final = Length.from_feet(26400)
MINIMUM_VISIBILITY: Final = Length.from_feet(200)
CHILL_FLOOR: Final = Temperature.from_degrees_f(-25)


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

    def gust_limit(self) -> Speed:
        lift_class = self.lift.lift_class
        if lift_class is LiftClass.SURFACE_TOW:
            return Speed.from_miles_per_hour(50)
        if lift_class is LiftClass.FIXED_CHAIR:
            return Speed.from_miles_per_hour(40)
        if lift_class is LiftClass.DETACHABLE_CHAIR:
            return Speed.from_miles_per_hour(45)
        if lift_class is LiftClass.AERIAL_TRAM:
            return Speed.from_miles_per_hour(55)
        return Speed.from_miles_per_hour(50)

    def crosswind_limit(self) -> Speed | None:
        lift_class = self.lift.lift_class
        if lift_class is LiftClass.SURFACE_TOW:
            return None
        if lift_class is LiftClass.FIXED_CHAIR:
            return Speed.from_miles_per_hour(30)
        if lift_class is LiftClass.DETACHABLE_CHAIR:
            return Speed.from_miles_per_hour(32)
        if lift_class is LiftClass.AERIAL_TRAM:
            return Speed.from_miles_per_hour(40)
        return Speed.from_miles_per_hour(35)

    def holds_on_ice(self) -> bool:
        """A grip that opens and closes is the thing ice stops."""
        return self.lift.lift_class.is_detachable

    def crosswind(self) -> Speed:
        return self.observation.wind.peak_across(self.lift.bearing)

    def holds(self) -> tuple[Hold, ...]:
        found = []
        peak = self.observation.wind.peak
        if peak > self.gust_limit():
            found.append(
                Hold(
                    HoldReason.GUST,
                    f"{peak.format_mph()} against a limit of {self.gust_limit().format_mph()}",
                )
            )
        limit = self.crosswind_limit()
        if limit is not None and self.crosswind() > limit:
            found.append(
                Hold(
                    HoldReason.CROSSWIND,
                    f"{self.crosswind().format_mph()} across the line",
                )
            )
        if self.observation.chill() < CHILL_FLOOR:
            found.append(
                Hold(
                    HoldReason.CHILL,
                    f"{self.observation.chill().format_f(0)} at the summit",
                )
            )
        if self.holds_on_ice() and self.observation.icing():
            found.append(Hold(HoldReason.ICING, "ice on the grips"))
        if (
            self.observation.lightning_within is not None
            and self.observation.lightning_within <= LIGHTNING_RADIUS
        ):
            found.append(
                Hold(
                    HoldReason.LIGHTNING,
                    f"strike within {self.observation.lightning_within.feet:.0f} ft",
                )
            )
        if self.observation.visibility < MINIMUM_VISIBILITY:
            found.append(
                Hold(
                    HoldReason.VISIBILITY,
                    f"{self.observation.visibility.feet:.0f} ft at the terminal",
                )
            )
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
