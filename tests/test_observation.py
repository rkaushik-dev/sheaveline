from datetime import datetime

import pytest

from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.weather.observation import (
    Observation,
    ObservationLog,
    Precipitation,
    Station,
    WeatherError,
)

SUMMIT = Station("summit", Length.from_feet(7965))
MID = Station("mid mountain", Length.from_feet(7100))
BASE = Station("base", Length.from_feet(6400))

NOON = datetime(2025, 1, 15, 12, 0)


def observed(
    station=SUMMIT,
    minutes=0,
    wind="W 18",
    degrees=8,
    visibility_feet=None,
    precipitation=Precipitation.NONE,
    rime=False,
    lightning_feet=None,
):
    taken = datetime(2025, 1, 15, 12, minutes % 60) if minutes < 60 else NOON
    return Observation(
        station=station,
        taken=taken,
        wind=Wind.parse(wind),
        temperature=Temperature.from_degrees_f(degrees),
        visibility=(
            Length.from_feet(visibility_feet)
            if visibility_feet is not None
            else Length.from_feet(30000)
        ),
        precipitation=precipitation,
        rime=rime,
        lightning_within=(None if lightning_feet is None else Length.from_feet(lightning_feet)),
    )


def test_a_station_has_a_name_and_an_elevation():
    assert SUMMIT.elevation == Length.from_feet(7965)
    assert str(SUMMIT) == "summit"
    with pytest.raises(WeatherError):
        Station("  ", Length.from_feet(7000))


def test_precipitation_says_what_it_does_to_the_machine():
    assert Precipitation.RAIN.wets_the_rope
    assert Precipitation.FREEZING_RAIN.wets_the_rope
    assert not Precipitation.SNOW.wets_the_rope
    assert Precipitation.FREEZING_RAIN.builds_ice
    assert not Precipitation.RAIN.builds_ice


def test_an_observation_carries_the_chill_it_implies():
    cold = observed(wind="W 20", degrees=5)
    assert cold.chill().degrees_f < cold.temperature.degrees_f


def test_an_observation_checks_its_own_numbers():
    with pytest.raises(WeatherError):
        observed(visibility_feet=-1)
    with pytest.raises(WeatherError):
        observed(lightning_feet=-100)


def test_icing_is_rime_on_the_towers_or_freezing_rain_on_the_way():
    assert observed(rime=True).icing()
    assert observed(precipitation=Precipitation.FREEZING_RAIN).icing()
    assert not observed(precipitation=Precipitation.SNOW).icing()


def test_lightning_is_recorded_by_distance_or_not_at_all():
    assert observed(lightning_feet=26400).has_lightning()
    assert not observed().has_lightning()


def test_an_observation_ages():
    seen = observed(minutes=0)
    assert seen.age_at(datetime(2025, 1, 15, 12, 10)) == Duration.from_minutes(10)
    assert not seen.is_stale(datetime(2025, 1, 15, 12, 10))
    assert seen.is_stale(datetime(2025, 1, 15, 12, 40))


def test_a_moment_before_the_reading_is_refused():
    with pytest.raises(WeatherError):
        observed(minutes=30).age_at(datetime(2025, 1, 15, 12, 0))


def test_the_staleness_window_can_be_tightened():
    seen = observed(minutes=0)
    now = datetime(2025, 1, 15, 12, 10)
    assert not seen.is_stale(now)
    assert seen.is_stale(now, Duration.from_minutes(5))


def test_an_observation_prints_short():
    assert str(observed(wind="W 18", degrees=8)) == "summit W 18, 8 F"


def test_a_log_needs_readings():
    with pytest.raises(WeatherError):
        ObservationLog.of([])


def test_a_log_sorts_by_time_and_lists_its_stations():
    log = ObservationLog.of(
        [observed(BASE, minutes=10), observed(SUMMIT, minutes=5), observed(MID, minutes=1)]
    )
    assert len(log) == 3
    assert [seen.taken.minute for seen in log.observations] == [1, 5, 10]
    assert set(log.stations()) == {SUMMIT, MID, BASE}


def test_readings_can_be_pulled_out_by_station():
    log = ObservationLog.of([observed(SUMMIT, minutes=1), observed(BASE, minutes=2)])
    assert len(log.from_station(SUMMIT)) == 1
    assert log.latest(SUMMIT).station is SUMMIT
    assert log.latest().station is BASE


def test_asking_a_station_that_has_reported_nothing():
    log = ObservationLog.of([observed(SUMMIT, minutes=1)])
    with pytest.raises(WeatherError):
        log.latest(BASE)


def test_the_latest_fresh_reading_skips_a_stale_one():
    log = ObservationLog.of(
        [observed(SUMMIT, minutes=5, wind="W 40"), observed(SUMMIT, minutes=50, wind="W 10")]
    )
    now = datetime(2025, 1, 15, 12, 55)
    assert log.latest_fresh(now, SUMMIT).wind.speed.miles_per_hour == 10


def test_a_log_of_nothing_fresh_is_an_error_rather_than_a_guess():
    log = ObservationLog.of([observed(SUMMIT, minutes=5)])
    with pytest.raises(WeatherError):
        log.latest_fresh(datetime(2025, 1, 15, 14, 0), SUMMIT)


def test_the_highest_station_is_found():
    log = ObservationLog.of([observed(BASE, minutes=1), observed(SUMMIT, minutes=2)])
    assert log.highest_station() is SUMMIT


def test_the_station_nearest_an_elevation_is_the_one_the_rules_read():
    log = ObservationLog.of(
        [observed(BASE, minutes=1), observed(MID, minutes=2), observed(SUMMIT, minutes=3)]
    )
    assert log.nearest_to(Length.from_feet(7900)) is SUMMIT
    assert log.nearest_to(Length.from_feet(7000)) is MID
    assert log.nearest_to(Length.from_feet(6420)) is BASE


def test_the_worst_gust_and_the_coldest_reading_are_found():
    log = ObservationLog.of(
        [
            observed(SUMMIT, minutes=1, wind="W 20G30", degrees=10),
            observed(SUMMIT, minutes=2, wind="W 25", degrees=-4),
        ]
    )
    assert log.worst_gust().wind.peak.miles_per_hour == 30
    assert log.coldest().temperature.degrees_f == -4


def test_adding_a_reading_makes_a_new_log():
    log = ObservationLog.of([observed(SUMMIT, minutes=1)])
    longer = log.with_observation(observed(SUMMIT, minutes=2))
    assert len(log) == 1
    assert len(longer) == 2
