"""Repositories, one a thing the department talks about.

Each one takes a session and hands back domain objects rather than rows, so
nothing above this line ever imports a model. Everything that reads a lift reads
it through here, which is the only way the mapper stays the single place the two
shapes meet.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from sheaveline.board.incident import Incident
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.mappers.plant import (
    lift_from_row,
    lift_to_row,
    rope_from_row,
    rope_to_row,
)
from sheaveline.persistence.mappers.ropework import wear_from_row, wear_to_row
from sheaveline.persistence.models.board import IncidentRow
from sheaveline.persistence.models.plant import HaulRopeRow, LiftRow
from sheaveline.persistence.models.ropework import WearSurveyRow
from sheaveline.persistence.models.upkeep import WorkOrderRow
from sheaveline.plant.identity import LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.wear import WearSurvey
from sheaveline.upkeep.workorder import Priority, WorkOrder


class RopeRepository:
    """Haul ropes, by their mill serial."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, rope: HaulRope, lift_code: LiftCode | None = None) -> None:
        if self.exists(rope.serial):
            raise StorageError(f"rope {rope.serial} is already on file")
        self.session.add(rope_to_row(rope, lift_code))

    def get(self, serial: RopeSerial) -> HaulRope:
        row = self.session.get(HaulRopeRow, serial)
        if row is None:
            raise StorageError(f"there is no rope {serial}")
        return rope_from_row(row)

    def exists(self, serial: RopeSerial) -> bool:
        return self.session.get(HaulRopeRow, serial) is not None

    def all(self) -> list[HaulRope]:
        rows = self.session.scalars(select(HaulRopeRow).order_by(HaulRopeRow.serial))
        return [rope_from_row(row) for row in rows]

    def on_lift(self, lift_code: LiftCode) -> list[HaulRope]:
        rows = self.session.scalars(select(HaulRopeRow).where(HaulRopeRow.lift_code == lift_code))
        return [rope_from_row(row) for row in rows]

    def spares(self) -> list[HaulRope]:
        rows = self.session.scalars(select(HaulRopeRow).where(HaulRopeRow.lift_code.is_(None)))
        return [rope_from_row(row) for row in rows]

    def remove(self, serial: RopeSerial) -> None:
        row = self.session.get(HaulRopeRow, serial)
        if row is None:
            raise StorageError(f"there is no rope {serial}")
        self.session.delete(row)


class LiftRepository:
    """Machines, by the code painted on the terminal."""

    def __init__(self, session: Session) -> None:
        self.session = session
        self.ropes = RopeRepository(session)

    def add(self, lift: Lift) -> None:
        if self.exists(lift.code):
            raise StorageError(f"lift {lift.code} is already on file")
        if not self.ropes.exists(lift.rope.serial):
            self.ropes.add(lift.rope, lift.code)
        self.session.add(lift_to_row(lift))

    def get(self, code: LiftCode) -> Lift:
        row = self.session.get(LiftRow, code)
        if row is None:
            raise StorageError(f"there is no lift {code}")
        if not row.ropes:
            raise StorageError(f"lift {code} has no rope on it")
        return lift_from_row(row, rope_from_row(row.ropes[0]))

    def exists(self, code: LiftCode) -> bool:
        return self.session.get(LiftRow, code) is not None

    def all(self) -> list[Lift]:
        rows = self.session.scalars(select(LiftRow).order_by(LiftRow.code))
        return [self.get(row.code) for row in rows]

    def codes(self) -> list[LiftCode]:
        rows = self.session.scalars(select(LiftRow.code).order_by(LiftRow.code))
        return list(rows)

    def remove(self, code: LiftCode) -> None:
        row = self.session.get(LiftRow, code)
        if row is None:
            raise StorageError(f"there is no lift {code}")
        self.session.delete(row)


class WearSurveyRepository:
    """Rope walks, by the rope they were taken on."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, survey: WearSurvey) -> None:
        self.session.add(wear_to_row(survey))

    def for_rope(self, rope: HaulRope) -> list[WearSurvey]:
        rows = self.session.scalars(
            select(WearSurveyRow)
            .where(WearSurveyRow.rope_serial == rope.serial)
            .order_by(WearSurveyRow.taken)
        )
        return [wear_from_row(row, rope) for row in rows]

    def latest(self, rope: HaulRope) -> WearSurvey:
        found = self.for_rope(rope)
        if not found:
            raise StorageError(f"rope {rope.serial} has never been walked")
        return found[-1]

    def on(self, rope: HaulRope, taken: date) -> WearSurvey:
        row = self.session.scalar(
            select(WearSurveyRow).where(
                WearSurveyRow.rope_serial == rope.serial,
                WearSurveyRow.taken == taken,
            )
        )
        if row is None:
            raise StorageError(f"rope {rope.serial} was not walked on {taken.isoformat()}")
        return wear_from_row(row, rope)

    def count_for(self, rope: HaulRope) -> int:
        return len(self.for_rope(rope))


class IncidentRepository:
    """Incidents, by the machine and the moment."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, incident: Incident) -> None:
        self.session.add(
            IncidentRow(
                lift_code=incident.lift,
                at=incident.at,
                kind=incident.kind,
                injury=incident.injury,
                people=incident.people,
                narrative=incident.narrative,
                reported_by=incident.reported_by,
            )
        )

    def for_lift(self, code: LiftCode) -> list[Incident]:
        rows = self.session.scalars(
            select(IncidentRow).where(IncidentRow.lift_code == code).order_by(IncidentRow.at)
        )
        return [self._to_domain(row) for row in rows]

    def unfiled(self) -> list[Incident]:
        rows = self.session.scalars(select(IncidentRow).where(IncidentRow.filing_number.is_(None)))
        return [self._to_domain(row) for row in rows]

    def between(self, start: date, end: date) -> list[Incident]:
        rows = self.session.scalars(select(IncidentRow).order_by(IncidentRow.at))
        return [self._to_domain(row) for row in rows if start <= row.at.date() <= end]

    def _to_domain(self, row: IncidentRow) -> Incident:
        return Incident(
            lift=row.lift_code,
            at=row.at,
            kind=row.kind,
            injury=row.injury,
            people=row.people,
            narrative=row.narrative,
            reported_by=row.reported_by,
        )


class WorkOrderRepository:
    """Work orders, by their number."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, order: WorkOrder) -> None:
        if self.session.get(WorkOrderRow, order.number) is not None:
            raise StorageError(f"work order {order.number} is already on file")
        self.session.add(
            WorkOrderRow(
                number=order.number,
                lift_code=order.lift,
                raised=order.raised,
                raised_by=order.raised_by,
                detail=order.detail,
                priority=order.priority,
                where=order.where,
                closed=order.closed,
                closed_by=order.closed_by,
            )
        )

    def get(self, number: int) -> WorkOrder:
        row = self.session.get(WorkOrderRow, number)
        if row is None:
            raise StorageError(f"there is no work order {number}")
        return self._to_domain(row)

    def next_number(self) -> int:
        highest = self.session.scalar(
            select(WorkOrderRow.number).order_by(WorkOrderRow.number.desc())
        )
        return 1 if highest is None else highest + 1

    def open_orders(self, code: LiftCode | None = None) -> list[WorkOrder]:
        statement = select(WorkOrderRow).where(WorkOrderRow.closed.is_(None))
        if code is not None:
            statement = statement.where(WorkOrderRow.lift_code == code)
        rows = self.session.scalars(statement.order_by(WorkOrderRow.number))
        return [self._to_domain(row) for row in rows]

    def of_priority(self, priority: Priority) -> list[WorkOrder]:
        rows = self.session.scalars(
            select(WorkOrderRow)
            .where(WorkOrderRow.priority == priority)
            .order_by(WorkOrderRow.number)
        )
        return [self._to_domain(row) for row in rows]

    def close(self, number: int, on: date, by: str) -> WorkOrder:
        row = self.session.get(WorkOrderRow, number)
        if row is None:
            raise StorageError(f"there is no work order {number}")
        closed = self._to_domain(row).close(on, by)
        row.closed = closed.closed
        row.closed_by = closed.closed_by
        return closed

    def _to_domain(self, row: WorkOrderRow) -> WorkOrder:
        return WorkOrder(
            number=row.number,
            lift=row.lift_code,
            raised=row.raised,
            raised_by=row.raised_by,
            detail=row.detail,
            priority=row.priority,
            where=row.where,
            closed=row.closed,
            closed_by=row.closed_by,
        )
