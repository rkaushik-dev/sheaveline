"""Cards and rosters, both ways.

The classes a card covers are their own table rather than a packed string,
because the question the office asks is which people can run a detachable and a
LIKE against a comma separated column is how you end up with SURFACE matching
SURFACE_TOW.

A roster is not stored as one row with people hanging off it. The assignments
stand on their own against the machine and the day, so a person's shift can be
moved without rewriting everybody else's, and the roster is assembled on the way
out. Which means the loader has to be told the machine's class, because a shift
row does not know whether it belongs on a quad or a tow.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sheaveline.ops.shift import Assignment, Roster
from sheaveline.people.certification import Certificate, Endorsement, PersonnelFile
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.models.board import CertificateClassRow, CertificateRow
from sheaveline.persistence.models.ops import AssignmentRow
from sheaveline.plant.classes import LiftClass
from sheaveline.plant.identity import LiftCode
from sheaveline.schedule.hours import OperatingWindow

EVERY_CLASS = frozenset(LiftClass)


def certificate_to_row(card: Certificate) -> CertificateRow:
    row = CertificateRow(
        person=card.person,
        endorsement=card.endorsement,
        issued=card.issued,
        expires=card.expires,
        issued_by=card.issued_by,
    )
    for one in sorted(card.classes, key=lambda kind: kind.value):
        row.classes.append(CertificateClassRow(lift_class=one.value))
    return row


def certificate_from_row(row: CertificateRow) -> Certificate:
    covered = set()
    for one in row.classes:
        try:
            covered.add(LiftClass(one.lift_class))
        except ValueError as wrong:
            raise StorageError(f"there is no lift class called {one.lift_class}") from wrong
    return Certificate(
        person=row.person,
        endorsement=Endorsement(row.endorsement),
        issued=row.issued,
        expires=row.expires,
        issued_by=row.issued_by,
        classes=frozenset(covered) if covered else EVERY_CLASS,
    )


def personnel_from_rows(rows: Sequence[CertificateRow]) -> PersonnelFile:
    return PersonnelFile.of([certificate_from_row(one) for one in rows])


def assignment_to_row(lift: LiftCode, day: date, shift: Assignment) -> AssignmentRow:
    return AssignmentRow(
        lift_code=lift,
        day=day,
        person=shift.person,
        post=shift.post,
        starts=shift.starts,
        ends=shift.ends,
    )


def assignment_from_row(row: AssignmentRow) -> Assignment:
    return Assignment(person=row.person, post=row.post, starts=row.starts, ends=row.ends)


def roster_from_rows(
    lift: LiftCode,
    lift_class: LiftClass,
    day: date,
    window: OperatingWindow,
    rows: Sequence[AssignmentRow],
) -> Roster:
    """The shifts assembled into a roster, which needs to be told what it is on."""
    for row in rows:
        if row.lift_code != lift:
            raise StorageError(f"that shift is on {row.lift_code} and not {lift}")
        if row.day != day:
            raise StorageError(f"that shift is on {row.day.isoformat()} and not {day.isoformat()}")
    return Roster.of(lift, lift_class, day, window, [assignment_from_row(one) for one in rows])
