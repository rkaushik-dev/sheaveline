"""What kind of machine a lift is, and what that decides about it.

The board files a lift under a class and half the rules downstream key off it.
A detachable runs the line fast because the carriers slow down in the terminal,
so its line speed limit is not the loading speed limit. A surface lift has no
carriers to evacuate, so it has no evacuation plan and never will. A tram runs
two cars on a fixed schedule and does not have an interval in the sense the
capacity arithmetic means.

The pairings between class, grip and carrier are written here as a table rather
than as a chain of tests, because a new machine on the hill should be one row.
"""

from __future__ import annotations

from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.measures.speed import Speed


class PlantError(SheavelineError):
    """A machine was described in a way that does not exist."""


class GripType(Enum):
    """How a carrier is attached to the haul rope."""

    FIXED = "fixed"
    DETACHABLE = "detachable"
    TOW = "tow"

    @property
    def detaches(self) -> bool:
        return self is GripType.DETACHABLE

    @property
    def is_tested_for_slip(self) -> bool:
        """A tow hanger is not a grip and is never slip tested."""
        return self is not GripType.TOW


class CarrierType(Enum):
    """What the public rides in or hangs from."""

    CHAIR_2 = "double chair"
    CHAIR_3 = "triple chair"
    CHAIR_4 = "quad chair"
    CHAIR_6 = "six place chair"
    CABIN_8 = "eight place cabin"
    TRAM_CAR = "tram car"
    TBAR = "t bar"
    PLATTER = "platter"

    @property
    def seats(self) -> int:
        return _SEATS[self]

    @property
    def is_enclosed(self) -> bool:
        return self in {CarrierType.CABIN_8, CarrierType.TRAM_CAR}

    @property
    def is_surface(self) -> bool:
        return self in {CarrierType.TBAR, CarrierType.PLATTER}


_SEATS: Final[dict[CarrierType, int]] = {
    CarrierType.CHAIR_2: 2,
    CarrierType.CHAIR_3: 3,
    CarrierType.CHAIR_4: 4,
    CarrierType.CHAIR_6: 6,
    CarrierType.CABIN_8: 8,
    CarrierType.TRAM_CAR: 60,
    CarrierType.TBAR: 2,
    CarrierType.PLATTER: 1,
}


class LiftClass(Enum):
    """The class the board files the machine under."""

    FIXED_CHAIR = "fixed grip chairlift"
    DETACHABLE_CHAIR = "detachable chairlift"
    PULSE_GONDOLA = "pulse gondola"
    DETACHABLE_GONDOLA = "detachable gondola"
    AERIAL_TRAM = "aerial tramway"
    SURFACE_TOW = "surface lift"

    @property
    def is_aerial(self) -> bool:
        """Carriers hang off the rope, so a stalled line strands people in the air."""
        return self is not LiftClass.SURFACE_TOW

    @property
    def is_detachable(self) -> bool:
        return self in {LiftClass.DETACHABLE_CHAIR, LiftClass.DETACHABLE_GONDOLA}

    @property
    def is_reversible(self) -> bool:
        """A tram runs its cars back and forth rather than round a loop."""
        return self is LiftClass.AERIAL_TRAM

    @property
    def needs_evacuation_plan(self) -> bool:
        return self.is_aerial

    @property
    def grip(self) -> GripType:
        return _GRIP_FOR_CLASS[self]

    @property
    def line_speed_limit(self) -> Speed:
        """The fastest the rope may run for this class of machine."""
        return _SPEED_LIMIT[self]

    def carriers(self) -> frozenset[CarrierType]:
        return _CARRIERS_FOR_CLASS[self]

    def accepts(self, carrier: CarrierType) -> bool:
        return carrier in self.carriers()

    def require(self, carrier: CarrierType) -> None:
        if not self.accepts(carrier):
            raise PlantError(f"a {self.value} does not carry a {carrier.value}")


_GRIP_FOR_CLASS: Final[dict[LiftClass, GripType]] = {
    LiftClass.FIXED_CHAIR: GripType.FIXED,
    LiftClass.DETACHABLE_CHAIR: GripType.DETACHABLE,
    LiftClass.PULSE_GONDOLA: GripType.FIXED,
    LiftClass.DETACHABLE_GONDOLA: GripType.DETACHABLE,
    LiftClass.AERIAL_TRAM: GripType.FIXED,
    LiftClass.SURFACE_TOW: GripType.TOW,
}

_CARRIERS_FOR_CLASS: Final[dict[LiftClass, frozenset[CarrierType]]] = {
    LiftClass.FIXED_CHAIR: frozenset(
        {CarrierType.CHAIR_2, CarrierType.CHAIR_3, CarrierType.CHAIR_4}
    ),
    LiftClass.DETACHABLE_CHAIR: frozenset(
        {CarrierType.CHAIR_4, CarrierType.CHAIR_6, CarrierType.CABIN_8}
    ),
    LiftClass.PULSE_GONDOLA: frozenset({CarrierType.CABIN_8}),
    LiftClass.DETACHABLE_GONDOLA: frozenset({CarrierType.CABIN_8}),
    LiftClass.AERIAL_TRAM: frozenset({CarrierType.TRAM_CAR}),
    LiftClass.SURFACE_TOW: frozenset({CarrierType.TBAR, CarrierType.PLATTER}),
}

_SPEED_LIMIT: Final[dict[LiftClass, Speed]] = {
    LiftClass.FIXED_CHAIR: Speed.from_feet_per_minute(500),
    LiftClass.DETACHABLE_CHAIR: Speed.from_feet_per_minute(1200),
    LiftClass.PULSE_GONDOLA: Speed.from_feet_per_minute(500),
    LiftClass.DETACHABLE_GONDOLA: Speed.from_feet_per_minute(1200),
    LiftClass.AERIAL_TRAM: Speed.from_feet_per_minute(2400),
    LiftClass.SURFACE_TOW: Speed.from_feet_per_minute(750),
}


class DriveType(Enum):
    """What turns the bullwheel."""

    DC_MOTOR = "direct current motor"
    AC_DRIVE = "alternating current drive"
    DIESEL_HYDRAULIC = "diesel hydraulic"

    @property
    def has_variable_speed(self) -> bool:
        return self is not DriveType.DIESEL_HYDRAULIC

    @property
    def is_prime_mover(self) -> bool:
        """A diesel is its own power, so it also serves as the standby."""
        return self is DriveType.DIESEL_HYDRAULIC


class TerminalRole(Enum):
    """What a terminal does, which is not the same as where it is."""

    DRIVE = "drive"
    RETURN = "return"
    DRIVE_TENSION = "drive tension"
    RETURN_TENSION = "return tension"

    @property
    def drives(self) -> bool:
        return self in {TerminalRole.DRIVE, TerminalRole.DRIVE_TENSION}

    @property
    def tensions(self) -> bool:
        return self in {TerminalRole.DRIVE_TENSION, TerminalRole.RETURN_TENSION}
