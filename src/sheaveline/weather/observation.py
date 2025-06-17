"""Weather observations, and the log of them the hold rules read.

There are three stations on this mountain and they disagree, which is the point
of having three. The base reads eighteen degrees and calm while the summit reads
minus four and gusting forty, and a lift that runs between them is held or not
held by whichever station is nearer the exposed end of it.

An observation goes stale. A wind reading from forty minutes ago is not evidence
about the wind now, and a rule that reads it as though it were is worse than a
rule with no reading at all, because it looks like it worked.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Final

from sheaveline.errors import SheavelineError
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind

STALE_AFTER: Final = Duration.from_minutes(20)
UNLIMITED_VISIBILITY: Final = Length.from_feet(30000)


class WeatherError(SheavelineError):
    """A weather record was built or read in a way that cannot hold."""


class Precipitation(Enum):
    """What is falling, which matters more than how much of it."""

    NONE = "none"
    SNOW = "snow"
    GRAUPEL = "graupel"
    RAIN = "rain"
    FREEZING_RAIN = "freezing rain"

    @property
    def wets_the_rope(self) -> bool:
        return self in {Precipitation.RAIN, Precipitation.FREEZING_RAIN}

    @property
    def builds_ice(self) -> bool:
        return self is Precipitation.FREEZING_RAIN


@dataclass(frozen=True)
class Station:
    """One weather station, and how high up the hill it sits."""

    name: str
    elevation: Length

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise WeatherError("a station has a name")

    def __str__(self) -> str:
        return self.name


@dataclass(frozen=True)
class Observation:
    """One reading off one station."""

    station: Station
    taken: datetime
    wind: Wind
    temperature: Temperature
    visibility: Length = UNLIMITED_VISIBILITY
    precipitation: Precipitation = Precipitation.NONE
    rime: bool = False
    lightning_within: Length | None = None

    def __post_init__(self) -> None:
        if self.visibility.mils < 0:
            raise WeatherError("visibility is not a negative distance")
        if self.lightning_within is not None and self.lightning_within.mils < 0:
            raise WeatherError("lightning distance is not negative")

    def chill(self) -> Temperature:
        return self.wind.chill(self.temperature)

    def age_at(self, now: datetime) -> Duration:
        if now < self.taken:
            raise WeatherError("that moment is before the observation was taken")
        return Duration.from_seconds(int((now - self.taken).total_seconds()))

    def is_stale(self, now: datetime, after: Duration = STALE_AFTER) -> bool:
        return self.age_at(now) > after

    def has_lightning(self) -> bool:
        return self.lightning_within is not None

    def icing(self) -> bool:
        """Rime on the towers, or freezing rain that will put it there."""
        return self.rime or self.precipitation.builds_ice

    def __str__(self) -> str:
        return f"{self.station} {self.wind}, {self.temperature.format_f(0)}"


@dataclass(frozen=True)
class ObservationLog:
    """Every reading from every station, in the order they arrived."""

    observations: tuple[Observation, ...]

    def __post_init__(self) -> None:
        if not self.observations:
            raise WeatherError("a log has readings in it")

    @classmethod
    def of(cls, observations: Sequence[Observation]) -> ObservationLog:
        ordered = tuple(sorted(observations, key=lambda seen: seen.taken))
        return cls(ordered)

    def stations(self) -> tuple[Station, ...]:
        seen: list[Station] = []
        for observation in self.observations:
            if observation.station not in seen:
                seen.append(observation.station)
        return tuple(seen)

    def from_station(self, station: Station) -> tuple[Observation, ...]:
        return tuple(seen for seen in self.observations if seen.station == station)

    def latest(self, station: Station | None = None) -> Observation:
        pool = self.observations if station is None else self.from_station(station)
        if not pool:
            raise WeatherError(f"nothing has been read at {station}")
        return pool[-1]

    def latest_fresh(self, now: datetime, station: Station | None = None) -> Observation:
        """The most recent reading that is still evidence about now."""
        pool = self.observations if station is None else self.from_station(station)
        for seen in reversed(pool):
            if not seen.is_stale(now):
                return seen
        raise WeatherError(f"no fresh reading at {station or 'any station'}")

    def highest_station(self) -> Station:
        return max(self.stations(), key=lambda station: station.elevation.mils)

    def nearest_to(self, elevation: Length) -> Station:
        """The station whose elevation is closest to the point being asked about."""
        return min(
            self.stations(),
            key=lambda station: abs((station.elevation - elevation).mils),
        )

    def worst_gust(self, station: Station | None = None) -> Observation:
        pool = self.observations if station is None else self.from_station(station)
        return max(pool, key=lambda seen: seen.wind.peak.mils_per_minute)

    def coldest(self, station: Station | None = None) -> Observation:
        pool = self.observations if station is None else self.from_station(station)
        return min(pool, key=lambda seen: seen.temperature.decidegrees_f)

    def with_observation(self, observation: Observation) -> ObservationLog:
        return ObservationLog.of([*self.observations, observation])

    def __len__(self) -> int:
        return len(self.observations)
