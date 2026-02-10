"""The unit of work, which is the only thing above here that knows about commits.

Everything the department does is a small transaction with an obvious boundary.
Sign off a morning walk. Record a rope survey. Raise a work order off a defect.
Each of those either happens or does not, and a half written one is worse than
none, because the next person reads it and believes it.

So the boundary is a context manager, it rolls back unless somebody says commit,
and the repositories only exist inside it. There is deliberately no way to get a
repository without a unit of work around it.
"""

from __future__ import annotations

from types import TracebackType

from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from sheaveline.persistence.base import StorageError, build_sessions
from sheaveline.persistence.repositories import (
    IncidentRepository,
    InspectionRepository,
    LiftRepository,
    RopeRepository,
    WearSurveyRepository,
    WeatherRepository,
    WorkOrderRepository,
)


class UnitOfWork:
    """One transaction, with every repository hanging off it."""

    def __init__(self, sessions: sessionmaker[Session]) -> None:
        self._sessions = sessions
        self._session: Session | None = None
        self._committed = False

    @classmethod
    def on(cls, engine: Engine) -> UnitOfWork:
        return cls(build_sessions(engine))

    def __enter__(self) -> UnitOfWork:
        if self._session is not None:
            raise StorageError("this unit of work is already open")
        self._session = self._sessions()
        self._committed = False
        return self

    def __exit__(
        self,
        kind: type[BaseException] | None,
        value: BaseException | None,
        trace: TracebackType | None,
    ) -> None:
        session = self._require()
        try:
            if kind is not None or not self._committed:
                session.rollback()
        finally:
            session.close()
            self._session = None

    def _require(self) -> Session:
        if self._session is None:
            raise StorageError("this unit of work is not open")
        return self._session

    @property
    def session(self) -> Session:
        return self._require()

    @property
    def is_open(self) -> bool:
        return self._session is not None

    @property
    def was_committed(self) -> bool:
        return self._committed

    def commit(self) -> None:
        self._require().commit()
        self._committed = True

    def rollback(self) -> None:
        self._require().rollback()
        self._committed = False

    def flush(self) -> None:
        """Push the pending writes so the database can refuse them now."""
        self._require().flush()

    @property
    def lifts(self) -> LiftRepository:
        return LiftRepository(self._require())

    @property
    def ropes(self) -> RopeRepository:
        return RopeRepository(self._require())

    @property
    def wear_surveys(self) -> WearSurveyRepository:
        return WearSurveyRepository(self._require())

    @property
    def incidents(self) -> IncidentRepository:
        return IncidentRepository(self._require())

    @property
    def inspections(self) -> InspectionRepository:
        return InspectionRepository(self._require())

    @property
    def weather(self) -> WeatherRepository:
        return WeatherRepository(self._require())

    @property
    def work_orders(self) -> WorkOrderRepository:
        return WorkOrderRepository(self._require())
