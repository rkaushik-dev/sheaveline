"""Rescue teams, what they carry, and whether they are allowed to use it.

A team is three things at once and all three have to be true on the day. It is
people, and the people have to be current on their training or they are
spectators. It is kit, and the kit reaches a certain height and no further. And
it is a place on the mountain, because a team staged at the base is forty
minutes from the top of the line whatever else is true about it.

The kit question is the one that catches people out. A twenty foot ladder is not
a twenty foot reach, because somebody has to stand on it and work, and a rope
system that reaches a hundred feet down needs an anchor and a tower to hang off,
which the middle of a span does not have.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from enum import Enum
from typing import Final

from sheaveline.evac.profile import CarrierStop, EvacuationError
from sheaveline.measures.length import Length

LADDER_WORKING_ALLOWANCE: Final = Length.from_feet(4)


class KitItem(Enum):
    """One piece of kit, and what it does."""

    LADDER = "ladder"
    ROPE_SYSTEM = "rope system"
    SEAT = "rescue seat"
    HARNESS = "harness"
    RADIO = "radio"

    @property
    def reaches(self) -> bool:
        """Kit that decides how high the team can work."""
        return self in {KitItem.LADDER, KitItem.ROPE_SYSTEM}


@dataclass(frozen=True)
class RescueKit:
    """What a team is carrying, and how far up it gets them."""

    ladder: Length | None = None
    rope: Length | None = None
    seats: int = 0
    harnesses: int = 0
    radios: int = 0

    def __post_init__(self) -> None:
        for name, value in (("ladder", self.ladder), ("rope", self.rope)):
            if value is not None and value.mils <= 0:
                raise EvacuationError(f"a {name} with no length is not kit")
        for name, count in (
            ("seats", self.seats),
            ("harnesses", self.harnesses),
            ("radios", self.radios),
        ):
            if count < 0:
                raise EvacuationError(f"a kit cannot hold negative {name}")

    def ladder_reach(self) -> Length:
        """A ladder reaches its length less the room to stand and work."""
        if self.ladder is None:
            return Length(0)
        reach = self.ladder - LADDER_WORKING_ALLOWANCE
        return reach if reach.mils > 0 else Length(0)

    def rope_reach(self) -> Length:
        return self.rope or Length(0)

    def reach(self) -> Length:
        return max(self.ladder_reach(), self.rope_reach())

    def reaches(self, height: Length) -> bool:
        return height <= self.reach()

    def needs_a_rope_for(self, height: Length) -> bool:
        """True when the ladder alone will not do it."""
        return height > self.ladder_reach()

    def is_complete(self, people: int) -> bool:
        """Everybody working aloft needs a harness and the team needs one radio."""
        return self.harnesses >= people and self.radios >= 1 and self.seats >= 1

    def carries(self, item: KitItem) -> bool:
        if item is KitItem.LADDER:
            return self.ladder is not None
        if item is KitItem.ROPE_SYSTEM:
            return self.rope is not None
        if item is KitItem.SEAT:
            return self.seats > 0
        if item is KitItem.HARNESS:
            return self.harnesses > 0
        return self.radios > 0

    def __str__(self) -> str:
        return f"reach {self.reach().feet:.0f} ft, {self.seats} seats"


@dataclass(frozen=True)
class RescueTeam:
    """One team, its people, its kit and where it is staged."""

    name: str
    members: tuple[str, ...]
    kit: RescueKit
    staged_at: Length
    trained_until: date

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise EvacuationError("a team has a name")
        if len(self.members) < 2:
            raise EvacuationError(f"{self.name} works aloft and needs at least two people")
        if len(set(self.members)) != len(self.members):
            raise EvacuationError(f"{self.name} has somebody counted twice")

    @classmethod
    def of(
        cls,
        name: str,
        members: Sequence[str],
        kit: RescueKit,
        staged_at: Length,
        trained_until: date,
    ) -> RescueTeam:
        return cls(name, tuple(members), kit, staged_at, trained_until)

    def size(self) -> int:
        return len(self.members)

    def is_current(self, on: date) -> bool:
        return on <= self.trained_until

    def is_equipped(self) -> bool:
        return self.kit.is_complete(self.size())

    def can_reach(self, stop: CarrierStop) -> bool:
        return self.kit.reaches(stop.height)

    def may_work(self, stop: CarrierStop, on: date) -> bool:
        return self.is_current(on) and self.is_equipped() and self.can_reach(stop)

    def refusals(self, stop: CarrierStop, on: date) -> tuple[str, ...]:
        """Why a team cannot take a carrier, which is what the plan has to say."""
        reasons = []
        if not self.is_current(on):
            reasons.append(f"{self.name} training lapsed {self.trained_until.isoformat()}")
        if not self.is_equipped():
            reasons.append(f"{self.name} kit is short")
        if not self.can_reach(stop):
            reasons.append(
                f"{self.name} reaches {self.kit.reach().feet:.0f} ft, "
                f"carrier {stop.number} is at {stop.height.feet:.0f} ft"
            )
        return tuple(reasons)

    def distance_to(self, station: Length) -> Length:
        return abs(station - self.staged_at)

    def __str__(self) -> str:
        return f"{self.name}, {self.size()} up, {self.kit}"


@dataclass(frozen=True)
class TeamPool:
    """Every team the department can put on the hill."""

    teams: tuple[RescueTeam, ...]

    def __post_init__(self) -> None:
        if not self.teams:
            raise EvacuationError("a pool has teams in it")
        names = [team.name for team in self.teams]
        if len(set(names)) != len(names):
            raise EvacuationError("two teams share a name")

    @classmethod
    def of(cls, teams: Sequence[RescueTeam]) -> TeamPool:
        return cls(tuple(teams))

    def current(self, on: date) -> tuple[RescueTeam, ...]:
        return tuple(team for team in self.teams if team.is_current(on))

    def lapsed(self, on: date) -> tuple[RescueTeam, ...]:
        return tuple(team for team in self.teams if not team.is_current(on))

    def people(self, on: date) -> int:
        return sum(team.size() for team in self.current(on))

    def able(self, stop: CarrierStop, on: date) -> tuple[RescueTeam, ...]:
        return tuple(team for team in self.teams if team.may_work(stop, on))

    def nearest_able(self, stop: CarrierStop, on: date) -> RescueTeam:
        able = self.able(stop, on)
        if not able:
            raise EvacuationError(f"no team can work carrier {stop.number}")
        return min(able, key=lambda team: team.distance_to(stop.station).mils)

    def deepest_reach(self) -> Length:
        return max(team.kit.reach() for team in self.teams)

    def __len__(self) -> int:
        return len(self.teams)
