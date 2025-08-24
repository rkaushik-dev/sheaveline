"""The weather limits, one row a class.

These were a chain of tests inside the hold engine and they should never have
been. Every one of them is a number somebody at the board or the manufacturer
decided, they change without any code changing around them, and the crew asks to
see them written down about once a month. So they are a table, and the engine
reads it.

The chill floor is per class because it is about the people rather than the
machine. A cabin with a door on it will run in weather that would give somebody
frostbite on an open chair, and a surface lift is held to the same figure as a
chair because the rider is standing in it.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.plant.classes import LiftClass

LIGHTNING_RADIUS: Final = Length.from_feet(26400)
MINIMUM_VISIBILITY: Final = Length.from_feet(200)


@dataclass(frozen=True)
class ClassPolicy:
    """Everything the weather rules need to know about one class of machine."""

    gust: Speed
    crosswind: Speed | None
    chill_floor: Temperature
    holds_on_ice: bool

    @classmethod
    def of(
        cls,
        gust_mph: int,
        crosswind_mph: int | None,
        chill_floor_f: int,
        holds_on_ice: bool,
    ) -> ClassPolicy:
        return cls(
            gust=Speed.from_miles_per_hour(gust_mph),
            crosswind=None if crosswind_mph is None else Speed.from_miles_per_hour(crosswind_mph),
            chill_floor=Temperature.from_degrees_f(chill_floor_f),
            holds_on_ice=holds_on_ice,
        )

    def cares_about_crosswind(self) -> bool:
        return self.crosswind is not None


POLICY: Final[dict[LiftClass, ClassPolicy]] = {
    LiftClass.FIXED_CHAIR: ClassPolicy.of(40, 30, -25, holds_on_ice=False),
    LiftClass.DETACHABLE_CHAIR: ClassPolicy.of(45, 32, -25, holds_on_ice=True),
    LiftClass.PULSE_GONDOLA: ClassPolicy.of(50, 35, -35, holds_on_ice=False),
    LiftClass.DETACHABLE_GONDOLA: ClassPolicy.of(50, 35, -35, holds_on_ice=True),
    LiftClass.AERIAL_TRAM: ClassPolicy.of(55, 40, -35, holds_on_ice=False),
    LiftClass.SURFACE_TOW: ClassPolicy.of(50, None, -25, holds_on_ice=False),
}


def policy_for(lift_class: LiftClass) -> ClassPolicy:
    return POLICY[lift_class]
