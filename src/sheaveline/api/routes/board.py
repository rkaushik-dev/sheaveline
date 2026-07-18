"""Incidents and what has been sent about them.

The route worth reading is the outstanding list. It is the one the mountain
manager opens on a Monday, and it answers in the order the deadlines fall rather
than the order things happened, because the deropement at four on Saturday is
more urgent than the twisted ankle from Thursday whatever the timestamps say.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, Query, status

from sheaveline.api.deps import Manager, Operator, Reader, Today, Work
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.paging import Page, Paged, Paging
from sheaveline.api.schemas.board import (
    FilingIn,
    FilingOut,
    IncidentIn,
    IncidentOut,
    OutstandingOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.board.filing import FilingKind
from sheaveline.measures.duration import Duration
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.uow import UnitOfWork

router = APIRouter(tags=["board"])


@router.post(
    "/lifts/{code}/incidents",
    response_model=IncidentOut,
    status_code=status.HTTP_201_CREATED,
)
def record_incident(
    code: str,
    body: IncidentIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Operator,
) -> IncidentOut:
    lift = look.lift_code(code)
    incident = body.to_domain(lift)
    row_id = work.incidents.record(incident)
    work.commit()
    return IncidentOut.of(row_id, incident)


@router.get("/lifts/{code}/incidents", response_model=Paged[IncidentOut])
def list_for_lift(
    code: str,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    paging: Paging = Page,
    key: ApiKey = Reader,
) -> Paged[IncidentOut]:
    """Paged, because the register is never cleared down."""
    lift = look.lift_code(code)
    return Paged.of(
        [
            IncidentOut.of(row_id, incident, filing)
            for row_id, incident, filing in work.incidents.numbered_for_lift(lift)
        ],
        paging,
    )


@router.get("/incidents/outstanding", response_model=list[OutstandingOut])
def outstanding(
    now: datetime | None = Query(default=None),
    work: UnitOfWork = Work,
    key: ApiKey = Reader,
    today: date = Today,
) -> list[OutstandingOut]:
    moment = now or datetime.combine(today, datetime.min.time())
    lines = []
    for row_id, incident, _ in work.incidents.numbered_unfiled():
        waited = (
            Duration.from_seconds(int((moment - incident.at).total_seconds()))
            if moment >= incident.at
            else Duration(0)
        )
        lines.append(
            OutstandingOut(
                id=row_id,
                lift=incident.lift,
                at=incident.at,
                kind=incident.kind.value,
                due_by=incident.due_by(),
                overdue=moment > incident.due_by(),
                outstanding_for=waited,
            )
        )
    return sorted(lines, key=lambda one: one.due_by)


@router.get("/incidents/{incident_id}", response_model=IncidentOut)
def read_incident(incident_id: int, look: Lookup = Look, key: ApiKey = Reader) -> IncidentOut:
    incident, filing = look.incident(incident_id)
    return IncidentOut.of(incident_id, incident, filing)


@router.post(
    "/incidents/{incident_id}/filing",
    response_model=FilingOut,
    status_code=status.HTTP_201_CREATED,
)
def file_incident(
    incident_id: int,
    body: FilingIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Manager,
) -> FilingOut:
    incident, existing = look.incident(incident_id)
    if existing is not None:
        raise StorageError(f"incident {incident_id} was filed as {existing}")
    filing = work.incidents.file(
        incident_id,
        FilingKind.INCIDENT,
        body.filed,
        body.filed_by,
    )
    work.commit()
    return FilingOut.of(filing, incident)
