"""What the total station says the towers have done since they were built.

Towers move. Frost gets under a footing and lifts it an inch a winter, a fill
settles for twenty years after it is placed, and a tower on a sidehill leans
downslope because that is what sidehills do. None of that is a defect on its own.
It becomes one when the crossarm is far enough out of plumb that the sheave train
does not sit under the rope any more, and the whole point of shooting the towers
every autumn is to see it coming rather than to find it in a deropement report.

Two measures and they are not the same question. Settlement is the footing going
down and it shows up as an elevation change against the as built drawing.
Lean is the top going sideways and it shows up as an offset measured from the
base, which turns into an angle only when the tower's own height is known. A
short tower and a tall one with the same offset are not in the same trouble, so
the height is asked for rather than assumed.

The trend is per tower, not per line. Two towers on the same lift with the same
absolute movement are not the same finding, because the one on the fill is doing
what a fill does and the one on rock is telling you something.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from sheaveline.measures.angle import Angle
from sheaveline.measures.length import Length
from sheaveline.plant.classes import PlantError
from sheaveline.plant.identity import TowerRef
from sheaveline.plant.tower import Tower, TowerLine

PLUMB_TOLERANCE = Angle.from_degrees("0.50")
SETTLEMENT_TOLERANCE = Length.from_inches("2.000")
LEAST_SHOTS = 2


@dataclass(frozen=True)
class TowerShot:
    """One tower, shot once, against the drawing it was built to."""

    ref: TowerRef
    station: Length
    crossarm_elevation: Length
    offset_at_top: Length

    def __post_init__(self) -> None:
        if self.station.mils < 0:
            raise PlantError(f"{self.ref} cannot be behind the bottom terminal")

    def lean(self, height: Length) -> Angle:
        """The offset turned into an angle, which needs the tower's own height."""
        if height.mils <= 0:
            raise PlantError(f"{self.ref} has no height to lean over")
        return Angle.from_rise_run(self.offset_at_top, height)

    def is_plumb(self, height: Length, tolerance: Angle = PLUMB_TOLERANCE) -> bool:
        return abs(self.lean(height).millidegrees) <= tolerance.millidegrees

    def __str__(self) -> str:
        return f"{self.ref} at {self.station.feet:.0f} ft, top out {self.offset_at_top}"


@dataclass(frozen=True)
class AlignmentSurvey:
    """One autumn's shots on one machine."""

    line: TowerLine
    taken: date
    surveyor: str
    shots: tuple[TowerShot, ...]

    def __post_init__(self) -> None:
        if not self.surveyor.strip():
            raise PlantError("a survey names who shot it")
        refs = [one.ref for one in self.shots]
        if len(set(refs)) != len(refs):
            raise PlantError("a tower is shot once in a survey")
        for ref in refs:
            if ref.lift != self.line.lift:
                raise PlantError(f"{ref} is not on {self.line.lift}")

    @classmethod
    def of(
        cls, line: TowerLine, taken: date, surveyor: str, shots: Sequence[TowerShot]
    ) -> AlignmentSurvey:
        ordered = tuple(sorted(shots, key=lambda one: one.ref.number))
        return cls(line, taken, surveyor, ordered)

    def tower(self, ref: TowerRef) -> Tower:
        for one in self.line:
            if one.ref == ref:
                return one
        raise PlantError(f"there is no {ref} on this line")

    def shot(self, ref: TowerRef) -> TowerShot | None:
        for one in self.shots:
            if one.ref == ref:
                return one
        return None

    def missed(self) -> tuple[TowerRef, ...]:
        """Towers on the line that nobody shot, which is what the file is for."""
        seen = {one.ref for one in self.shots}
        return tuple(one.ref for one in self.line if one.ref not in seen)

    def settlement(self, ref: TowerRef) -> Length:
        """How far the crossarm has come down since the drawing."""
        found = self.shot(ref)
        if found is None:
            raise PlantError(f"{ref} was not shot on {self.taken.isoformat()}")
        return self.tower(ref).crossarm_elevation() - found.crossarm_elevation

    def lean(self, ref: TowerRef) -> Angle:
        found = self.shot(ref)
        if found is None:
            raise PlantError(f"{ref} was not shot on {self.taken.isoformat()}")
        return found.lean(self.tower(ref).height)

    def out_of_plumb(self, tolerance: Angle = PLUMB_TOLERANCE) -> tuple[TowerRef, ...]:
        return tuple(
            one.ref for one in self.shots if not one.is_plumb(self.tower(one.ref).height, tolerance)
        )

    def settled_too_far(self, limit: Length = SETTLEMENT_TOLERANCE) -> tuple[TowerRef, ...]:
        return tuple(one.ref for one in self.shots if self.settlement(one.ref) > limit)

    def is_clean(self) -> bool:
        return not self.out_of_plumb() and not self.settled_too_far() and not self.missed()

    def findings(self) -> tuple[str, ...]:
        found = [f"{ref} was not shot" for ref in self.missed()]
        for ref in self.out_of_plumb():
            found.append(f"{ref} leans {self.lean(ref).format_degrees()}")
        for ref in self.settled_too_far():
            found.append(f"{ref} has settled {self.settlement(ref).format_inches(2)}")
        return tuple(found)

    def __len__(self) -> int:
        return len(self.shots)


@dataclass(frozen=True)
class AlignmentHistory:
    """Every autumn's shots, so a tower's movement can be read as a rate."""

    surveys: tuple[AlignmentSurvey, ...]

    def __post_init__(self) -> None:
        if not self.surveys:
            raise PlantError("a history has surveys in it")
        days = [one.taken for one in self.surveys]
        if len(set(days)) != len(days):
            raise PlantError("two surveys on the same day cannot both be the trend")

    @classmethod
    def of(cls, surveys: Sequence[AlignmentSurvey]) -> AlignmentHistory:
        return cls(tuple(sorted(surveys, key=lambda one: one.taken)))

    def latest(self) -> AlignmentSurvey:
        return self.surveys[-1]

    def first(self) -> AlignmentSurvey:
        return self.surveys[0]

    def shots_of(self, ref: TowerRef) -> tuple[tuple[date, TowerShot], ...]:
        return tuple(
            (one.taken, found) for one in self.surveys if (found := one.shot(ref)) is not None
        )

    def lean_a_year(self, ref: TowerRef) -> Angle | None:
        """How fast the top is going over, or nothing if the file will not carry a line."""
        shots = self.shots_of(ref)
        if len(shots) < LEAST_SHOTS:
            return None
        (early, first), (late, last) = shots[0], shots[-1]
        years = Decimal((late - early).days) / 365
        if years <= 0:
            return None
        moved = last.offset_at_top - first.offset_at_top
        height = self.latest().tower(ref).height
        rate = Angle.from_rise_run(moved, height).millidegrees
        return Angle(int(Decimal(rate) / years))

    def settling_a_year(self, ref: TowerRef) -> Length | None:
        shots = self.shots_of(ref)
        if len(shots) < LEAST_SHOTS:
            return None
        (early, first), (late, last) = shots[0], shots[-1]
        years = Decimal((late - early).days) / 365
        if years <= 0:
            return None
        dropped = first.crossarm_elevation.mils - last.crossarm_elevation.mils
        return Length(int(Decimal(dropped) / years))

    def years_until_out_of_plumb(
        self, ref: TowerRef, tolerance: Angle = PLUMB_TOLERANCE
    ) -> Decimal | None:
        """When the lean reaches the tolerance, which is the number worth budgeting on."""
        rate = self.lean_a_year(ref)
        if rate is None or rate.millidegrees <= 0:
            return None
        now = abs(self.latest().lean(ref).millidegrees)
        left = tolerance.millidegrees - now
        if left <= 0:
            return Decimal(0)
        return Decimal(left) / rate.millidegrees

    def moving(self, tolerance: Angle = PLUMB_TOLERANCE) -> tuple[TowerRef, ...]:
        """Every tower going over, soonest first. The ones on rock come first."""
        found = []
        for one in self.latest().shots:
            years = self.years_until_out_of_plumb(one.ref, tolerance)
            if years is not None:
                found.append((years, one.ref))
        return tuple(ref for _, ref in sorted(found, key=lambda pair: pair[0]))

    def __len__(self) -> int:
        return len(self.surveys)
