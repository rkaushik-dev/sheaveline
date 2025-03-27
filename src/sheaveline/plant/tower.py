"""Towers and the sheave assemblies bolted to their crossarms.

A tower does one of three jobs and the job is decided by which way the rope
bends over it. On a rise the rope is pushed down onto support sheaves. In a dip
it is pulled up against hold down sheaves. Where the profile changes sign inside
one tower the assembly carries both, and those are the towers that give trouble,
because a rope that lifts off a hold down is a deropement waiting to happen.

The sign convention here is the one the drawings use. A positive deviation bends
the rope downward, which is a support. A negative deviation bends it upward,
which is a hold down.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from enum import Enum

from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import LiftCode, TowerRef


class AssemblyKind(Enum):
    """Which way a sheave assembly bends the rope."""

    SUPPORT = "support"
    HOLD_DOWN = "hold down"
    COMBINATION = "combination"

    @property
    def carries_down_force(self) -> bool:
        """The rope presses onto the sheaves rather than lifting off them."""
        return self in {AssemblyKind.SUPPORT, AssemblyKind.COMBINATION}

    @property
    def carries_up_force(self) -> bool:
        return self in {AssemblyKind.HOLD_DOWN, AssemblyKind.COMBINATION}


@dataclass(frozen=True)
class SheaveAssembly:
    """One side of a tower, its sheaves and the bend it puts in the rope."""

    kind: AssemblyKind
    sheaves: int
    deviation: Angle

    def __post_init__(self) -> None:
        if self.sheaves < 2 or self.sheaves % 2 != 0:
            raise PlantError(
                f"a sheave assembly has an even count of at least two, not {self.sheaves}"
            )
        if self.kind is AssemblyKind.SUPPORT and not self.deviation.is_uphill():
            raise PlantError("a support assembly bends the rope downward")
        if self.kind is AssemblyKind.HOLD_DOWN and self.deviation.millidegrees >= 0:
            raise PlantError("a hold down assembly bends the rope upward")
        if self.kind is AssemblyKind.COMBINATION and self.deviation.is_flat():
            raise PlantError("a combination assembly bends the rope one way or the other")

    def per_sheave(self, line_load: Load) -> Load:
        """How much of the rope load each sheave takes, evenly split."""
        return line_load.scaled(f"{1 / self.sheaves:.10f}")

    def is_heavier_than(self, other: SheaveAssembly) -> bool:
        return abs(self.deviation) > abs(other.deviation)

    def __str__(self) -> str:
        return f"{self.sheaves}s {self.kind.value} at {self.deviation.format_degrees()}"


@dataclass(frozen=True)
class Tower:
    """A steel tower on the line, with an assembly on each side."""

    ref: TowerRef
    station: Length
    base_elevation: Length
    height: Length
    uphill: SheaveAssembly
    downhill: SheaveAssembly

    def __post_init__(self) -> None:
        if self.station.mils < 0:
            raise PlantError(f"{self.ref} stands before the drive terminal")
        if self.height.mils <= 0:
            raise PlantError(f"{self.ref} has no height")

    @property
    def number(self) -> int:
        return self.ref.number

    @property
    def lift(self) -> LiftCode:
        return self.ref.lift

    def crossarm_elevation(self) -> Length:
        return self.base_elevation + self.height

    def total_deviation(self) -> Angle:
        return self.uphill.deviation + self.downhill.deviation

    def kinds(self) -> frozenset[AssemblyKind]:
        return frozenset({self.uphill.kind, self.downhill.kind})

    def is_hold_down(self) -> bool:
        return any(kind.carries_up_force for kind in self.kinds())

    def sheave_count(self) -> int:
        return self.uphill.sheaves + self.downhill.sheaves

    def __str__(self) -> str:
        return f"{self.ref} at station {self.station.feet:.0f}"


class TowerLine:
    """The towers of one lift, in order from the drive terminal up the hill."""

    __slots__ = ("_towers",)

    def __init__(self, towers: Sequence[Tower]) -> None:
        ordered = tuple(sorted(towers, key=lambda tower: tower.number))
        if not ordered:
            raise PlantError("a line has at least one tower")
        lifts = {tower.lift for tower in ordered}
        if len(lifts) > 1:
            raise PlantError("a tower line belongs to one lift")
        for position, tower in enumerate(ordered, start=1):
            if tower.number != position:
                raise PlantError(f"tower numbers run from one without gaps, found {tower.ref}")
        for earlier, later in zip(ordered, ordered[1:], strict=False):
            if later.station <= earlier.station:
                raise PlantError(f"{later.ref} is not further up the line than {earlier.ref}")
        self._towers = ordered

    @property
    def lift(self) -> LiftCode:
        return self._towers[0].lift

    def __len__(self) -> int:
        return len(self._towers)

    def __iter__(self) -> Iterator[Tower]:
        return iter(self._towers)

    def __getitem__(self, number: int) -> Tower:
        if not 1 <= number <= len(self._towers):
            raise PlantError(f"{self.lift} has no tower {number}")
        return self._towers[number - 1]

    def first(self) -> Tower:
        return self._towers[0]

    def last(self) -> Tower:
        return self._towers[-1]

    def hold_downs(self) -> tuple[Tower, ...]:
        return tuple(tower for tower in self._towers if tower.is_hold_down())

    def sheave_count(self) -> int:
        return sum(tower.sheave_count() for tower in self._towers)

    def steepest(self) -> Tower:
        """The tower that bends the rope hardest, which is the one to watch."""
        return max(self._towers, key=lambda tower: abs(tower.total_deviation()).millidegrees)

    def span_stations(self) -> tuple[Length, ...]:
        return tuple(tower.station for tower in self._towers)
