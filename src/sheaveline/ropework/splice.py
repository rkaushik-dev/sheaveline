"""Splices, and what happens to a rope when a bad piece is cut out of it.

A haul rope is a loop, so it has at least one splice in it and every splice is a
place where the rope is not quite the rope. The mill splice is made when the
rope goes on. Every one after that is the record of something going wrong: a
section came out, the ends were tucked back together, and the rope got shorter.

Shorter matters. The tension carriage has a fixed travel and every shortening
moves it toward one end of it, so a rope that has been cut three times can be
perfectly sound and still be finished, because the carriage has nowhere left to
go.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from typing import Final

from sheaveline.measures.length import Length
from sheaveline.plant.rope import HaulRope, RopeSpec
from sheaveline.ropework.wear import RopeworkError

LONG_SPLICE_DIAMETERS: Final = 1200
MINIMUM_SEPARATION_DIAMETERS: Final = 3000
MAXIMUM_SPLICES: Final = 4


@dataclass(frozen=True)
class Splice:
    """One splice, where it is and who made it."""

    station: Length
    made: date
    made_by: str
    tucks: int
    length: Length
    mill: bool = False

    def __post_init__(self) -> None:
        if self.station.mils < 0:
            raise RopeworkError("a splice station is measured from the mill splice, forwards")
        if not self.made_by.strip():
            raise RopeworkError("a splice is signed by whoever made it")
        if self.tucks < 4:
            raise RopeworkError("a long splice carries at least four tucks a strand")
        if self.length.mils <= 0:
            raise RopeworkError("a splice has a length")

    def minimum_length(self, spec: RopeSpec) -> Length:
        return spec.nominal_diameter.scaled(LONG_SPLICE_DIAMETERS)

    def is_long_enough(self, spec: RopeSpec) -> bool:
        return self.length >= self.minimum_length(spec)

    def age_in_days(self, on: date) -> int:
        if on < self.made:
            raise RopeworkError(f"{on.isoformat()} is before the splice was made")
        return (on - self.made).days

    def __str__(self) -> str:
        made = "mill splice" if self.mill else "field splice"
        return f"{made} at {self.station.feet:.0f} ft, {self.made.isoformat()}"


@dataclass(frozen=True)
class SpliceRegister:
    """Every splice in one rope, and what they add up to."""

    rope: HaulRope
    splices: tuple[Splice, ...]

    def __post_init__(self) -> None:
        if not self.splices:
            raise RopeworkError("a loop of rope has at least the mill splice in it")
        mills = [splice for splice in self.splices if splice.mill]
        if len(mills) != 1:
            raise RopeworkError("a rope has exactly one mill splice")
        stations = [splice.station.mils for splice in self.splices]
        if len(set(stations)) != len(stations):
            raise RopeworkError("two splices cannot sit at the same station")
        for splice in self.splices:
            if splice.station > self.rope.length:
                raise RopeworkError(f"station {splice.station.feet:.0f} is past the end")

    @classmethod
    def of(cls, rope: HaulRope, splices: Sequence[Splice]) -> SpliceRegister:
        ordered = tuple(sorted(splices, key=lambda splice: splice.station.mils))
        return cls(rope, ordered)

    def count(self) -> int:
        return len(self.splices)

    def field_splices(self) -> tuple[Splice, ...]:
        return tuple(splice for splice in self.splices if not splice.mill)

    def minimum_separation(self) -> Length:
        return self.rope.diameter.scaled(MINIMUM_SEPARATION_DIAMETERS)

    def separations(self) -> tuple[Length, ...]:
        """Gaps between splices round the loop, including the wrap past the end."""
        if len(self.splices) == 1:
            return (self.rope.length,)
        gaps = []
        for earlier, later in zip(self.splices, self.splices[1:], strict=False):
            gaps.append(later.station - earlier.station)
        wrap = self.rope.length - self.splices[-1].station + self.splices[0].station
        gaps.append(wrap)
        return tuple(gaps)

    def too_close(self) -> tuple[Length, ...]:
        limit = self.minimum_separation()
        return tuple(gap for gap in self.separations() if gap < limit)

    def short_splices(self) -> tuple[Splice, ...]:
        return tuple(splice for splice in self.splices if not splice.is_long_enough(self.rope.spec))

    def nearest_to(self, station: Length) -> Splice:
        return min(
            self.splices,
            key=lambda splice: abs((splice.station - station).mils),
        )

    def is_acceptable(self) -> bool:
        if self.count() > MAXIMUM_SPLICES:
            return False
        if self.too_close():
            return False
        return not self.short_splices()

    def shortened_by(self, removed: Length, splice: Splice) -> SpliceRegister:
        """Cut a piece out and tuck the ends together. The rope gets shorter."""
        if removed.mils <= 0:
            raise RopeworkError("a shortening removes a positive length")
        if removed >= self.rope.length:
            raise RopeworkError("that would remove the whole rope")
        if splice.mill:
            raise RopeworkError("a field splice is not a mill splice")
        shorter = HaulRope(
            serial=self.rope.serial,
            spec=self.rope.spec,
            installed=self.rope.installed,
            length=self.rope.length - removed,
            splices=self.rope.splices + 1,
        )
        return SpliceRegister.of(shorter, [*self.splices, splice])

    def total_removed(self, original: Length) -> Length:
        return original - self.rope.length
