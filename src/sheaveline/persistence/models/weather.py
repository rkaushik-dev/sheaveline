"""The tables the weather log lives in.

A station is a row rather than a string on every reading, because the elevation
is what decides which station a lift reads and an elevation typed into four
thousand readings is an elevation that is wrong in one of them.

The wind is stored as three columns rather than one, because a gust is not a
wind and the hold rules read them separately. Lightning is a distance and a
null, not a flag, because how far away it was is the whole question.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from sheaveline.measures.angle import Bearing
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.persistence.base import Base
from sheaveline.persistence.models.plant import named_enum
from sheaveline.persistence.types import (
    BearingColumn,
    LengthColumn,
    SpeedColumn,
    TemperatureColumn,
)
from sheaveline.weather.observation import Precipitation


class StationRow(Base):
    __tablename__ = "weather_stations"

    name: Mapped[str] = mapped_column(String(32), primary_key=True)
    elevation: Mapped[Length] = mapped_column(LengthColumn)

    observations: Mapped[list[ObservationRow]] = relationship(
        back_populates="station", cascade="all, delete-orphan"
    )


class ObservationRow(Base):
    __tablename__ = "observations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    station_name: Mapped[str] = mapped_column(String(32), ForeignKey("weather_stations.name"))
    taken: Mapped[datetime] = mapped_column(DateTime)
    wind_speed: Mapped[Speed] = mapped_column(SpeedColumn)
    wind_bearing: Mapped[Bearing] = mapped_column(BearingColumn)
    wind_gust: Mapped[Speed | None] = mapped_column(SpeedColumn, nullable=True)
    temperature: Mapped[Temperature] = mapped_column(TemperatureColumn)
    visibility: Mapped[Length] = mapped_column(LengthColumn)
    precipitation: Mapped[Precipitation] = mapped_column(named_enum(Precipitation, "precipitation"))
    rime: Mapped[bool] = mapped_column(Boolean, default=False)
    lightning_within: Mapped[Length | None] = mapped_column(LengthColumn, nullable=True)

    station: Mapped[StationRow] = relationship(back_populates="observations")

    __table_args__ = (
        UniqueConstraint("station_name", "taken", name="one_reading_a_moment"),
        CheckConstraint("visibility >= 0", name="visibility_not_negative"),
        CheckConstraint(
            "wind_gust is null or wind_gust >= wind_speed", name="gust_above_sustained"
        ),
        # Everything since a moment, across every station, is what the hold log reads.
        Index("ix_observations_by_moment", "taken"),
    )
