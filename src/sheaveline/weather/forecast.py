"""The forecast, read against the same rules the anemometer is read against.

The department does not act on a forecast, it plans against one. Whether to put
a second crew on the summit lift at ten, whether to warn the ski school that the
tram will be down after two, whether to run the groomers up the far side while
the wind is still under the limit.

The one thing that must not happen is a forecast judged by a different standard
from a reading. So a period is turned into an observation and handed to the same
hold engine, and if the answer disagrees with what the anemometer says later,
that is the weather being wrong rather than the software.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from enum import Enum

from sheaveline.measures.length import Length
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.plant.lift import Lift
from sheaveline.weather.hold import Hold, WeatherHold
from sheaveline.weather.observation import (
    UNLIMITED_VISIBILITY,
    Observation,
    Precipitation,
    Station,
    WeatherError,
)


class Confidence(Enum):
    """How much of the forecast the office is prepared to act on."""

    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"

    @property
    def worth_planning_against(self) -> bool:
        return self is not Confidence.LOW


@dataclass(frozen=True)
class ForecastPeriod:
    """What the weather is expected to do over one stretch of the day."""

    starts: datetime
    ends: datetime
    wind: Wind
    temperature: Temperature
    confidence: Confidence = Confidence.MODERATE
    visibility: Length = UNLIMITED_VISIBILITY
    precipitation: Precipitation = Precipitation.NONE
    rime: bool = False

    def __post_init__(self) -> None:
        if self.ends <= self.starts:
            raise WeatherError("a forecast period runs forwards")
        if self.visibility.mils < 0:
            raise WeatherError("visibility is not a negative distance")

    def covers(self, moment: datetime) -> bool:
        return self.starts <= moment < self.ends

    def as_observation(self, station: Station) -> Observation:
        """The same shape the anemometer produces, so the same rules can read it."""
        return Observation(
            station=station,
            taken=self.starts,
            wind=self.wind,
            temperature=self.temperature,
            visibility=self.visibility,
            precipitation=self.precipitation,
            rime=self.rime,
        )

    def __str__(self) -> str:
        return f"{self.starts:%H:%M} to {self.ends:%H:%M}, {self.wind}"


@dataclass(frozen=True)
class Forecast:
    """One forecast, issued once, covering a run of periods."""

    station: Station
    issued: datetime
    issued_by: str
    periods: tuple[ForecastPeriod, ...]

    def __post_init__(self) -> None:
        if not self.issued_by.strip():
            raise WeatherError("a forecast names who issued it")
        if not self.periods:
            raise WeatherError("a forecast has periods in it")
        for earlier, later in zip(self.periods, self.periods[1:], strict=False):
            if later.starts < earlier.ends:
                raise WeatherError("two forecast periods overlap")

    @classmethod
    def of(
        cls,
        station: Station,
        issued: datetime,
        issued_by: str,
        periods: Sequence[ForecastPeriod],
    ) -> Forecast:
        ordered = tuple(sorted(periods, key=lambda one: one.starts))
        return cls(station, issued, issued_by, ordered)

    def at(self, moment: datetime) -> ForecastPeriod | None:
        for period in self.periods:
            if period.covers(moment):
                return period
        return None

    def windiest(self) -> ForecastPeriod:
        return max(self.periods, key=lambda one: one.wind.peak.mils_per_minute)

    def coldest(self) -> ForecastPeriod:
        return min(self.periods, key=lambda one: one.temperature.decidegrees_f)

    def holds_in(self, period: ForecastPeriod, lift: Lift) -> tuple[Hold, ...]:
        """The same engine that reads the anemometer, given a forecast instead."""
        return WeatherHold(lift, period.as_observation(self.station)).holds()

    def expected_holds(self, lift: Lift) -> tuple[ForecastPeriod, ...]:
        return tuple(one for one in self.periods if self.holds_in(one, lift))

    def first_hold(self, lift: Lift) -> ForecastPeriod | None:
        expected = self.expected_holds(lift)
        return expected[0] if expected else None

    def clear_periods(self, lift: Lift) -> tuple[ForecastPeriod, ...]:
        return tuple(one for one in self.periods if not self.holds_in(one, lift))

    def clear_after(self, lift: Lift, moment: datetime) -> datetime | None:
        """When the machine is next expected to be runnable, or nothing."""
        for period in self.periods:
            if period.ends <= moment:
                continue
            if not self.holds_in(period, lift):
                return max(period.starts, moment)
        return None

    def worth_acting_on(self) -> tuple[ForecastPeriod, ...]:
        return tuple(one for one in self.periods if one.confidence.worth_planning_against)

    def covers(self) -> tuple[datetime, datetime]:
        return (self.periods[0].starts, self.periods[-1].ends)

    def age_at(self, now: datetime) -> int:
        """How many whole hours old the forecast is, which is how far to trust it."""
        if now < self.issued:
            raise WeatherError("that moment is before the forecast was issued")
        return int((now - self.issued).total_seconds() // 3600)

    def __len__(self) -> int:
        return len(self.periods)
