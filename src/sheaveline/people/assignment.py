"""Reading a day's roster against the card file, which nobody does by eye.

The roster knows there is somebody at the top of the quad from noon. The card
file knows whether that somebody is allowed to be. Neither of them knows the
other, and the mistake this module exists to catch is the ordinary one: a lead
operator goes home sick, the bottom operator moves up to cover, and nobody
notices that their card is an operator card rather than a lead one until the
board audits the season in April.

The answer is a list of problems rather than a yes or a no, because the person
reading it has to fix them one at a time and wants to know who is free.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from sheaveline.ops.shift import Assignment, Post, Roster
from sheaveline.people.certification import PeopleError, PersonnelFile
from sheaveline.plant.classes import LiftClass
from sheaveline.schedule.hours import TimeOfDay


class ProblemKind(Enum):
    """What is wrong with a roster."""

    UNCOVERED = "post uncovered"
    UNCARDED = "no card for the post"
    WRONG_CLASS = "card does not cover the machine"
    LAPSED = "card has run out"

    @property
    def is_about_a_person(self) -> bool:
        return self is not ProblemKind.UNCOVERED


@dataclass(frozen=True)
class RosterProblem:
    """One thing wrong, said in a way somebody can act on."""

    kind: ProblemKind
    post: Post
    at: TimeOfDay
    person: str | None = None

    def __str__(self) -> str:
        who = self.person or "nobody"
        return f"{self.at} {self.post.value}: {who}, {self.kind.value}"


@dataclass(frozen=True)
class RosterCheck:
    """One day's roster, read against the cards, on one machine."""

    roster: Roster
    file: PersonnelFile
    lift_class: LiftClass
    on: date

    def __post_init__(self) -> None:
        if self.roster.lift_class is not self.lift_class:
            raise PeopleError("that roster is for another class of machine")

    def uncarded(self) -> tuple[RosterProblem, ...]:
        found = []
        for one in self.roster.assignments:
            if self.file.may_hold(one.person, one.post, self.lift_class, self.on):
                continue
            found.append(RosterProblem(self._why(one), one.post, one.starts, one.person))
        return tuple(found)

    def _why(self, one: Assignment) -> ProblemKind:
        """Say which of the three card questions failed, because the fix differs."""
        cards = self.file.for_person(one.person)
        if not cards:
            return ProblemKind.UNCARDED
        for_post = [card for card in cards if card.covers_post(one.post)]
        if not for_post:
            return ProblemKind.UNCARDED
        for_class = [card for card in for_post if card.covers_class(self.lift_class)]
        if not for_class:
            return ProblemKind.WRONG_CLASS
        return ProblemKind.LAPSED

    def uncovered(self) -> tuple[RosterProblem, ...]:
        return tuple(
            RosterProblem(ProblemKind.UNCOVERED, post, at) for post, at in self.roster.gaps()
        )

    def problems(self) -> tuple[RosterProblem, ...]:
        return (*self.uncovered(), *self.uncarded())

    def is_legal(self) -> bool:
        return not self.problems()

    def substitutes(self, one: Assignment) -> tuple[str, ...]:
        """Who else holds the card and is not already working at that minute."""
        qualified = self.file.qualified_for(one.post, self.lift_class, self.on)
        busy = set(self.roster.people_at(one.starts))
        return tuple(name for name in qualified if name not in busy)

    def cover_for(self, post: Post, at: TimeOfDay) -> tuple[str, ...]:
        qualified = self.file.qualified_for(post, self.lift_class, self.on)
        busy = set(self.roster.people_at(at))
        return tuple(name for name in qualified if name not in busy)

    def problems_of(self, kind: ProblemKind) -> tuple[RosterProblem, ...]:
        return tuple(problem for problem in self.problems() if problem.kind is kind)

    def people_to_fix(self) -> tuple[str, ...]:
        return tuple(
            sorted({problem.person for problem in self.problems() if problem.person is not None})
        )

    def __str__(self) -> str:
        if self.is_legal():
            return f"{self.roster.lift} {self.on.isoformat()}, roster clear"
        return f"{self.roster.lift} {self.on.isoformat()}, {len(self.problems())} problems"
