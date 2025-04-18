"""The lifts of one resort, held together so they can be asked about as a set.

The department is not asked about one lift very often. It is asked how many
seats the mountain turns in an hour, which machines are due for a five year
inspection, and whether the rope on a machine has been anywhere else. So the
lifts live in a registry that keeps the answers that only make sense across the
whole hill, and refuses the mistakes that only show up across it, such as the
same rope serial recorded on two machines.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from datetime import date

from sheaveline.plant.classes import LiftClass, PlantError
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.schedule.daylight import Site
from sheaveline.schedule.season import Season


@dataclass(frozen=True)
class Resort:
    """One mountain, its lifts, where it is and when it runs."""

    name: str
    site: Site
    season: Season
    lifts: tuple[Lift, ...]

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise PlantError("a resort has a name")
        if not self.lifts:
            raise PlantError(f"{self.name} has no lifts")
        codes = [lift.code for lift in self.lifts]
        if len(set(codes)) != len(codes):
            raise PlantError("two lifts share a code")
        serials = [lift.rope.serial for lift in self.lifts]
        if len(set(serials)) != len(serials):
            raise PlantError("one rope serial is recorded on two machines")

    @classmethod
    def of(cls, name: str, site: Site, season: Season, lifts: Sequence[Lift]) -> Resort:
        ordered = tuple(sorted(lifts, key=lambda lift: lift.code.value))
        return cls(name=name, site=site, season=season, lifts=ordered)

    def __len__(self) -> int:
        return len(self.lifts)

    def __iter__(self) -> Iterator[Lift]:
        return iter(self.lifts)

    def __contains__(self, code: object) -> bool:
        return any(lift.code == code for lift in self.lifts)

    def lift(self, code: LiftCode | str) -> Lift:
        wanted = code if isinstance(code, LiftCode) else LiftCode.parse(code)
        for machine in self.lifts:
            if machine.code == wanted:
                return machine
        raise PlantError(f"{self.name} has no lift {wanted}")

    def codes(self) -> tuple[LiftCode, ...]:
        return tuple(lift.code for lift in self.lifts)

    def of_class(self, lift_class: LiftClass) -> tuple[Lift, ...]:
        return tuple(lift for lift in self.lifts if lift.lift_class is lift_class)

    def aerial(self) -> tuple[Lift, ...]:
        return tuple(lift for lift in self.lifts if lift.lift_class.is_aerial)

    def surface(self) -> tuple[Lift, ...]:
        return tuple(lift for lift in self.lifts if not lift.lift_class.is_aerial)

    def detachables(self) -> tuple[Lift, ...]:
        return tuple(lift for lift in self.lifts if lift.lift_class.is_detachable)

    def seats_on_line(self) -> int:
        return sum(lift.seats_on_line() for lift in self.lifts)

    def carriers_on_line(self) -> int:
        return sum(lift.carriers_on_line() for lift in self.lifts)

    def tower_count(self) -> int:
        return sum(len(lift.towers) for lift in self.lifts)

    def sheave_count(self) -> int:
        return sum(lift.towers.sheave_count() for lift in self.lifts)

    def rope_serials(self) -> frozenset[RopeSerial]:
        return frozenset(lift.rope.serial for lift in self.lifts)

    def oldest(self) -> Lift:
        return min(self.lifts, key=lambda lift: lift.commissioned)

    def newest(self) -> Lift:
        return max(self.lifts, key=lambda lift: lift.commissioned)

    def commissioned_before(self, cutoff: date) -> tuple[Lift, ...]:
        return tuple(lift for lift in self.lifts if lift.commissioned < cutoff)

    def needing_evacuation_plans(self) -> tuple[Lift, ...]:
        return tuple(lift for lift in self.lifts if lift.lift_class.needs_evacuation_plan)

    def with_lift(self, lift: Lift) -> Resort:
        """A new registry with one more machine on it. Nothing is edited in place."""
        return Resort.of(self.name, self.site, self.season, [*self.lifts, lift])

    def without_lift(self, code: LiftCode | str) -> Resort:
        going = self.lift(code)
        remaining = [lift for lift in self.lifts if lift.code != going.code]
        return Resort.of(self.name, self.site, self.season, remaining)

    def __str__(self) -> str:
        return f"{self.name}, {len(self.lifts)} lifts"
