"""Weather on the wire.

The hold reply is the shape worth thinking about. It carries the reading it was
made against, because the first question an operator asks after being told the
machine is held is which reading said so, and the second is how old it is.
"""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field

from sheaveline.api.schemas.measures import (
    BearingText,
    DurationText,
    LengthFeet,
    LiftCodeText,
    TemperatureText,
    WindText,
)
from sheaveline.measures.duration import Duration
from sheaveline.measures.wind import Wind
from sheaveline.weather.hold import WeatherHold
from sheaveline.weather.observation import (
    UNLIMITED_VISIBILITY,
    Observation,
    Precipitation,
    Station,
)


class StationIn(BaseModel):
    name: str = Field(min_length=1, max_length=32)
    elevation: LengthFeet

    def to_domain(self) -> Station:
        return Station(self.name, self.elevation)


class StationOut(BaseModel):
    name: str
    elevation: LengthFeet

    @classmethod
    def of(cls, station: Station) -> StationOut:
        return cls(name=station.name, elevation=station.elevation)


class ObservationIn(BaseModel):
    station: str = Field(min_length=1)
    taken: datetime
    wind: WindText
    bearing: BearingText
    gust: WindText | None = None
    temperature: TemperatureText
    # The default is the text a client would send rather than the Length it
    # becomes, because a Length is not JSON and pydantic drops a default it
    # cannot encode, with a warning nobody reads. Validating the default puts it
    # back through the same parser every other value goes through.
    visibility: LengthFeet = Field(
        default=UNLIMITED_VISIBILITY.format_feet_inches(), validate_default=True
    )
    precipitation: Precipitation = Precipitation.NONE
    rime: bool = False
    lightning_within: LengthFeet | None = None

    def to_domain(self, station: Station) -> Observation:
        return Observation(
            station=station,
            taken=self.taken,
            wind=Wind(self.wind, self.bearing, self.gust),
            temperature=self.temperature,
            visibility=self.visibility,
            precipitation=self.precipitation,
            rime=self.rime,
            lightning_within=self.lightning_within,
        )


class ObservationOut(BaseModel):
    station: str
    taken: datetime
    wind: WindText
    bearing: BearingText
    gust: WindText | None
    peak: WindText
    temperature: TemperatureText
    chill: TemperatureText
    visibility: LengthFeet
    precipitation: str
    icing: bool
    lightning_within: LengthFeet | None

    @classmethod
    def of(cls, seen: Observation) -> ObservationOut:
        return cls(
            station=seen.station.name,
            taken=seen.taken,
            wind=seen.wind.speed,
            bearing=seen.wind.bearing,
            gust=seen.wind.gust,
            peak=seen.wind.peak,
            temperature=seen.temperature,
            chill=seen.chill(),
            visibility=seen.visibility,
            precipitation=seen.precipitation.value,
            icing=seen.icing(),
            lightning_within=seen.lightning_within,
        )


class HoldReasonOut(BaseModel):
    reason: str
    detail: str
    clears_on_reading: bool


class HoldOut(BaseModel):
    lift: LiftCodeText
    held: bool
    may_run: bool
    needs_a_timer: bool
    gust_limit: WindText
    crosswind_limit: WindText | None
    crosswind: WindText
    reading_age: DurationText
    reading: ObservationOut
    reasons: list[HoldReasonOut]

    @classmethod
    def of(cls, decision: WeatherHold, now: datetime) -> HoldOut:
        seen = decision.observation
        age = (
            Duration.from_seconds(int((now - seen.taken).total_seconds()))
            if now >= seen.taken
            else Duration(0)
        )
        return cls(
            lift=decision.lift.code,
            held=decision.is_held(),
            may_run=decision.may_run(),
            needs_a_timer=decision.needs_a_timer(),
            gust_limit=decision.gust_limit(),
            crosswind_limit=decision.crosswind_limit(),
            crosswind=decision.crosswind(),
            reading_age=age,
            reading=ObservationOut.of(seen),
            reasons=[
                HoldReasonOut(
                    reason=one.reason.value,
                    detail=one.detail,
                    clears_on_reading=one.reason.clears_on_reading,
                )
                for one in decision.holds()
            ],
        )
