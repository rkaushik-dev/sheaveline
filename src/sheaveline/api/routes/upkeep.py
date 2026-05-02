"""Inspections, defects and the work orders that come off them.

The one route worth reading twice is the raise. It takes a defect and returns an
order, and the priority on the order is not in the request body at all. A client
cannot ask for a serious defect to be scheduled for the summer, which is exactly
what a client would eventually ask for.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal

from fastapi import APIRouter, Query, status

from sheaveline.api.deps import Manager, Mechanic, Reader, Today, Work
from sheaveline.api.lookups import Look, Lookup
from sheaveline.api.schemas.upkeep import (
    CloseOrderIn,
    DefectOut,
    InspectionIn,
    InspectionOut,
    WorkOrderIn,
    WorkOrderOut,
)
from sheaveline.api.security import ApiKey
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.upkeep.record import Defect, InspectionRecord
from sheaveline.upkeep.workorder import Priority, WorkOrder

router = APIRouter(tags=["upkeep"])


@router.post(
    "/lifts/{code}/inspections",
    response_model=InspectionOut,
    status_code=status.HTTP_201_CREATED,
)
def record_inspection(
    code: str,
    body: InspectionIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Mechanic,
) -> InspectionOut:
    lift = look.lift_code(code)
    record = InspectionRecord.of(
        lift=lift,
        code=body.code,
        done=body.done,
        by=body.by,
        hours=None if body.hours is None else Decimal(body.hours),
        cycles=body.cycles,
        defects=[one.to_domain(body.done) for one in body.defects],
    )
    work.inspections.add(record)
    work.commit()
    return InspectionOut.of(record)


@router.get("/lifts/{code}/inspections", response_model=list[InspectionOut])
def list_inspections(
    code: str,
    job: str | None = Query(default=None),
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> list[InspectionOut]:
    lift = look.lift_code(code)
    return [InspectionOut.of(one) for one in work.inspections.for_lift(lift, job)]


@router.get("/lifts/{code}/defects", response_model=list[DefectOut])
def list_defects(
    code: str, work: UnitOfWork = Work, look: Lookup = Look, key: ApiKey = Reader
) -> list[DefectOut]:
    lift = look.lift_code(code)
    return [DefectOut.of(one) for one in work.inspections.open_defects(lift)]


@router.post(
    "/lifts/{code}/work-orders",
    response_model=WorkOrderOut,
    status_code=status.HTTP_201_CREATED,
)
def raise_order(
    code: str,
    body: WorkOrderIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Mechanic,
) -> WorkOrderOut:
    lift = look.lift_code(code)
    defect = Defect(where=body.where, level=body.level, detail=body.detail, raised=body.raised)
    order = WorkOrder.from_defect(work.work_orders.next_number(), lift, defect, body.raised_by)
    work.work_orders.add(order)
    work.commit()
    return WorkOrderOut.of(order)


@router.get("/work-orders", response_model=list[WorkOrderOut])
def list_orders(
    lift: str | None = Query(default=None),
    priority: Priority | None = Query(default=None),
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Reader,
) -> list[WorkOrderOut]:
    if priority is not None:
        found = work.work_orders.of_priority(priority)
    else:
        code = None if lift is None else look.lift_code(lift)
        found = work.work_orders.open_orders(code)
    return [WorkOrderOut.of(one) for one in found]


@router.get("/work-orders/overdue", response_model=list[WorkOrderOut])
def list_overdue(
    work: UnitOfWork = Work, key: ApiKey = Reader, today: date = Today
) -> list[WorkOrderOut]:
    return [WorkOrderOut.of(one) for one in work.work_orders.open_orders() if one.is_overdue(today)]


@router.get("/work-orders/{number}", response_model=WorkOrderOut)
def read_order(number: int, look: Lookup = Look, key: ApiKey = Reader) -> WorkOrderOut:
    return WorkOrderOut.of(look.work_order(number))


@router.post("/work-orders/{number}/closure", response_model=WorkOrderOut)
def close_order(
    number: int,
    body: CloseOrderIn,
    work: UnitOfWork = Work,
    look: Lookup = Look,
    key: ApiKey = Manager,
) -> WorkOrderOut:
    look.work_order(number)
    closed = work.work_orders.close(number, body.closed, body.closed_by)
    work.commit()
    return WorkOrderOut.of(closed)
