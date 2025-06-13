"""What happens in the terminal, which is where capacity is really won or lost.

On a fixed grip the carrier comes through the load point at line speed and the
crew has the length of the ramp to get everybody sat down. That is why a fixed
grip triple runs at four hundred and fifty feet a minute and a detachable does
not have to.

On a detachable the grip opens, the carrier drops to a walking pace, runs round
the terminal on tyres, and speeds back up on the way out. The loading window is
set by the slow section and has almost nothing to do with the line speed. That
is the whole trick of the machine and it is why the two classes need different
arithmetic rather than one formula with a flag in it.

Two things have to hold at once and they bind independently. The carriers have
to arrive far enough apart for the class, and each one has to sit at the gate
long enough for the crew to fill it.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Final

from sheaveline.capacity.spacing import MINIMUM_INTERVAL, CapacityError, LineSpacing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.classes import LiftClass

SECONDS_PER_PASSENGER: Final = Decimal("1.4")
BOARDING_ALLOWANCE: Final = Duration.from_seconds(2)


@dataclass(frozen=True)
class TerminalTransit:
    """How a carrier gets through one terminal."""

    line_speed: Speed
    terminal_speed: Speed
    deceleration: Length
    slow_section: Length
    acceleration: Length

    def __post_init__(self) -> None:
        if self.line_speed.is_stopped():
            raise CapacityError("a terminal transit needs a line speed")
        if self.terminal_speed.is_stopped():
            raise CapacityError("a carrier does not stop dead in the terminal")
        if self.terminal_speed > self.line_speed:
            raise CapacityError("a terminal does not run faster than the line")
        for piece in (self.deceleration, self.slow_section, self.acceleration):
            if piece.mils < 0:
                raise CapacityError("a terminal section has a length")

    @classmethod
    def fixed_grip(cls, line_speed: Speed, ramp: Length) -> TerminalTransit:
        """A fixed grip has no slow section, so the ramp is the whole of it."""
        return cls(line_speed, line_speed, Length(0), ramp, Length(0))

    def detaches(self) -> bool:
        return self.terminal_speed < self.line_speed

    def average_ramp_speed(self) -> Speed:
        """Mean of the two ends, which is close enough over a ramp this short."""
        return Speed((self.line_speed.mils_per_minute + self.terminal_speed.mils_per_minute) // 2)

    def slow_time(self) -> Duration:
        return self.terminal_speed.time_to_cover(self.slow_section)

    def transit_time(self) -> Duration:
        ramp = self.deceleration + self.acceleration
        on_ramp = self.average_ramp_speed().time_to_cover(ramp)
        return self.slow_time() + on_ramp

    def loading_window(self) -> Duration:
        """How long a carrier is beside the crew, which is the slow section only."""
        return self.slow_time()

    def length(self) -> Length:
        return self.deceleration + self.slow_section + self.acceleration


@dataclass(frozen=True)
class LoadingGate:
    """The load point, and whether the crew can keep up with it."""

    transit: TerminalTransit
    seats: int
    attendants: int = 1

    def __post_init__(self) -> None:
        if self.seats < 1:
            raise CapacityError("a gate loads at least one seat")
        if self.attendants < 1:
            raise CapacityError("a load point is staffed")

    def time_needed(self) -> Duration:
        """Seconds to seat a full carrier, which a second attendant shortens."""
        per_row = Decimal(self.seats) * SECONDS_PER_PASSENGER / self.attendants
        return Duration.from_seconds(int(per_row)) + BOARDING_ALLOWANCE

    def window(self) -> Duration:
        return self.transit.loading_window()

    def keeps_up(self) -> bool:
        return self.window() >= self.time_needed()

    def slack(self) -> Duration:
        window = self.window()
        needed = self.time_needed()
        return window - needed if window >= needed else Duration(0)

    def attendants_needed(self) -> int:
        """The fewest attendants that make the window work."""
        for count in range(1, 5):
            if LoadingGate(self.transit, self.seats, count).keeps_up():
                return count
        raise CapacityError("no reasonable crew can load that carrier in that window")

    def with_attendants(self, attendants: int) -> LoadingGate:
        return LoadingGate(self.transit, self.seats, attendants)


@dataclass(frozen=True)
class TerminalCheck:
    """Both constraints on one machine, read together."""

    spacing: LineSpacing
    gate: LoadingGate
    lift_class: LiftClass

    def interval_holds(self) -> bool:
        return self.spacing.meets(self.lift_class)

    def gate_holds(self) -> bool:
        return self.gate.keeps_up()

    def holds(self) -> bool:
        return self.interval_holds() and self.gate_holds()

    def binding_constraint(self) -> str:
        if not self.interval_holds() and not self.gate_holds():
            return "both"
        if not self.interval_holds():
            return "interval"
        if not self.gate_holds():
            return "gate"
        return "neither"

    def most_carriers(self) -> int:
        """The fullest line that still satisfies whichever constraint binds first."""
        if not self.gate_holds():
            return 0
        return self.spacing.most_carriers_for(self.lift_class)

    def floor(self) -> Duration:
        return MINIMUM_INTERVAL[self.lift_class]

    def __str__(self) -> str:
        return (
            f"interval {self.spacing.interval().format_clock()}, "
            f"window {self.gate.window().format_clock()}, "
            f"{self.binding_constraint()} binding"
        )
