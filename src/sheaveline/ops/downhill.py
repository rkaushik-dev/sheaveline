"""Carrying people down, which is a different machine from the same machine.

Loading at the top is not the reverse of loading at the bottom. The attendant is
standing where the carriers are moving away rather than toward them, the
unloading happens at the terminal that was built for loading, and the rope has
to be slowed because a rider stepping off downhill has the carrier catching up
with them rather than leaving.

So it is authorised rather than assumed. A machine may do it, at a stated speed,
with somebody at the top, and only for a reason somebody wrote down. A tow does
not do it at all, and the reason that has to be said out loud is that people ask.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from enum import Enum
from typing import Final

from sheaveline.measures.speed import Speed
from sheaveline.ops.preop import OperationsError
from sheaveline.ops.shift import Post
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.lift import Lift


class DownhillReason(Enum):
    """Why somebody is riding down."""

    DISABILITY = "disability"
    BEGINNER = "beginner returning"
    EQUIPMENT = "equipment failure"
    SIGHTSEEING = "sightseeing"
    STAFF = "staff movement"

    @property
    def is_public(self) -> bool:
        return self is not DownhillReason.STAFF

    @property
    def needs_an_escort(self) -> bool:
        """Somebody who cannot get themselves off does not ride alone."""
        return self in {DownhillReason.DISABILITY, DownhillReason.EQUIPMENT}


@dataclass(frozen=True)
class DownhillPolicy:
    """What one class of machine may do when the traffic runs the other way."""

    allowed: bool
    speed: Speed | None
    carriers_at_once: int
    attendant_at_top: bool

    @classmethod
    def for_class(cls, lift_class: LiftClass) -> DownhillPolicy:
        return _POLICY[lift_class]


_POLICY: Final[dict[LiftClass, DownhillPolicy]] = {
    LiftClass.FIXED_CHAIR: DownhillPolicy(
        allowed=True,
        speed=Speed.from_feet_per_minute(300),
        carriers_at_once=2,
        attendant_at_top=True,
    ),
    LiftClass.DETACHABLE_CHAIR: DownhillPolicy(
        allowed=True,
        speed=Speed.from_feet_per_minute(500),
        carriers_at_once=4,
        attendant_at_top=True,
    ),
    LiftClass.PULSE_GONDOLA: DownhillPolicy(
        allowed=True,
        speed=Speed.from_feet_per_minute(400),
        carriers_at_once=1,
        attendant_at_top=True,
    ),
    LiftClass.DETACHABLE_GONDOLA: DownhillPolicy(
        allowed=True,
        speed=Speed.from_feet_per_minute(500),
        carriers_at_once=6,
        attendant_at_top=True,
    ),
    LiftClass.AERIAL_TRAM: DownhillPolicy(
        allowed=True,
        speed=None,
        carriers_at_once=1,
        attendant_at_top=True,
    ),
    LiftClass.SURFACE_TOW: DownhillPolicy(
        allowed=False,
        speed=None,
        carriers_at_once=0,
        attendant_at_top=False,
    ),
}


@dataclass(frozen=True)
class DownhillAuthorisation:
    """One machine cleared to carry down, for a stated stretch of the season."""

    lift: LiftCode
    reason: DownhillReason
    granted: date
    granted_by: str
    expires: date
    escort: str | None = None

    def __post_init__(self) -> None:
        if not self.granted_by.strip():
            raise OperationsError("an authorisation is signed by whoever gave it")
        if self.expires < self.granted:
            raise OperationsError("an authorisation cannot expire before it is given")
        if self.reason.needs_an_escort and not (self.escort or "").strip():
            raise OperationsError(f"a {self.reason.value} ride is escorted")
        if not self.reason.needs_an_escort and self.escort:
            raise OperationsError(f"a {self.reason.value} ride does not carry an escort")

    def is_current(self, on: date) -> bool:
        return self.granted <= on <= self.expires

    def days_left(self, on: date) -> int:
        return (self.expires - on).days

    def __str__(self) -> str:
        return f"{self.lift} downhill for {self.reason.value}, to {self.expires.isoformat()}"


@dataclass(frozen=True)
class DownhillCheck:
    """Whether a machine may run downhill right now, and why not."""

    lift: Lift
    on: date
    authorisations: tuple[DownhillAuthorisation, ...] = ()
    posts_manned: frozenset[Post] = frozenset()
    rope_speed: Speed | None = None

    @classmethod
    def of(
        cls,
        lift: Lift,
        on: date,
        authorisations: Sequence[DownhillAuthorisation] = (),
        posts_manned: Sequence[Post] = (),
        rope_speed: Speed | None = None,
    ) -> DownhillCheck:
        return cls(lift, on, tuple(authorisations), frozenset(posts_manned), rope_speed)

    def policy(self) -> DownhillPolicy:
        return DownhillPolicy.for_class(self.lift.lift_class)

    def current(self) -> tuple[DownhillAuthorisation, ...]:
        return tuple(
            one
            for one in self.authorisations
            if one.lift == self.lift.code and one.is_current(self.on)
        )

    def refusals(self) -> tuple[str, ...]:
        policy = self.policy()
        found: list[str] = []
        if not policy.allowed:
            found.append(f"a {self.lift.lift_class.value} does not carry downhill")
            return tuple(found)
        if not self.current():
            found.append("nothing authorised for today")
        if policy.attendant_at_top and Post.TOP not in self.posts_manned:
            found.append("nobody at the top terminal")
        if policy.speed is not None:
            if self.rope_speed is None:
                found.append("no rope speed set")
            elif self.rope_speed > policy.speed:
                found.append(
                    f"{self.rope_speed.format_fpm()} is above the "
                    f"{policy.speed.format_fpm()} downhill limit"
                )
        return tuple(found)

    def may_run(self) -> bool:
        return not self.refusals()

    def carriers_at_once(self) -> int:
        return self.policy().carriers_at_once if self.may_run() else 0

    def __str__(self) -> str:
        if self.may_run():
            return f"{self.lift.code} may carry down, {self.carriers_at_once()} at once"
        return f"{self.lift.code} may not carry down: {self.refusals()[0]}"
