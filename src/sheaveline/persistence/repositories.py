"""Repositories, one a thing the department talks about.

Each one takes a session and hands back domain objects rather than rows, so
nothing above this line ever imports a model. Everything that reads a lift reads
it through here, which is the only way the mapper stays the single place the two
shapes meet.

The seven of them used to open with the same three lines of constructor and then
repeat the same fetch and complain pattern fourteen times between them, each with
its own wording for the same failure. That is on the base class now, and what is
left in each repository is the part that is about ropes or incidents rather than
about sessions.
"""

from __future__ import annotations

from datetime import date, datetime, time
from typing import TypeVar

from sqlalchemy import Select, or_, select
from sqlalchemy.orm import Session

from sheaveline.board.filing import Filing, FilingKind
from sheaveline.board.incident import Incident
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.wind import Wind
from sheaveline.ops.logbook import DayLog
from sheaveline.ops.meters import MeterHistory, MeterReading
from sheaveline.ops.stop import StopEvent, StopLog
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.mappers.ops import (
    day_log_from_row,
    day_log_to_row,
    hold_from_row,
    hold_to_row,
    meter_from_row,
    meter_to_row,
    stop_from_row,
    stop_log_from_rows,
    stop_to_row,
)
from sheaveline.persistence.mappers.plant import (
    lift_from_row,
    lift_to_row,
    rope_from_row,
    rope_to_row,
)
from sheaveline.persistence.mappers.ropework import wear_from_row, wear_to_row
from sheaveline.persistence.models.board import FilingRow, IncidentRow
from sheaveline.persistence.models.ops import DayLogRow, HoldRow, MeterReadingRow, StopRow
from sheaveline.persistence.models.plant import HaulRopeRow, LiftRow
from sheaveline.persistence.models.ropework import WearSurveyRow
from sheaveline.persistence.models.upkeep import (
    DefectRow,
    InspectionRecordRow,
    WorkOrderRow,
)
from sheaveline.persistence.models.weather import ObservationRow, StationRow
from sheaveline.persistence.numbering import take_number
from sheaveline.plant.identity import FilingNumber, LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.wear import WearSurvey
from sheaveline.upkeep.record import Defect, InspectionRecord
from sheaveline.upkeep.workorder import Priority, WorkOrder
from sheaveline.weather.holdlog import HoldRecord, HoldRegister
from sheaveline.weather.observation import (
    STALE_AFTER,
    Observation,
    ObservationLog,
    Station,
)

RowT = TypeVar("RowT")


class Repository:
    """The session plumbing every repository would otherwise repeat."""

    __slots__ = ("session",)

    def __init__(self, session: Session) -> None:
        self.session = session

    def fetch(self, kind: type[RowT], key: object, missing: str) -> RowT:
        """Get a row by its key, or say which one was asked for."""
        row = self.session.get(kind, key)
        if row is None:
            raise StorageError(missing)
        return row

    def find(self, kind: type[RowT], key: object) -> RowT | None:
        return self.session.get(kind, key)

    def one(self, statement: Select[tuple[RowT]], missing: str) -> RowT:
        row = self.session.scalar(statement)
        if row is None:
            raise StorageError(missing)
        return row

    def many(self, statement: Select[tuple[RowT]]) -> list[RowT]:
        return list(self.session.scalars(statement))


class RopeRepository(Repository):
    """Haul ropes, by their mill serial."""

    def add(self, rope: HaulRope, lift_code: LiftCode | None = None) -> None:
        if self.exists(rope.serial):
            raise StorageError(f"rope {rope.serial} is already on file")
        self.session.add(rope_to_row(rope, lift_code))

    def get(self, serial: RopeSerial) -> HaulRope:
        return rope_from_row(self.fetch(HaulRopeRow, serial, f"there is no rope {serial}"))

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

    def attach(self, serial: RopeSerial, lift_code: LiftCode) -> None:
        """Put a rope already on file onto a machine.

        A rope taken off a machine is left on file as a spare rather than
        deleted, so putting the same machine back has to find it again and
        claim it. A rope that is on some other machine is refused, because a
        rope in two places is a records error rather than a spare.
        """
        row = self.fetch(HaulRopeRow, serial, f"there is no rope {serial}")
        if row.lift_code is not None and row.lift_code != lift_code:
            raise StorageError(f"rope {serial} is already on {row.lift_code}")
        row.lift_code = lift_code

    def detach(self, serial: RopeSerial) -> None:
        found = self.fetch(HaulRopeRow, serial, f"there is no rope {serial}")
        found.lift_code = None

    def remove(self, serial: RopeSerial) -> None:
        self.session.delete(self.fetch(HaulRopeRow, serial, f"there is no rope {serial}"))


class LiftRepository(Repository):
    """Machines, by the code painted on the terminal."""

    def __init__(self, session: Session) -> None:
        super().__init__(session)
        self.ropes = RopeRepository(session)

    def add(self, lift: Lift) -> None:
        if self.exists(lift.code):
            raise StorageError(f"lift {lift.code} is already on file")
        # The machine goes in first so the rope has something to point at,
        # whether it is new or a spare being claimed back.
        self.session.add(lift_to_row(lift))
        self.session.flush()
        if self.ropes.exists(lift.rope.serial):
            self.ropes.attach(lift.rope.serial, lift.code)
        else:
            self.ropes.add(lift.rope, lift.code)

    def get(self, code: LiftCode) -> Lift:
        row = self.fetch(LiftRow, code, f"there is no lift {code}")
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
        self.session.delete(self.fetch(LiftRow, code, f"there is no lift {code}"))


class WearSurveyRepository(Repository):
    """Rope walks, by the rope they were taken on."""

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


class IncidentRepository(Repository):
    """Incidents, by the machine and the moment."""

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

    def record(self, incident: Incident) -> int:
        """Write an incident and hand back the id the routes address it by."""
        row = IncidentRow(
            lift_code=incident.lift,
            at=incident.at,
            kind=incident.kind,
            injury=incident.injury,
            people=incident.people,
            narrative=incident.narrative,
            reported_by=incident.reported_by,
        )
        self.session.add(row)
        self.session.flush()
        return row.id

    def get(self, incident_id: int) -> tuple[Incident, FilingNumber | None]:
        row = self.fetch(IncidentRow, incident_id, f"there is no incident {incident_id}")
        return self._to_domain(row), row.filing_number

    def numbered_for_lift(self, code: LiftCode) -> list[tuple[int, Incident, FilingNumber | None]]:
        rows = self.session.scalars(
            select(IncidentRow).where(IncidentRow.lift_code == code).order_by(IncidentRow.at)
        )
        return [(row.id, self._to_domain(row), row.filing_number) for row in rows]

    def numbered_unfiled(self) -> list[tuple[int, Incident, FilingNumber | None]]:
        rows = self.session.scalars(
            select(IncidentRow).where(IncidentRow.filing_number.is_(None)).order_by(IncidentRow.at)
        )
        return [(row.id, self._to_domain(row), None) for row in rows]

    def file(
        self,
        incident_id: int,
        kind: FilingKind,
        filed: datetime,
        filed_by: str,
    ) -> Filing:
        """Take the next board number, write the filing and hang it on the incident."""
        row = self.fetch(IncidentRow, incident_id, f"there is no incident {incident_id}")
        if row.filing_number is not None:
            raise StorageError(f"incident {incident_id} is already filed")
        number = take_number(self.session, "UT", filed.year)
        self.session.add(
            FilingRow(
                number=number,
                kind=kind,
                lift_code=row.lift_code,
                filed=filed,
                about=row.narrative,
                filed_by=filed_by,
            )
        )
        self.session.flush()
        row.filing_number = number
        self.session.flush()
        return Filing(
            number=number,
            kind=kind,
            lift=row.lift_code,
            filed=filed,
            about=row.narrative,
            filed_by=filed_by,
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


class InspectionRepository(Repository):
    """Inspection records and the defects hanging off them."""

    def add(self, record: InspectionRecord) -> None:
        row = InspectionRecordRow(
            lift_code=record.lift,
            code=record.code,
            done=record.done,
            by=record.by,
            hours=record.hours,
            cycles=record.cycles,
        )
        for defect in record.defects:
            row.defects.append(
                DefectRow(
                    where=defect.where,
                    level=defect.level,
                    detail=defect.detail,
                    raised=defect.raised,
                    cleared=defect.cleared,
                    cleared_by=defect.cleared_by,
                )
            )
        self.session.add(row)

    def for_lift(self, code: LiftCode, job: str | None = None) -> list[InspectionRecord]:
        statement = select(InspectionRecordRow).where(InspectionRecordRow.lift_code == code)
        if job is not None:
            statement = statement.where(InspectionRecordRow.code == job.upper())
        rows = self.session.scalars(statement.order_by(InspectionRecordRow.done))
        return [self._to_domain(row) for row in rows]

    def last_done(self, code: LiftCode, job: str) -> InspectionRecord | None:
        found = self.for_lift(code, job)
        return found[-1] if found else None

    def open_defects(self, code: LiftCode | None = None) -> list[Defect]:
        statement = select(DefectRow).where(DefectRow.cleared.is_(None))
        if code is not None:
            statement = statement.join(InspectionRecordRow).where(
                InspectionRecordRow.lift_code == code
            )
        rows = self.session.scalars(statement.order_by(DefectRow.raised))
        return [self._defect(row) for row in rows]

    def clear_defect(self, where: str, raised: date, on: date, by: str) -> Defect:
        row = self.session.scalar(
            select(DefectRow).where(DefectRow.where == where, DefectRow.raised == raised)
        )
        if row is None:
            raise StorageError(f"there is no open defect at {where} raised {raised}")
        cleared = self._defect(row).clear(on, by)
        row.cleared = cleared.cleared
        row.cleared_by = cleared.cleared_by
        return cleared

    def _defect(self, row: DefectRow) -> Defect:
        return Defect(
            where=row.where,
            level=row.level,
            detail=row.detail,
            raised=row.raised,
            cleared=row.cleared,
            cleared_by=row.cleared_by,
        )

    def _to_domain(self, row: InspectionRecordRow) -> InspectionRecord:
        return InspectionRecord.of(
            lift=row.lift_code,
            code=row.code,
            done=row.done,
            by=row.by,
            hours=row.hours,
            cycles=row.cycles,
            defects=[self._defect(one) for one in row.defects],
        )


class WorkOrderRepository(Repository):
    """Work orders, by their number."""

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
        return self._to_domain(self.fetch(WorkOrderRow, number, f"there is no work order {number}"))

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
        row = self.fetch(WorkOrderRow, number, f"there is no work order {number}")
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


class WeatherRepository(Repository):
    """Stations and everything they have reported."""

    def add_station(self, station: Station) -> None:
        if self.session.get(StationRow, station.name) is not None:
            raise StorageError(f"there is already a station called {station.name}")
        self.session.add(StationRow(name=station.name, elevation=station.elevation))

    def station(self, name: str) -> Station:
        row = self.fetch(StationRow, name, f"there is no station called {name}")
        return Station(row.name, row.elevation)

    def stations(self) -> list[Station]:
        rows = self.session.scalars(select(StationRow).order_by(StationRow.name))
        return [Station(row.name, row.elevation) for row in rows]

    def nearest_to(self, elevation: Length) -> Station:
        """The station whose elevation is closest to the point being asked about."""
        found = self.stations()
        if not found:
            raise StorageError("no station has been set up yet")
        return min(found, key=lambda one: abs((one.elevation - elevation).mils))

    def record(self, observation: Observation) -> None:
        if self.session.get(StationRow, observation.station.name) is None:
            raise StorageError(f"there is no station called {observation.station.name}")
        self.session.add(
            ObservationRow(
                station_name=observation.station.name,
                taken=observation.taken,
                wind_speed=observation.wind.speed,
                wind_bearing=observation.wind.bearing,
                wind_gust=observation.wind.gust,
                temperature=observation.temperature,
                visibility=observation.visibility,
                precipitation=observation.precipitation,
                rime=observation.rime,
                lightning_within=observation.lightning_within,
            )
        )

    def since(self, moment: datetime, station: Station | None = None) -> list[Observation]:
        statement = select(ObservationRow).where(ObservationRow.taken >= moment)
        if station is not None:
            statement = statement.where(ObservationRow.station_name == station.name)
        rows = self.session.scalars(statement.order_by(ObservationRow.taken))
        return [self._to_domain(row) for row in rows]

    def latest(self, station: Station) -> Observation:
        row = self.session.scalar(
            select(ObservationRow)
            .where(ObservationRow.station_name == station.name)
            .order_by(ObservationRow.taken.desc())
            .limit(1)
        )
        if row is None:
            raise StorageError(f"nothing has been read at {station.name}")
        return self._to_domain(row)

    def latest_fresh(
        self,
        station: Station,
        now: datetime,
        after: Duration = STALE_AFTER,
    ) -> Observation:
        """The most recent reading that is still evidence about now."""
        seen = self.latest(station)
        if seen.is_stale(now, after):
            raise StorageError(f"the last reading at {station.name} is stale")
        return seen

    def log(self, station: Station | None = None) -> ObservationLog:
        statement = select(ObservationRow)
        if station is not None:
            statement = statement.where(ObservationRow.station_name == station.name)
        rows = list(self.session.scalars(statement.order_by(ObservationRow.taken)))
        if not rows:
            raise StorageError("there are no readings to build a log from")
        return ObservationLog.of([self._to_domain(row) for row in rows])

    def _to_domain(self, row: ObservationRow) -> Observation:
        return Observation(
            station=Station(row.station.name, row.station.elevation),
            taken=row.taken,
            wind=Wind(row.wind_speed, row.wind_bearing, row.wind_gust),
            temperature=row.temperature,
            visibility=row.visibility,
            precipitation=row.precipitation,
            rime=row.rime,
            lightning_within=row.lightning_within,
        )


class DayLogRepository(Repository):
    """The day book, and the stops and meters that do not fit inside a day."""

    def __init__(self, session: Session) -> None:
        super().__init__(session)
        self.lifts = LiftRepository(session)

    def add(self, log: DayLog) -> None:
        if self.find_log(log.lift, log.day) is not None:
            raise StorageError(f"{log.lift} already has a log for {log.day.isoformat()}")
        self.session.add(day_log_to_row(log))
        if log.meter is not None:
            self.read_meter(log.meter)
        if log.stops is not None:
            for stop in log.stops.stops:
                self.stop(stop)
        self.session.flush()

    def find_log(self, code: LiftCode, day: date) -> DayLogRow | None:
        return self.session.scalar(
            select(DayLogRow).where(DayLogRow.lift_code == code, DayLogRow.day == day)
        )

    def get(self, code: LiftCode, day: date) -> DayLog:
        row = self.one(
            select(DayLogRow).where(DayLogRow.lift_code == code, DayLogRow.day == day),
            f"there is no log for {code} on {day.isoformat()}",
        )
        return day_log_from_row(
            row,
            self.lifts.get(code),
            stops=self.stops_on(code, day),
            meter=self.meter_on(code, day),
        )

    def days_logged(self, code: LiftCode) -> list[date]:
        return [
            one.day
            for one in self.many(
                select(DayLogRow).where(DayLogRow.lift_code == code).order_by(DayLogRow.day)
            )
        ]

    def between(self, code: LiftCode, start: date, end: date) -> list[DayLog]:
        lift = self.lifts.get(code)
        rows = self.many(
            select(DayLogRow)
            .where(
                DayLogRow.lift_code == code,
                DayLogRow.day >= start,
                DayLogRow.day <= end,
            )
            .order_by(DayLogRow.day)
        )
        return [
            day_log_from_row(
                one,
                lift,
                stops=self.stops_on(code, one.day),
                meter=self.meter_on(code, one.day),
            )
            for one in rows
        ]

    def replace(self, log: DayLog) -> None:
        """Rewriting a day, which happens when the log is signed off next morning."""
        found = self.find_log(log.lift, log.day)
        if found is None:
            raise StorageError(f"there is no log for {log.lift} on {log.day.isoformat()}")
        self.session.delete(found)
        self.session.flush()
        self.session.add(day_log_to_row(log))
        self.session.flush()

    def stop(self, event: StopEvent) -> None:
        clash = self.session.scalar(
            select(StopRow).where(StopRow.lift_code == event.lift, StopRow.stopped == event.stopped)
        )
        if clash is not None:
            raise StorageError(f"{event.lift} is already stopped at {event.stopped:%H:%M}")
        self.session.add(stop_to_row(event))
        self.session.flush()

    def restart(
        self,
        code: LiftCode,
        stopped: datetime,
        when: datetime,
        by: str,
        line_checked: bool = False,
    ) -> StopEvent:
        """The domain decides whether that is allowed, not the table."""
        row = self.one(
            select(StopRow).where(StopRow.lift_code == code, StopRow.stopped == stopped),
            f"there is no stop on {code} at {stopped:%H:%M}",
        )
        restarted = stop_from_row(row).restart(when, by, line_checked)
        row.restarted = restarted.restarted
        row.restarted_by = restarted.restarted_by
        row.line_checked = restarted.line_checked
        self.session.flush()
        return restarted

    def stops_on(self, code: LiftCode, day: date) -> StopLog:
        """Every stop that touched the day, which is not every stop that began on it."""
        opened = datetime.combine(day, time.min)
        shut = datetime.combine(day, time.max)
        rows = self.many(
            select(StopRow)
            .where(
                StopRow.lift_code == code,
                StopRow.stopped <= shut,
                or_(StopRow.restarted.is_(None), StopRow.restarted >= opened),
            )
            .order_by(StopRow.stopped)
        )
        return stop_log_from_rows(code, rows)

    def open_stops(self, code: LiftCode | None = None) -> list[StopEvent]:
        statement = select(StopRow).where(StopRow.restarted.is_(None))
        if code is not None:
            statement = statement.where(StopRow.lift_code == code)
        return [stop_from_row(one) for one in self.many(statement.order_by(StopRow.stopped))]

    def call_hold(self, record: HoldRecord) -> None:
        self.session.add(hold_to_row(record))
        self.session.flush()

    def clear_hold(self, code: LiftCode, started: datetime, when: datetime, by: str) -> HoldRecord:
        row = self.one(
            select(HoldRow).where(HoldRow.lift_code == code, HoldRow.started == started),
            f"there is no hold on {code} from {started:%H:%M}",
        )
        cleared = hold_from_row(row).closed(when, by)
        row.ended = cleared.ended
        row.ended_by = cleared.ended_by
        self.session.flush()
        return cleared

    def holds(self, code: LiftCode | None = None) -> HoldRegister:
        statement = select(HoldRow)
        if code is not None:
            statement = statement.where(HoldRow.lift_code == code)
        rows = self.many(statement.order_by(HoldRow.started))
        return HoldRegister.of([hold_from_row(one) for one in rows])

    def read_meter(self, reading: MeterReading) -> None:
        clash = self.session.scalar(
            select(MeterReadingRow).where(
                MeterReadingRow.lift_code == reading.lift,
                MeterReadingRow.day == reading.day,
            )
        )
        if clash is not None:
            raise StorageError(f"{reading.lift} was already read on {reading.day.isoformat()}")
        self.session.add(meter_to_row(reading))
        self.session.flush()

    def meter_on(self, code: LiftCode, day: date) -> MeterReading | None:
        row = self.session.scalar(
            select(MeterReadingRow).where(
                MeterReadingRow.lift_code == code, MeterReadingRow.day == day
            )
        )
        return None if row is None else meter_from_row(row)

    def meters(self, code: LiftCode) -> MeterHistory | None:
        """Nothing rather than an empty history, which the domain refuses to make."""
        rows = self.many(
            select(MeterReadingRow)
            .where(MeterReadingRow.lift_code == code)
            .order_by(MeterReadingRow.day)
        )
        if not rows:
            return None
        return MeterHistory.of(code, [meter_from_row(one) for one in rows])
