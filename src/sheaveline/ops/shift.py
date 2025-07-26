"""Who is on the machine, where, and for which part of the day.

Minimum staffing is not a headcount. It is a set of posts that have to be
covered for every minute the machine is turning, and the set depends on what the
machine is. A surface tow runs with one person at the bottom. A detachable quad
needs somebody at each end, because the top operator is the only person who can
see a chair that has not unloaded. A tram runs with a mechanic on site, because
a tram that stops does not clear itself.

The gap this module exists to find is the one nobody notices: two operators, one
leaves at noon for lunch, the other arrives at ten past, and for ten minutes the
top terminal is unmanned while the lift keeps turning.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from enum import Enum
from typing import Final

from sheaveline.measures.duration import Duration
from sheaveline.ops.preop import OperationsError
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.schedule.hours import OperatingWindow, TimeOfDay


class Post(Enum):
    """A place somebody has to be standing."""

    BOTTOM = "bottom operator"
    TOP = "top operator"
    MID = "mid station attendant"
    MECHANIC = "mechanic on site"

    @property
    def is_an_operator(self) -> bool:
        return self in {Post.BOTTOM, Post.TOP, Post.MID}


REQUIRED_POSTS: Final[dict[LiftClass, frozenset[Post]]] = {
    LiftClass.FIXED_CHAIR: frozenset({Post.BOTTOM, Post.TOP}),
    LiftClass.DETACHABLE_CHAIR: frozenset({Post.BOTTOM, Post.TOP}),
    LiftClass.PULSE_GONDOLA: frozenset({Post.BOTTOM, Post.TOP}),
    LiftClass.DETACHABLE_GONDOLA: frozenset({Post.BOTTOM, Post.TOP}),
    LiftClass.AERIAL_TRAM: frozenset({Post.BOTTOM, Post.TOP, Post.MECHANIC}),
    LiftClass.SURFACE_TOW: frozenset({Post.BOTTOM}),
}


@dataclass(frozen=True)
class Assignment:
    """One person at one post for one stretch of the day."""

    person: str
    post: Post
    starts: TimeOfDay
    ends: TimeOfDay

    def __post_init__(self) -> None:
        if not self.person.strip():
            raise OperationsError("an assignment names a person")
        if self.ends <= self.starts:
            raise OperationsError(f"{self.person} would finish before starting")

    def covers(self, moment: TimeOfDay) -> bool:
        return self.starts <= moment < self.ends

    def length(self) -> Duration:
        return self.starts.until(self.ends)

    def overlaps(self, other: Assignment) -> bool:
        return self.starts < other.ends and other.starts < self.ends

    def __str__(self) -> str:
        return f"{self.person} on {self.post.value}, {self.starts} to {self.ends}"


@dataclass(frozen=True)
class Roster:
    """Everybody assigned to one machine on one day."""

    lift: LiftCode
    lift_class: LiftClass
    day: date
    window: OperatingWindow
    assignments: tuple[Assignment, ...] = ()

    def __post_init__(self) -> None:
        for one in self.assignments:
            for other in self.assignments:
                if one is other:
                    continue
                if one.person == other.person and one.overlaps(other):
                    raise OperationsError(f"{one.person} is in two places at once")

    @classmethod
    def of(
        cls,
        lift: LiftCode,
        lift_class: LiftClass,
        day: date,
        window: OperatingWindow,
        assignments: Sequence[Assignment] = (),
    ) -> Roster:
        ordered = tuple(sorted(assignments, key=lambda one: (one.post.value, one.starts.minutes)))
        return cls(lift, lift_class, day, window, ordered)

    def required(self) -> frozenset[Post]:
        return REQUIRED_POSTS[self.lift_class]

    def at_post(self, post: Post) -> tuple[Assignment, ...]:
        return tuple(one for one in self.assignments if one.post is post)

    def staffing_at(self, moment: TimeOfDay) -> frozenset[Post]:
        return frozenset(one.post for one in self.assignments if one.covers(moment))

    def is_staffed_at(self, moment: TimeOfDay) -> bool:
        return self.required() <= self.staffing_at(moment)

    def people_at(self, moment: TimeOfDay) -> tuple[str, ...]:
        return tuple(sorted(one.person for one in self.assignments if one.covers(moment)))

    def gaps(self) -> tuple[tuple[Post, TimeOfDay], ...]:
        """Every minute of the running day where a required post is empty."""
        found: list[tuple[Post, TimeOfDay]] = []
        for post in sorted(self.required(), key=lambda entry: entry.value):
            minute = self.window.first_load
            open_gap: TimeOfDay | None = None
            while minute <= self.window.sweep_by:
                covered = any(one.covers(minute) for one in self.at_post(post))
                if not covered and open_gap is None:
                    open_gap = minute
                    found.append((post, minute))
                elif covered:
                    open_gap = None
                minute = TimeOfDay(minute.minutes + 1)
        return tuple(found)

    def is_covered(self) -> bool:
        return not self.gaps()

    def hours_worked(self, person: str) -> Duration:
        total = Duration(0)
        for one in self.assignments:
            if one.person == person:
                total = total + one.length()
        return total

    def people(self) -> tuple[str, ...]:
        return tuple(sorted({one.person for one in self.assignments}))

    def with_assignment(self, assignment: Assignment) -> Roster:
        return Roster.of(
            self.lift,
            self.lift_class,
            self.day,
            self.window,
            [*self.assignments, assignment],
        )

    def __str__(self) -> str:
        state = "covered" if self.is_covered() else f"{len(self.gaps())} gaps"
        return f"{self.lift} {self.day.isoformat()}, {len(self.people())} on, {state}"
