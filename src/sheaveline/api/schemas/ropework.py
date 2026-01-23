"""What the rope file looks like on the wire.

The one shape here that goes in as well as out is the wear survey, because that
is what the mechanic types on the tablet standing next to the rope with the
caliper still in their hand. Everything it carries is a measure written as
text, and the readings are a list rather than a fixed number of them because
nobody knows in advance how many stations a walk will find worth writing down.
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import (
    LengthFeet,
    LengthInches,
    LoadText,
    RopeSerialText,
)
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.verdict import Finding
from sheaveline.ropework.wear import DiameterReading, LayReading, WearSurvey


class RopeOut(BaseModel):
    serial: RopeSerialText
    construction: str
    grade: str
    lay: str
    diameter: LengthInches
    condemning_diameter: LengthInches
    breaking_strength: LoadText
    maximum_tension: LoadText
    length: LengthFeet
    installed: date
    splices: int
    endless: bool

    @classmethod
    def of(cls, rope: HaulRope) -> RopeOut:
        return cls(
            serial=rope.serial,
            construction=rope.spec.construction.value,
            grade=rope.spec.grade.value,
            lay=rope.spec.lay.value,
            diameter=rope.diameter,
            condemning_diameter=rope.spec.condemning_diameter(),
            breaking_strength=rope.spec.breaking_strength,
            maximum_tension=rope.spec.maximum_tension(),
            length=rope.length,
            installed=rope.installed,
            splices=rope.splices,
            endless=rope.is_endless(),
        )


class DiameterReadingIn(BaseModel):
    station: LengthFeet
    across_crowns: LengthInches
    across_valleys: LengthInches

    def to_domain(self) -> DiameterReading:
        return DiameterReading(self.station, self.across_crowns, self.across_valleys)


class LayReadingIn(BaseModel):
    station: LengthFeet
    measured: LengthInches

    def to_domain(self) -> LayReading:
        return LayReading(self.station, self.measured)


class WearSurveyIn(BaseModel):
    taken: date
    inspector: str = Field(min_length=1)
    diameters: list[DiameterReadingIn] = Field(min_length=1)
    lays: list[LayReadingIn] = Field(default_factory=list)

    def to_domain(self, rope: HaulRope) -> WearSurvey:
        return WearSurvey.of(
            rope=rope,
            taken=self.taken,
            inspector=self.inspector,
            diameters=[one.to_domain() for one in self.diameters],
            lays=[one.to_domain() for one in self.lays],
        )


class DiameterReadingOut(BaseModel):
    station: LengthFeet
    mean: LengthInches
    ovality: LengthInches


class WearSurveyOut(BaseModel):
    serial: RopeSerialText
    taken: date
    inspector: str
    smallest: DiameterReadingOut
    readings: list[DiameterReadingOut]
    verdict: str

    @classmethod
    def of(cls, survey: WearSurvey, verdict: str) -> WearSurveyOut:
        def render(reading: DiameterReading) -> DiameterReadingOut:
            return DiameterReadingOut(
                station=reading.station,
                mean=reading.mean(),
                ovality=reading.ovality(),
            )

        return cls(
            serial=survey.rope.serial,
            taken=survey.taken,
            inspector=survey.inspector,
            smallest=render(survey.smallest()),
            readings=[render(one) for one in survey.diameters],
            verdict=verdict,
        )


class FindingOut(BaseModel):
    source: str
    verdict: str
    detail: str

    @classmethod
    def of(cls, finding: Finding) -> FindingOut:
        return cls(
            source=finding.source,
            verdict=finding.verdict.value,
            detail=finding.detail,
        )


class RopeFileOut(BaseModel):
    serial: RopeSerialText
    verdict: str
    may_run: bool
    sources: list[str]
    findings: list[FindingOut]
