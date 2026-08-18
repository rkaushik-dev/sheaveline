"""The evacuation plan on the wire, for the tablet the duty manager carries.

The teams come up in the request rather than out of the database, because the
teams on the hill on any given afternoon are whoever turned up, and the shift
board is not something this system holds. What it does hold is the line, which is
the half nobody can type in from memory.

The beat sheet goes back in the order the teams work it, top to bottom, because
that is the order somebody reads it out over the radio.
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import DurationText, LengthFeet, LiftCodeText
from sheaveline.evac.staging import Beat, StagingPlan


class TeamIn(BaseModel):
    """One team off the truck, with what is actually on it."""

    name: str = Field(min_length=1, max_length=32)
    ladder_feet: int | None = Field(default=None, ge=1, le=60)
    rope_feet: int | None = Field(default=None, ge=1, le=1000)
    people: int = Field(default=3, ge=2, le=8)


class StagingIn(BaseModel):
    teams: list[TeamIn] = Field(min_length=1, max_length=12)
    seats_filled: int = Field(default=3, ge=1, le=8)
    snow: str = "2 ft"
    tension: str = "34500 lb"


class BeatOut(BaseModel):
    team: str
    staged_at: LengthFeet
    carriers: int
    walking: LengthFeet
    busy_for: DurationText
    reaches_everything: bool

    @classmethod
    def of(cls, beat: Beat) -> BeatOut:
        return cls(
            team=beat.team.name,
            staged_at=beat.staged_at,
            carriers=len(beat.stops),
            walking=beat.walking(),
            busy_for=beat.work(),
            reaches_everything=beat.reaches_everything(),
        )


class StagingOut(BaseModel):
    lift: LiftCodeText
    workable: bool
    clears_in: DurationText | None
    serial_time: DurationText | None
    spread: DurationText | None
    carriers: int
    stranded: int
    beats: list[BeatOut]
    refusals: list[str]

    @classmethod
    def of(cls, plan: StagingPlan) -> StagingOut:
        workable = bool(plan.beats)
        return cls(
            lift=plan.profile.lift.code,
            workable=plan.is_workable(),
            clears_in=plan.clears_in() if workable else None,
            serial_time=plan.serial_time() if workable else None,
            spread=plan.spread() if workable else None,
            carriers=len(plan.stops()),
            stranded=len(plan.stranded),
            beats=[BeatOut.of(one) for one in plan.beats],
            refusals=list(plan.refusals()),
        )
