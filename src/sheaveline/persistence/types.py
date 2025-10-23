"""Column types that store a measure as the integer it already is.

Every measure in this package is an exact integer in a small base unit, so the
honest column is an integer column and the type decorator is a five line
conversion either side of it. Storing a length as a float would be storing a
number this system deliberately never produced.

The one that is worth reading twice is the length column. It stores mils, and a
mil is a thousandth of an inch, so a five thousand foot line is sixty million of
them. That fits in an ordinary signed integer with room to spare, and it means
two lengths compare in the database exactly as they compare in Python.
"""

from __future__ import annotations

from decimal import Decimal

from sqlalchemy import Dialect, Integer, String, TypeDecorator

from sheaveline.measures.angle import Angle, Bearing
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.torque import Torque
from sheaveline.plant.identity import FilingNumber, LiftCode, RopeSerial, TowerRef
from sheaveline.schedule.hours import TimeOfDay


class LengthColumn(TypeDecorator[Length]):
    """A length, stored as whole mils."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Length | None, dialect: Dialect) -> int | None:
        return None if value is None else value.mils

    def process_result_value(self, value: int | None, dialect: Dialect) -> Length | None:
        return None if value is None else Length(value)


class LoadColumn(TypeDecorator[Load]):
    """A load, stored as whole pounds."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Load | None, dialect: Dialect) -> int | None:
        return None if value is None else value.pounds

    def process_result_value(self, value: int | None, dialect: Dialect) -> Load | None:
        return None if value is None else Load(value)


class LoadPerFootColumn(TypeDecorator[LoadPerFoot]):
    """A weight per foot, stored as whole thousandths of a pound."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: LoadPerFoot | None, dialect: Dialect) -> int | None:
        return None if value is None else value.millipounds_per_foot

    def process_result_value(self, value: int | None, dialect: Dialect) -> LoadPerFoot | None:
        return None if value is None else LoadPerFoot(value)


class SpeedColumn(TypeDecorator[Speed]):
    """A speed, stored as whole mils a minute."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Speed | None, dialect: Dialect) -> int | None:
        return None if value is None else value.mils_per_minute

    def process_result_value(self, value: int | None, dialect: Dialect) -> Speed | None:
        return None if value is None else Speed(value)


class DurationColumn(TypeDecorator[Duration]):
    """A duration, stored as whole seconds."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Duration | None, dialect: Dialect) -> int | None:
        return None if value is None else value.seconds

    def process_result_value(self, value: int | None, dialect: Dialect) -> Duration | None:
        return None if value is None else Duration(value)


class AngleColumn(TypeDecorator[Angle]):
    """An angle, stored as whole thousandths of a degree."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Angle | None, dialect: Dialect) -> int | None:
        return None if value is None else value.millidegrees

    def process_result_value(self, value: int | None, dialect: Dialect) -> Angle | None:
        return None if value is None else Angle(value)


class BearingColumn(TypeDecorator[Bearing]):
    """A compass bearing, stored the same way and wrapped on the way back."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Bearing | None, dialect: Dialect) -> int | None:
        return None if value is None else value.millidegrees

    def process_result_value(self, value: int | None, dialect: Dialect) -> Bearing | None:
        return None if value is None else Bearing(value)


class TemperatureColumn(TypeDecorator[Temperature]):
    """A reading, stored as whole tenths of a degree Fahrenheit."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Temperature | None, dialect: Dialect) -> int | None:
        return None if value is None else value.decidegrees_f

    def process_result_value(self, value: int | None, dialect: Dialect) -> Temperature | None:
        return None if value is None else Temperature(value)


class TorqueColumn(TypeDecorator[Torque]):
    """A torque, stored as whole inch pounds."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Torque | None, dialect: Dialect) -> int | None:
        return None if value is None else value.inch_pounds

    def process_result_value(self, value: int | None, dialect: Dialect) -> Torque | None:
        return None if value is None else Torque(value)


class TimeOfDayColumn(TypeDecorator[TimeOfDay]):
    """A wall clock time, stored as minutes since midnight."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: TimeOfDay | None, dialect: Dialect) -> int | None:
        return None if value is None else value.minutes

    def process_result_value(self, value: int | None, dialect: Dialect) -> TimeOfDay | None:
        return None if value is None else TimeOfDay(value)


class LiftCodeColumn(TypeDecorator[LiftCode]):
    """Identifiers keep their printed form, which is what people search on."""

    impl = String(8)
    cache_ok = True

    def process_bind_param(self, value: LiftCode | None, dialect: Dialect) -> str | None:
        return None if value is None else value.value

    def process_result_value(self, value: str | None, dialect: Dialect) -> LiftCode | None:
        return None if value is None else LiftCode(value)


class TowerRefColumn(TypeDecorator[TowerRef]):
    impl = String(16)
    cache_ok = True

    def process_bind_param(self, value: TowerRef | None, dialect: Dialect) -> str | None:
        return None if value is None else str(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> TowerRef | None:
        return None if value is None else TowerRef.parse(value)


class RopeSerialColumn(TypeDecorator[RopeSerial]):
    impl = String(20)
    cache_ok = True

    def process_bind_param(self, value: RopeSerial | None, dialect: Dialect) -> str | None:
        return None if value is None else str(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> RopeSerial | None:
        return None if value is None else RopeSerial.parse(value)


class FilingNumberColumn(TypeDecorator[FilingNumber]):
    impl = String(16)
    cache_ok = True

    def process_bind_param(self, value: FilingNumber | None, dialect: Dialect) -> str | None:
        return None if value is None else str(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> FilingNumber | None:
        return None if value is None else FilingNumber.parse(value)


class DecimalColumn(TypeDecorator[Decimal]):
    """A decimal held to three places, stored as an integer of thousandths."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Decimal | None, dialect: Dialect) -> int | None:
        if value is None:
            return None
        return int((value * 1000).to_integral_value())

    def process_result_value(self, value: int | None, dialect: Dialect) -> Decimal | None:
        return None if value is None else Decimal(value) / 1000
