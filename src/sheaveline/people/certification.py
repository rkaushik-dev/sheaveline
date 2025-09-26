"""Who is allowed to stand where, which is a card in a wallet and a date on it.

A certificate is not a qualification, it is a permission with an expiry. The
person who ran the top of the quad for six seasons is not allowed to run it on
the first of December if their card ran out on the thirtieth of November, and
the fact that they obviously still know how is not the point: the board audits
the cards.

Cards are also specific. An endorsement to run a fixed grip triple does not let
somebody run a detachable, because the machines fail differently and the
training is different. So the question is always three questions at once. Is the
card current, does it carry the endorsement the post needs, and does that
endorsement cover this class of machine.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.ops.shift import Post
from sheaveline.plant.classes import LiftClass


class PeopleError(SheavelineError):
    """A personnel record was built or read in a way that cannot hold."""


class Endorsement(Enum):
    """What a card lets somebody do."""

    OPERATOR = "operator"
    LEAD_OPERATOR = "lead operator"
    MECHANIC = "mechanic"
    EVACUATION = "evacuation"

    @property
    def is_operational(self) -> bool:
        return self in {Endorsement.OPERATOR, Endorsement.LEAD_OPERATOR}


POST_NEEDS: Final[dict[Post, Endorsement]] = {
    Post.BOTTOM: Endorsement.OPERATOR,
    Post.MID: Endorsement.OPERATOR,
    Post.TOP: Endorsement.LEAD_OPERATOR,
    Post.MECHANIC: Endorsement.MECHANIC,
}


@dataclass(frozen=True)
class Certificate:
    """One card, for one person, for one endorsement."""

    person: str
    endorsement: Endorsement
    issued: date
    expires: date
    issued_by: str
    classes: frozenset[LiftClass] = frozenset(LiftClass)

    def __post_init__(self) -> None:
        if not self.person.strip():
            raise PeopleError("a certificate names a person")
        if not self.issued_by.strip():
            raise PeopleError(f"{self.person} has a card with nobody's name on it")
        if self.expires <= self.issued:
            raise PeopleError(f"{self.person} has a card that expires before it was issued")
        if not self.classes:
            raise PeopleError(f"{self.person} has a card that covers no machines")

    def is_current(self, on: date) -> bool:
        return self.issued <= on <= self.expires

    def covers_class(self, lift_class: LiftClass) -> bool:
        return lift_class in self.classes

    def covers_post(self, post: Post) -> bool:
        """A lead operator card also covers the posts an ordinary operator holds."""
        needed = POST_NEEDS[post]
        if self.endorsement is needed:
            return True
        return self.endorsement is Endorsement.LEAD_OPERATOR and needed is Endorsement.OPERATOR

    def allows(self, post: Post, lift_class: LiftClass, on: date) -> bool:
        return self.is_current(on) and self.covers_post(post) and self.covers_class(lift_class)

    def days_left(self, on: date) -> int:
        return (self.expires - on).days

    def renewed_to(self, expires: date, issued_by: str) -> Certificate:
        if expires <= self.expires:
            raise PeopleError(f"{self.person} would be renewed to no later than they already are")
        return Certificate(
            person=self.person,
            endorsement=self.endorsement,
            issued=self.expires,
            expires=expires,
            issued_by=issued_by,
            classes=self.classes,
        )

    def __str__(self) -> str:
        return f"{self.person}, {self.endorsement.value}, to {self.expires.isoformat()}"


@dataclass(frozen=True)
class PersonnelFile:
    """Every card the department holds."""

    certificates: tuple[Certificate, ...] = ()

    @classmethod
    def of(cls, certificates: Sequence[Certificate] = ()) -> PersonnelFile:
        ordered = tuple(
            sorted(
                certificates, key=lambda card: (card.person, card.endorsement.value, card.expires)
            )
        )
        return cls(ordered)

    def people(self) -> tuple[str, ...]:
        return tuple(sorted({card.person for card in self.certificates}))

    def for_person(self, person: str) -> tuple[Certificate, ...]:
        return tuple(card for card in self.certificates if card.person == person)

    def current_for(self, person: str, on: date) -> tuple[Certificate, ...]:
        return tuple(card for card in self.for_person(person) if card.is_current(on))

    def may_hold(self, person: str, post: Post, lift_class: LiftClass, on: date) -> bool:
        return any(card.allows(post, lift_class, on) for card in self.for_person(person))

    def qualified_for(self, post: Post, lift_class: LiftClass, on: date) -> tuple[str, ...]:
        return tuple(
            sorted({card.person for card in self.certificates if card.allows(post, lift_class, on)})
        )

    def expiring_within(self, on: date, days: int) -> tuple[Certificate, ...]:
        if days < 0:
            raise PeopleError("a look ahead is a positive number of days")
        cutoff = on + timedelta(days=days)
        return tuple(
            card for card in self.certificates if card.is_current(on) and card.expires <= cutoff
        )

    def lapsed(self, on: date) -> tuple[Certificate, ...]:
        return tuple(card for card in self.certificates if card.expires < on)

    def with_certificate(self, certificate: Certificate) -> PersonnelFile:
        return PersonnelFile.of([*self.certificates, certificate])

    def __len__(self) -> int:
        return len(self.certificates)
