from datetime import datetime

from hill import john_paul, strawberry, wildcat
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.plant.classes import LiftClass
from sheaveline.weather.hold import HoldReason, WeatherHold
from sheaveline.weather.observation import Observation, Precipitation, Station
from sheaveline.weather.policy import POLICY, policy_for

SUMMIT = Station("summit", Length.from_feet(7965))
NOON = datetime(2025, 1, 15, 12, 0)

# John Paul runs at seventy two degrees, so a wind from the south is square across it.
ALONG = "ENE"
ACROSS = "SSE"


def observed(
    wind="W 18",
    degrees=8,
    visibility_feet=30000,
    precipitation=Precipitation.NONE,
    rime=False,
    lightning_feet=None,
):
    return Observation(
        station=SUMMIT,
        taken=NOON,
        wind=Wind.parse(wind),
        temperature=Temperature.from_degrees_f(degrees),
        visibility=Length.from_feet(visibility_feet),
        precipitation=precipitation,
        rime=rime,
        lightning_within=(None if lightning_feet is None else Length.from_feet(lightning_feet)),
    )


def held(lift=None, **kwargs):
    return WeatherHold(lift or john_paul(), observed(**kwargs))


def test_a_quiet_day_holds_nothing():
    clear = held()
    assert clear.may_run()
    assert not clear.is_held()
    assert clear.holds() == ()
    assert str(clear) == "JP clear"


def test_a_gust_over_the_limit_holds_the_machine():
    windy = held(wind=f"{ALONG} 30G50")
    assert windy.is_held()
    assert HoldReason.GUST in windy.reasons()
    assert "45 mph" in windy.holds()[0].detail


def test_each_class_carries_its_own_gust_limit():
    assert held(lift=strawberry()).gust_limit() == Speed.from_miles_per_hour(40)
    assert held(lift=john_paul()).gust_limit() == Speed.from_miles_per_hour(45)
    assert held(lift=wildcat()).gust_limit() == Speed.from_miles_per_hour(50)


def test_a_wind_that_stops_a_triple_will_not_stop_a_tow():
    wind = f"{ALONG} 42"
    assert held(lift=strawberry(), wind=wind).is_held()
    assert held(lift=wildcat(), wind=wind).may_run()


def test_a_wind_along_the_line_has_no_crosswind_in_it():
    along = held(wind=f"{ALONG} 34")
    assert along.crosswind() < Speed.from_miles_per_hour(5)
    assert HoldReason.CROSSWIND not in along.reasons()


def test_the_same_wind_square_across_the_line_holds_it():
    across = held(wind=f"{ACROSS} 34")
    assert across.crosswind() > Speed.from_miles_per_hour(30)
    assert HoldReason.CROSSWIND in across.reasons()


def test_a_surface_tow_is_not_asked_about_crosswind_at_all():
    tow = held(lift=wildcat(), wind=f"{ACROSS} 40")
    assert tow.crosswind_limit() is None
    assert HoldReason.CROSSWIND not in tow.reasons()


def test_the_crosswind_is_read_off_the_gust_not_the_sustained_wind():
    gusting = held(wind=f"{ACROSS} 20G40")
    assert HoldReason.CROSSWIND in gusting.reasons()
    steady = held(wind=f"{ACROSS} 20")
    assert HoldReason.CROSSWIND not in steady.reasons()


def test_cold_alone_does_not_hold_a_lift_but_cold_and_wind_do():
    still = held(wind="W 2", degrees=-20)
    assert HoldReason.CHILL not in still.reasons()
    blowing = held(wind=f"{ALONG} 25", degrees=-10)
    assert HoldReason.CHILL in blowing.reasons()


def test_ice_stops_a_detachable_and_not_a_fixed_grip():
    assert HoldReason.ICING in held(rime=True).reasons()
    assert HoldReason.ICING not in held(lift=strawberry(), rime=True).reasons()


def test_freezing_rain_counts_as_icing_without_any_rime_yet():
    assert HoldReason.ICING in held(precipitation=Precipitation.FREEZING_RAIN).reasons()
    assert HoldReason.ICING not in held(precipitation=Precipitation.SNOW).reasons()


def test_lightning_inside_five_miles_holds_everything():
    for lift in (john_paul(), strawberry(), wildcat()):
        strike = held(lift=lift, lightning_feet=20000)
        assert HoldReason.LIGHTNING in strike.reasons()


def test_lightning_outside_five_miles_does_not():
    assert HoldReason.LIGHTNING not in held(lightning_feet=40000).reasons()


def test_a_lightning_hold_runs_on_a_clock_and_a_wind_hold_does_not():
    assert held(lightning_feet=20000).needs_a_timer()
    assert not held(wind=f"{ALONG} 30G50").needs_a_timer()
    assert HoldReason.GUST.clears_on_reading
    assert not HoldReason.LIGHTNING.clears_on_reading


def test_visibility_at_the_terminal_holds_the_machine():
    assert HoldReason.VISIBILITY in held(visibility_feet=100).reasons()
    assert HoldReason.VISIBILITY not in held(visibility_feet=400).reasons()


def test_several_reasons_can_hold_at_once():
    bad = held(wind=f"{ACROSS} 30G60", degrees=-15, lightning_feet=10000, visibility_feet=80)
    assert len(bad.reasons()) >= 4
    assert bad.is_held()
    assert "held" in str(bad)


def test_a_hold_prints_its_reason_and_its_detail():
    windy = held(wind=f"{ALONG} 30G50")
    assert str(windy.holds()[0]).startswith("gust: ")


def test_the_limits_now_come_off_the_policy_table():
    assert held().policy is policy_for(john_paul().lift_class)
    assert held(lift=wildcat()).policy.crosswind is None
    assert POLICY[john_paul().lift_class].gust == Speed.from_miles_per_hour(45)


def test_every_class_has_a_row_in_the_table():
    for lift_class in LiftClass:
        row = policy_for(lift_class)
        assert row.gust.miles_per_hour > 0
        assert row.chill_floor.degrees_f < 0


def test_only_the_detachables_hold_on_ice_in_the_table():
    for lift_class in LiftClass:
        assert policy_for(lift_class).holds_on_ice == lift_class.is_detachable


def test_a_cabin_tolerates_a_colder_chill_than_an_open_chair():
    cabin = policy_for(LiftClass.DETACHABLE_GONDOLA)
    chair = policy_for(LiftClass.DETACHABLE_CHAIR)
    assert cabin.chill_floor < chair.chill_floor


def test_the_surface_tow_is_the_only_row_with_no_crosswind_figure():
    without = [c for c in LiftClass if not policy_for(c).cares_about_crosswind()]
    assert without == [LiftClass.SURFACE_TOW]
