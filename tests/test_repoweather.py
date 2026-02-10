from datetime import datetime

import pytest
from sqlalchemy import inspect
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.persistence.base import StorageError, build_engine, create_schema
from sheaveline.persistence.models.weather import ObservationRow, StationRow
from sheaveline.persistence.repositories import WeatherRepository
from sheaveline.weather.observation import Observation, Precipitation, Station

SUMMIT = Station("summit", Length.from_feet(7981))
MID = Station("mid mountain", Length.from_feet(7100))
BASE = Station("base", Length.from_feet(6400))
NOON = datetime(2025, 1, 15, 12, 0)


@pytest.fixture
def session():
    engine = build_engine()
    create_schema(engine)
    with Session(engine) as opened:
        yield opened
    engine.dispose()


def observed(station=SUMMIT, minute=0, wind="W 18", degrees=8, lightning=None, rime=False):
    return Observation(
        station=station,
        taken=datetime(2025, 1, 15, 12, minute),
        wind=Wind.parse(wind),
        temperature=Temperature.from_degrees_f(degrees),
        visibility=Length.from_feet(30000),
        precipitation=Precipitation.SNOW,
        rime=rime,
        lightning_within=None if lightning is None else Length.from_feet(lightning),
    )


def stocked(session):
    weather = WeatherRepository(session)
    for station in (SUMMIT, MID, BASE):
        weather.add_station(station)
    session.commit()
    return weather


def test_the_schema_carries_the_weather_tables():
    engine = build_engine()
    create_schema(engine)
    assert {"weather_stations", "observations"} <= set(inspect(engine).get_table_names())
    engine.dispose()


def test_a_station_goes_in_once(session):
    weather = stocked(session)
    with pytest.raises(StorageError):
        weather.add_station(SUMMIT)


def test_the_stations_come_back_in_name_order(session):
    weather = stocked(session)
    assert [one.name for one in weather.stations()] == ["base", "mid mountain", "summit"]
    assert weather.station("summit").elevation == Length.from_feet(7981)


def test_a_station_that_is_not_there(session):
    with pytest.raises(StorageError):
        WeatherRepository(session).station("cirque")


def test_the_nearest_station_to_an_elevation_is_the_one_the_rules_read(session):
    weather = stocked(session)
    assert weather.nearest_to(Length.from_feet(7900)).name == "summit"
    assert weather.nearest_to(Length.from_feet(7050)).name == "mid mountain"
    assert weather.nearest_to(Length.from_feet(6440)).name == "base"


def test_asking_the_nearest_station_before_any_exist(session):
    with pytest.raises(StorageError):
        WeatherRepository(session).nearest_to(Length.from_feet(7000))


def test_a_reading_against_a_station_that_is_not_there_is_refused(session):
    weather = WeatherRepository(session)
    with pytest.raises(StorageError):
        weather.record(observed())


def test_a_reading_round_trips_with_every_field(session):
    weather = stocked(session)
    weather.record(observed(wind="SW 24G38", degrees=-12, lightning=20000, rime=True))
    session.commit()
    found = weather.latest(SUMMIT)
    assert found.wind.speed == Speed.from_miles_per_hour(24)
    assert found.wind.gust == Speed.from_miles_per_hour(38)
    assert found.temperature == Temperature.from_degrees_f(-12)
    assert found.lightning_within == Length.from_feet(20000)
    assert found.rime is True
    assert found.precipitation is Precipitation.SNOW
    assert found.station.elevation == SUMMIT.elevation


def test_a_reading_with_no_gust_comes_back_with_none(session):
    weather = stocked(session)
    weather.record(observed(wind="W 18"))
    session.commit()
    assert weather.latest(SUMMIT).wind.gust is None


def test_two_readings_cannot_share_a_moment_at_one_station(session):
    weather = stocked(session)
    weather.record(observed())
    weather.record(observed())
    with pytest.raises(IntegrityError):
        session.commit()


def test_two_stations_may_report_the_same_moment(session):
    weather = stocked(session)
    weather.record(observed(station=SUMMIT))
    weather.record(observed(station=BASE))
    session.commit()
    assert len(weather.since(NOON)) == 2


def test_a_gust_below_the_sustained_wind_is_refused_by_the_database(session):
    stocked(session)
    session.add(
        ObservationRow(
            station_name="summit",
            taken=NOON,
            wind_speed=Speed.from_miles_per_hour(30),
            wind_bearing=Wind.parse("W 30").bearing,
            wind_gust=Speed.from_miles_per_hour(20),
            temperature=Temperature.from_degrees_f(8),
            visibility=Length.from_feet(30000),
            precipitation=Precipitation.NONE,
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_the_latest_reading_is_the_most_recent_one(session):
    weather = stocked(session)
    weather.record(observed(minute=5, wind="W 40"))
    weather.record(observed(minute=25, wind="W 10"))
    session.commit()
    assert weather.latest(SUMMIT).wind.speed == Speed.from_miles_per_hour(10)


def test_a_station_that_has_reported_nothing_has_no_latest(session):
    weather = stocked(session)
    with pytest.raises(StorageError):
        weather.latest(SUMMIT)


def test_a_stale_reading_is_refused_rather_than_believed(session):
    weather = stocked(session)
    weather.record(observed(minute=5))
    session.commit()
    fresh = weather.latest_fresh(SUMMIT, datetime(2025, 1, 15, 12, 20))
    assert fresh.wind.speed == Speed.from_miles_per_hour(18)
    with pytest.raises(StorageError):
        weather.latest_fresh(SUMMIT, datetime(2025, 1, 15, 13, 0))


def test_the_staleness_window_can_be_tightened(session):
    weather = stocked(session)
    weather.record(observed(minute=0))
    session.commit()
    now = datetime(2025, 1, 15, 12, 10)
    assert weather.latest_fresh(SUMMIT, now) is not None
    with pytest.raises(StorageError):
        weather.latest_fresh(SUMMIT, now, Duration.from_minutes(5))


def test_the_readings_since_a_moment_come_back_in_order(session):
    weather = stocked(session)
    for minute in (30, 5, 20):
        weather.record(observed(minute=minute))
    session.commit()
    found = weather.since(datetime(2025, 1, 15, 12, 10))
    assert [one.taken.minute for one in found] == [20, 30]


def test_a_log_can_be_built_for_one_station_or_for_all_of_them(session):
    weather = stocked(session)
    weather.record(observed(station=SUMMIT, minute=5))
    weather.record(observed(station=BASE, minute=10))
    session.commit()
    assert len(weather.log()) == 2
    assert len(weather.log(SUMMIT)) == 1
    assert weather.log().highest_station().name == "summit"


def test_a_log_with_nothing_in_it_is_an_error_rather_than_an_empty_one(session):
    stocked(session)
    with pytest.raises(StorageError):
        WeatherRepository(session).log()


def test_deleting_a_station_takes_its_readings_with_it(session):
    weather = stocked(session)
    weather.record(observed())
    session.commit()
    row = session.get(StationRow, "summit")
    assert row is not None
    session.delete(row)
    session.commit()
    assert weather.since(NOON) == []
