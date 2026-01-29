"""What the board's paperwork looks like on the wire.

Enumerations travel as the sentence a person reads rather than as the name the
code uses, in both directions, because these bodies are read by hand as often
as they are parsed. The names are what the database stores; the wire is the
department's own vocabulary.

The incident carries its deadline and the clock that set it, because the
question everybody asks about an incident is not what happened, it is how long
there is. Whichever of the two clocks is shorter is named in the reply so
nobody has to work it out from the kind and the injury for themselves.
"""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import DurationText, FilingNumberText, LiftCodeText
from sheaveline.board.filing import Filing
from sheaveline.board.incident import Incident, IncidentKind, InjuryLevel


class IncidentIn(BaseModel):
    """What an operator types in after the machine has stopped."""

    at: datetime
    kind: IncidentKind
    injury: InjuryLevel = InjuryLevel.NONE
    people: int = Field(default=0, ge=0)
    narrative: str = Field(min_length=1)
    reported_by: str = Field(min_length=1)

    def to_domain(self, lift: LiftCodeText) -> Incident:
        return Incident(
            lift=lift,
            at=self.at,
            kind=self.kind,
            injury=self.injury,
            people=self.people,
            narrative=self.narrative,
            reported_by=self.reported_by,
        )


class IncidentOut(BaseModel):
    id: int
    lift: LiftCodeText
    at: datetime
    kind: str
    injury: str
    people: int
    narrative: str
    reported_by: str
    deadline: DurationText
    deadline_driver: str
    due_by: datetime
    holds_the_machine: bool
    filing: FilingNumberText | None

    @classmethod
    def of(cls, row_id: int, incident: Incident, filing: object = None) -> IncidentOut:
        return cls(
            id=row_id,
            lift=incident.lift,
            at=incident.at,
            kind=incident.kind.value,
            injury=incident.injury.value,
            people=incident.people,
            narrative=incident.narrative,
            reported_by=incident.reported_by,
            deadline=incident.deadline(),
            deadline_driver=incident.deadline_driver(),
            due_by=incident.due_by(),
            holds_the_machine=incident.holds_the_machine(),
            filing=filing,
        )


class OutstandingOut(BaseModel):
    """One line of the Monday morning list."""

    id: int
    lift: LiftCodeText
    at: datetime
    kind: str
    due_by: datetime
    overdue: bool
    outstanding_for: DurationText


class FilingIn(BaseModel):
    filed: datetime
    filed_by: str = Field(min_length=1)


class FilingOut(BaseModel):
    number: FilingNumberText
    kind: str
    lift: LiftCodeText
    filed: datetime
    about: str
    filed_by: str
    late: bool
    lateness: DurationText

    @classmethod
    def of(cls, filing: Filing, incident: Incident) -> FilingOut:
        return cls(
            number=filing.number,
            kind=filing.kind.value,
            lift=filing.lift,
            filed=filing.filed,
            about=filing.about,
            filed_by=filing.filed_by,
            late=filing.was_late(incident),
            lateness=filing.lateness(incident),
        )
