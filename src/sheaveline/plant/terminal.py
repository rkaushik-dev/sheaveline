"""Terminals, the bullwheels in them, and what turns and stops the rope.

A terminal is not just a building. It carries a bullwheel the rope wraps around,
and depending on its role it may also carry the drive train, the tension system,
or both. The rules about which is which are worth enforcing because a lift with
two drive terminals or none is a data entry mistake, not a machine.

Brakes are here too. Every aerial lift carries a service brake on the drive
train and an emergency brake on the bullwheel, and any lift that could run away
downhill under load carries a backstop as well.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.classes import DriveType, LiftClass, PlantError, TerminalRole

MINIMUM_WRAP_RATIO = 60


class BrakeKind(Enum):
    """Where a brake acts, which decides what it can stop."""

    SERVICE = "service"
    EMERGENCY = "emergency"
    BACKSTOP = "backstop"

    @property
    def acts_on_bullwheel(self) -> bool:
        """An emergency brake grips the bullwheel, so it works with the shaft broken."""
        return self is BrakeKind.EMERGENCY

    @property
    def holds_against_rollback(self) -> bool:
        return self is BrakeKind.BACKSTOP


@dataclass(frozen=True)
class Brake:
    """One brake on the drive, with the torque it is set to."""

    kind: BrakeKind
    torque_pound_feet: int
    hydraulic: bool = True

    def __post_init__(self) -> None:
        if self.torque_pound_feet <= 0:
            raise PlantError(f"a {self.kind.value} brake with no torque is not a brake")
        if self.kind is BrakeKind.BACKSTOP and self.hydraulic:
            raise PlantError("a backstop is mechanical, not hydraulic")

    def __str__(self) -> str:
        return f"{self.kind.value} brake at {self.torque_pound_feet} lb ft"


@dataclass(frozen=True)
class Bullwheel:
    """The wheel the rope wraps, measured to the bottom of the groove."""

    diameter: Length
    liner: str

    def __post_init__(self) -> None:
        if self.diameter.mils <= 0:
            raise PlantError("a bullwheel has a diameter")
        if not self.liner.strip():
            raise PlantError("a bullwheel groove has a liner")

    def circumference(self) -> Length:
        return self.diameter.scaled(Decimal(str(math.pi)))

    def rope_speed_at(self, revolutions_per_minute: int | float | str | Decimal) -> Speed:
        """How fast the rope runs when the wheel turns at that rate."""
        travel = self.circumference().scaled(Decimal(str(revolutions_per_minute)))
        return Speed(travel.mils)

    def revolutions_for(self, speed: Speed) -> Decimal:
        return Decimal(speed.mils_per_minute) / self.circumference().mils

    def largest_rope(self) -> Length:
        """The thickest rope this wheel may carry, sixty to one on the diameter."""
        return self.diameter.scaled(Decimal(1) / MINIMUM_WRAP_RATIO)

    def accepts_rope(self, rope_diameter: Length) -> bool:
        return rope_diameter <= self.largest_rope()

    def __str__(self) -> str:
        return f"{self.diameter.format_feet_inches()} bullwheel, {self.liner} liner"


@dataclass(frozen=True)
class DriveTrain:
    """The prime mover, what it drives through, and how it is stopped."""

    drive_type: DriveType
    rated_horsepower: int
    gearbox_ratio: Decimal
    brakes: tuple[Brake, ...]
    auxiliary: DriveType | None = None

    def __post_init__(self) -> None:
        if self.rated_horsepower <= 0:
            raise PlantError("a drive train has a rating")
        if self.gearbox_ratio <= 0:
            raise PlantError("a gearbox ratio is positive")
        kinds = [brake.kind for brake in self.brakes]
        if len(set(kinds)) != len(kinds):
            raise PlantError("a drive train carries one brake of each kind")
        if BrakeKind.SERVICE not in kinds:
            raise PlantError("a drive train carries a service brake")

    def has_brake(self, kind: BrakeKind) -> bool:
        return any(brake.kind is kind for brake in self.brakes)

    def brake(self, kind: BrakeKind) -> Brake:
        for brake in self.brakes:
            if brake.kind is kind:
                return brake
        raise PlantError(f"there is no {kind.value} brake on this drive")

    def has_standby_power(self) -> bool:
        """Either a separate auxiliary drive or a prime mover that is its own."""
        return self.auxiliary is not None or self.drive_type.is_prime_mover

    def motor_speed_for(self, wheel_rpm: Decimal) -> Decimal:
        return wheel_rpm * self.gearbox_ratio

    def suits(self, lift_class: LiftClass) -> bool:
        """An aerial lift needs an emergency brake and a way to run without the grid."""
        if not lift_class.is_aerial:
            return True
        return self.has_brake(BrakeKind.EMERGENCY) and self.has_standby_power()


@dataclass(frozen=True)
class Terminal:
    """One end of the lift, and what it is responsible for."""

    role: TerminalRole
    label: str
    station: Length
    rope_elevation: Length
    bullwheel: Bullwheel
    drive: DriveTrain | None = None
    tension_travel: Length | None = None

    def __post_init__(self) -> None:
        if self.role.drives and self.drive is None:
            raise PlantError(f"{self.label} drives the rope and has no drive train")
        if not self.role.drives and self.drive is not None:
            raise PlantError(f"{self.label} does not drive the rope and carries a drive train")
        if self.role.tensions and self.tension_travel is None:
            raise PlantError(f"{self.label} tensions the rope and has no travel")
        if not self.role.tensions and self.tension_travel is not None:
            raise PlantError(f"{self.label} does not tension the rope and has travel")

    def drive_train(self) -> DriveTrain:
        if self.drive is None:
            raise PlantError(f"{self.label} has no drive train")
        return self.drive

    def travel(self) -> Length:
        if self.tension_travel is None:
            raise PlantError(f"{self.label} does not tension the rope")
        return self.tension_travel

    def __str__(self) -> str:
        return f"{self.label} ({self.role.value})"


def check_pair(lower: Terminal, upper: Terminal) -> None:
    """A lift has one drive terminal and one tension terminal, and no more."""
    drives = [terminal for terminal in (lower, upper) if terminal.role.drives]
    tensions = [terminal for terminal in (lower, upper) if terminal.role.tensions]
    if len(drives) != 1:
        raise PlantError(f"a lift drives from one terminal, found {len(drives)}")
    if len(tensions) != 1:
        raise PlantError(f"a lift tensions at one terminal, found {len(tensions)}")
    if lower.station >= upper.station:
        raise PlantError("the lower terminal stands below the upper one")
