"""The maze, the queue in it, and how long somebody stands there.

Capacity is what the machine can do. The queue is what the public experiences,
and the two come apart the moment arrivals beat capacity, because from then on
the wait grows by the difference every hour and nothing about the machine
changes at all.

The singles lane is in here because it is the cheapest capacity on the mountain.
It does not make the lift faster; it fills the empty seat beside the pair who
came together, and on a quad that is worth more than a hundred feet a minute.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Final

from sheaveline.capacity.spacing import CapacityError
from sheaveline.capacity.uphill import UphillCapacity
from sheaveline.measures.duration import Duration

SINGLES_LIFT: Final = Decimal("0.08")
MAXIMUM_OCCUPANCY: Final = Decimal("0.98")


@dataclass(frozen=True)
class Maze:
    """The corral at the bottom, and how many people it holds."""

    lanes: int
    people_a_lane: int
    singles_lane: bool = False

    def __post_init__(self) -> None:
        if self.lanes < 1:
            raise CapacityError("a maze has at least one lane")
        if self.people_a_lane < 1:
            raise CapacityError("a lane holds people")

    def holds(self) -> int:
        return self.lanes * self.people_a_lane

    def with_singles(self) -> Maze:
        return Maze(self.lanes, self.people_a_lane, singles_lane=True)


@dataclass(frozen=True)
class Queue:
    """One machine, one maze and the rate people are turning up at."""

    capacity: UphillCapacity
    maze: Maze
    arrivals_an_hour: int

    def __post_init__(self) -> None:
        if self.arrivals_an_hour < 0:
            raise CapacityError("people cannot arrive at a negative rate")

    def occupancy(self) -> Decimal:
        """A singles lane lifts the loading efficiency and nothing else."""
        if not self.maze.singles_lane:
            return self.capacity.occupancy
        return min(self.capacity.occupancy + SINGLES_LIFT, MAXIMUM_OCCUPANCY)

    def throughput(self) -> int:
        """People an hour the machine actually clears from this maze."""
        return self.capacity.at_occupancy(self.occupancy()).persons_per_hour()

    def keeps_up(self) -> bool:
        return self.arrivals_an_hour <= self.throughput()

    def surplus_an_hour(self) -> int:
        """People an hour arriving that the machine cannot take."""
        return max(0, self.arrivals_an_hour - self.throughput())

    def spare_an_hour(self) -> int:
        return max(0, self.throughput() - self.arrivals_an_hour)

    def length_after(self, elapsed: Duration) -> int:
        """How many people are standing there after a stretch at this rate."""
        hours = elapsed.hours
        return int(Decimal(self.surplus_an_hour()) * hours)

    def wait_after(self, elapsed: Duration) -> Duration:
        """How long the last person to join waits, which is the number on the sign."""
        standing = self.length_after(elapsed)
        if standing == 0:
            return Duration(0)
        throughput = self.throughput()
        if throughput == 0:
            raise CapacityError("a machine that clears nobody has no wait to report")
        return Duration.from_seconds(int(Decimal(standing) * 3600 / throughput))

    def overflows_after(self, elapsed: Duration) -> bool:
        """True when the queue is out of the maze and into the car park."""
        return self.length_after(elapsed) > self.maze.holds()

    def fills_in(self) -> Duration | None:
        """How long until the maze is full, or nothing if it never is."""
        surplus = self.surplus_an_hour()
        if surplus == 0:
            return None
        hours = Decimal(self.maze.holds()) / surplus
        return Duration.from_seconds(int(hours * 3600))

    def clears_in(self, standing: int) -> Duration:
        """How long to clear a queue of that size once the arrivals stop."""
        if standing < 0:
            raise CapacityError("a queue cannot be shorter than nobody")
        throughput = self.throughput()
        if throughput == 0:
            raise CapacityError("a machine that clears nobody never clears a queue")
        return Duration.from_seconds(int(Decimal(standing) * 3600 / throughput))

    def with_arrivals(self, arrivals_an_hour: int) -> Queue:
        return Queue(self.capacity, self.maze, arrivals_an_hour)

    def with_maze(self, maze: Maze) -> Queue:
        return Queue(self.capacity, maze, self.arrivals_an_hour)

    def __str__(self) -> str:
        state = "keeping up" if self.keeps_up() else f"{self.surplus_an_hour()} an hour behind"
        return f"{self.throughput():,} an hour, {state}"
