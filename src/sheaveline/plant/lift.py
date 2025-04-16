"""The lift record, which is where all the other records have to agree.

Everything up to here describes one part of a machine and checks itself. This is
the record that checks the parts against each other, and it is the only place
that can. A quad chair with six place carriers, a rope too thick for the
bullwheel, a tower standing above the top terminal, a design speed the class
does not allow, a survey that stops halfway up the line. Each of those is
consistent inside its own record and wrong the moment the records are put
together.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from sheaveline.measures.angle import Bearing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.carrier import CarrierSet
from sheaveline.plant.classes import LiftClass, PlantError
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.profile import LineProfile
from sheaveline.plant.rope import HaulRope
from sheaveline.plant.span import SpanLine, Support
from sheaveline.plant.terminal import Terminal, check_pair
from sheaveline.plant.tower import TowerLine

ROPE_LENGTH_TOLERANCE = Length.from_feet(40)


@dataclass(frozen=True)
class Lift:
    """One machine, described well enough to answer for itself."""

    code: LiftCode
    name: str
    lift_class: LiftClass
    bearing: Bearing
    bottom: Terminal
    top: Terminal
    towers: TowerLine
    carriers: CarrierSet
    rope: HaulRope
    ground: LineProfile
    design_speed: Speed
    commissioned: date

    def __post_init__(self) -> None:
        check_pair(self.bottom, self.top)
        self._check_towers()
        self._check_carriers()
        self._check_rope()
        self._check_speed()
        self._check_survey()

    def _check_towers(self) -> None:
        if self.towers.lift != self.code:
            raise PlantError(f"the tower line belongs to {self.towers.lift}, not {self.code}")
        if self.towers.first().station <= self.bottom.station:
            raise PlantError(f"{self.towers.first().ref} stands below the bottom terminal")
        if self.towers.last().station >= self.top.station:
            raise PlantError(f"{self.towers.last().ref} stands above the top terminal")

    def _check_carriers(self) -> None:
        self.carriers.require_suits(self.lift_class)

    def _check_rope(self) -> None:
        for terminal in (self.bottom, self.top):
            if not terminal.bullwheel.accepts_rope(self.rope.diameter):
                raise PlantError(f"the rope is too thick for the bullwheel at {terminal.label}")
        if self.rope.installed < self.commissioned:
            raise PlantError(f"rope {self.rope.serial} went on before {self.code} was built")

    def _check_speed(self) -> None:
        if self.design_speed.is_stopped():
            raise PlantError(f"{self.code} has no design speed")
        if self.design_speed > self.lift_class.line_speed_limit:
            raise PlantError(f"{self.design_speed} is above what a {self.lift_class.value} may run")

    def _check_survey(self) -> None:
        if not self.ground.covers(self.bottom.station):
            raise PlantError("the survey does not reach the bottom terminal")
        if not self.ground.covers(self.top.station):
            raise PlantError("the survey does not reach the top terminal")

    def spans(self) -> SpanLine:
        bottom = Support.terminal(
            self.bottom.label, self.bottom.station, self.bottom.rope_elevation
        )
        top = Support.terminal(self.top.label, self.top.station, self.top.rope_elevation)
        return SpanLine.between(bottom, self.towers, top)

    def drive_terminal(self) -> Terminal:
        return self.bottom if self.bottom.role.drives else self.top

    def tension_terminal(self) -> Terminal:
        return self.bottom if self.bottom.role.tensions else self.top

    def horizontal_length(self) -> Length:
        return self.top.station - self.bottom.station

    def vertical_rise(self) -> Length:
        return self.top.rope_elevation - self.bottom.rope_elevation

    def slope_length(self) -> Length:
        return self.spans().slope_length()

    def expected_rope_length(self) -> Length:
        """Twice up the hill plus half a turn round each bullwheel."""
        wraps = self.bottom.bullwheel.diameter + self.top.bullwheel.diameter
        return self.slope_length().scaled(2) + wraps.scaled(Decimal(str(math.pi)) / 2)

    def rope_length_agrees(self, tolerance: Length = ROPE_LENGTH_TOLERANCE) -> bool:
        difference = abs(self.rope.length - self.expected_rope_length())
        return difference <= tolerance

    def ride_time(self) -> Duration:
        return self.design_speed.time_to_cover(self.slope_length())

    def carriers_on_line(self) -> int:
        return len(self.carriers.on_line())

    def seats_on_line(self) -> int:
        return self.carriers.seats_on_line()

    def age_in_years(self, on: date) -> int:
        if on < self.commissioned:
            raise PlantError(f"{on.isoformat()} is before {self.code} was built")
        years = on.year - self.commissioned.year
        if (on.month, on.day) < (self.commissioned.month, self.commissioned.day):
            years -= 1
        return years

    def uphill_bearing(self) -> Bearing:
        return self.bearing

    def downhill_bearing(self) -> Bearing:
        return self.bearing.reciprocal()

    def __str__(self) -> str:
        return f"{self.code} {self.name}, {self.lift_class.value}"
