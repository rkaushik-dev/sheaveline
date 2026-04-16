from datetime import datetime

import pytest

from hill import john_paul, wildcat
from sheaveline.measures.length import Length
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.weather.forecast import Confidence, Forecast, ForecastPeriod
from sheaveline.weather.hold import HoldReason
from sheaveline.weather.observation import Station, WeatherError

SUMMIT = Station("summit", Length.from_feet(7981))
ISSUED = datetime(2025, 1, 15, 5, 0)

# John Paul runs at seventy two degrees, so a wind from the south is across it.
ALONG = "ENE"
ACROSS = "SSE"


def period(start_hour=9, end_hour=12, wind=f"{ALONG} 18", degrees=8, **rest):
    return ForecastPeriod(
        starts=datetime(2025, 1, 15, start_hour),
        ends=datetime(2025, 1, 15, end_hour),
        wind=Wind.parse(wind),
        temperature=Temperature.from_degrees_f(degrees),
        **rest,
    )


def forecast(*periods, by="mountain forecaster"):
    return Forecast.of(SUMMIT, ISSUED, by, periods or [period()])


def test_a_period_runs_forwards():
    with pytest.raises(WeatherError):
        ForecastPeriod(
            starts=datetime(2025, 1, 15, 12),
            ends=datetime(2025, 1, 15, 9),
            wind=Wind.parse("W 18"),
            temperature=Temperature.from_degrees_f(8),
        )


def test_a_period_covers_its_own_stretch():
    one = period(9, 12)
    assert one.covers(datetime(2025, 1, 15, 9))
    assert one.covers(datetime(2025, 1, 15, 11, 59))
    assert not one.covers(datetime(2025, 1, 15, 12))


def test_a_period_prints_short():
    assert str(period(9, 12, wind="W 18")) == "09:00 to 12:00, W 18"


def test_a_forecast_is_signed_and_has_periods():
    with pytest.raises(WeatherError):
        Forecast.of(SUMMIT, ISSUED, "  ", [period()])
    with pytest.raises(WeatherError):
        Forecast.of(SUMMIT, ISSUED, "forecaster", [])


def test_overlapping_periods_are_refused():
    with pytest.raises(WeatherError):
        forecast(period(9, 13), period(12, 15))


def test_the_periods_come_back_in_order_whichever_way_they_are_given():
    held = forecast(period(15, 18), period(9, 12), period(12, 15))
    assert [one.starts.hour for one in held.periods] == [9, 12, 15]
    assert len(held) == 3


def test_the_period_covering_a_moment_is_found():
    held = forecast(period(9, 12), period(12, 15))
    found = held.at(datetime(2025, 1, 15, 13))
    assert found is not None
    assert found.starts.hour == 12
    assert held.at(datetime(2025, 1, 15, 20)) is None


def test_the_windiest_and_coldest_periods_are_found():
    held = forecast(
        period(9, 12, wind=f"{ALONG} 18", degrees=20),
        period(12, 15, wind=f"{ALONG} 30G55", degrees=-8),
    )
    assert held.windiest().starts.hour == 12
    assert held.coldest().starts.hour == 12


def test_a_quiet_forecast_holds_nothing():
    lift = john_paul()
    held = forecast(period(9, 12))
    assert held.expected_holds(lift) == ()
    assert held.first_hold(lift) is None
    assert len(held.clear_periods(lift)) == 1


def test_a_windy_period_is_expected_to_hold_the_machine():
    lift = john_paul()
    held = forecast(period(9, 12), period(12, 15, wind=f"{ALONG} 30G60"))
    expected = held.expected_holds(lift)
    assert [one.starts.hour for one in expected] == [12]
    first = held.first_hold(lift)
    assert first is not None
    assert first.starts.hour == 12


def test_the_forecast_is_judged_by_the_same_rules_as_a_reading():
    lift = john_paul()
    held = forecast(period(9, 12, wind=f"{ACROSS} 20G50"))
    reasons = {one.reason for one in held.holds_in(held.periods[0], lift)}
    assert HoldReason.GUST in reasons
    assert HoldReason.CROSSWIND in reasons


def test_a_wind_that_holds_a_quad_may_not_hold_a_tow():
    windy = period(9, 12, wind=f"{ACROSS} 30G48")
    held = forecast(windy)
    assert held.expected_holds(john_paul())
    assert not held.expected_holds(wildcat())


def test_the_time_the_machine_is_next_expected_to_run():
    lift = john_paul()
    held = forecast(
        period(9, 12, wind=f"{ALONG} 30G60"),
        period(12, 15, wind=f"{ALONG} 30G60"),
        period(15, 18, wind=f"{ALONG} 12"),
    )
    clear = held.clear_after(lift, datetime(2025, 1, 15, 10))
    assert clear == datetime(2025, 1, 15, 15)


def test_a_machine_already_clear_is_clear_from_now():
    lift = john_paul()
    held = forecast(period(9, 12), period(12, 15, wind=f"{ALONG} 30G60"))
    assert held.clear_after(lift, datetime(2025, 1, 15, 10)) == datetime(2025, 1, 15, 10)


def test_a_forecast_that_never_clears_says_nothing_rather_than_guessing():
    lift = john_paul()
    held = forecast(period(9, 12, wind=f"{ALONG} 30G60"))
    assert held.clear_after(lift, datetime(2025, 1, 15, 10)) is None


def test_a_low_confidence_period_is_not_worth_planning_against():
    held = forecast(
        period(9, 12, confidence=Confidence.HIGH),
        period(12, 15, confidence=Confidence.LOW),
    )
    assert [one.starts.hour for one in held.worth_acting_on()] == [9]
    assert Confidence.MODERATE.worth_planning_against
    assert not Confidence.LOW.worth_planning_against


def test_a_forecast_says_what_it_covers():
    held = forecast(period(9, 12), period(12, 15))
    starts, ends = held.covers()
    assert starts.hour == 9
    assert ends.hour == 15


def test_a_forecast_ages():
    held = forecast()
    assert held.age_at(datetime(2025, 1, 15, 11)) == 6
    with pytest.raises(WeatherError):
        held.age_at(datetime(2025, 1, 15, 4))


def test_a_period_turns_into_the_shape_the_anemometer_produces():
    one = period(9, 12, wind="W 24G38", degrees=-4)
    seen = one.as_observation(SUMMIT)
    assert seen.station is SUMMIT
    assert seen.taken == one.starts
    assert seen.wind.peak == one.wind.peak
    assert seen.temperature == one.temperature


def test_icing_in_a_forecast_reaches_the_hold_engine():
    lift = john_paul()
    held = forecast(period(9, 12, rime=True))
    reasons = {one.reason for one in held.holds_in(held.periods[0], lift)}
    assert HoldReason.ICING in reasons
