from datetime import datetime

import pytest

from hill import JOHN_PAUL, john_paul
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.temperature import Temperature
from sheaveline.measures.wind import Wind
from sheaveline.weather.hold import HoldReason, WeatherHold
from sheaveline.weather.holdlog import HoldRecord, HoldRegister
from sheaveline.weather.observation import Observation, Station, WeatherError

SUMMIT = Station("summit", Length.from_feet(7965))
ACROSS = "SSE"


def at(hour, minute=0):
    return datetime(2025, 1, 15, hour, minute)


def observed(wind="W 12", taken=None, lightning_feet=None):
    return Observation(
        station=SUMMIT,
        taken=taken or at(12),
        wind=Wind.parse(wind),
        temperature=Temperature.from_degrees_f(14),
        lightning_within=(None if lightning_feet is None else Length.from_feet(lightning_feet)),
    )


def decision(**kwargs):
    return WeatherHold(john_paul(), observed(**kwargs))


def record(reason=HoldReason.GUST, started=None, ended=None, by="K Ostler"):
    return HoldRecord(
        lift=JOHN_PAUL,
        reason=reason,
        started=started or at(10),
        started_by=by,
        ended=ended,
        ended_by=None if ended is None else "K Ostler",
    )


def test_a_record_is_signed_and_ordered():
    with pytest.raises(WeatherError):
        record(by="  ")
    with pytest.raises(WeatherError):
        record(started=at(12), ended=at(10))


def test_a_record_is_either_closed_by_somebody_or_still_open():
    with pytest.raises(WeatherError):
        HoldRecord(JOHN_PAUL, HoldReason.GUST, at(10), "K Ostler", ended=at(11))
    with pytest.raises(WeatherError):
        HoldRecord(JOHN_PAUL, HoldReason.GUST, at(10), "K Ostler", ended_by="K Ostler")


def test_an_open_hold_is_measured_against_a_moment():
    open_hold = record()
    assert open_hold.is_open()
    assert open_hold.length(at(11)) == Duration.from_hours(1)
    with pytest.raises(WeatherError):
        open_hold.length()
    with pytest.raises(WeatherError):
        open_hold.length(at(9))


def test_a_closed_hold_knows_its_own_length():
    closed = record(ended=at(11, 30))
    assert not closed.is_open()
    assert closed.length() == Duration.from_minutes(90)


def test_closing_a_hold_makes_a_new_record():
    open_hold = record()
    closed = open_hold.closed(at(11), "D Ruiz")
    assert open_hold.is_open()
    assert closed.ended_by == "D Ruiz"
    with pytest.raises(WeatherError):
        closed.closed(at(12), "D Ruiz")


def test_a_record_prints_its_state():
    assert str(record()) == "JP gust from 10:00, open"
    assert str(record(ended=at(11, 30))) == "JP gust from 10:00, closed 11:30"


def test_calling_a_hold_opens_one_record_a_reason():
    register = HoldRegister.of().call(decision(wind=f"{ACROSS} 30G60"), at(11), "K Ostler")
    assert len(register) >= 2
    assert HoldReason.GUST in register.open_reasons(JOHN_PAUL)
    assert HoldReason.CROSSWIND in register.open_reasons(JOHN_PAUL)
    assert register.is_held(JOHN_PAUL)


def test_calling_the_same_hold_twice_does_not_duplicate_it():
    windy = decision(wind=f"{ACROSS} 30G60")
    once = HoldRegister.of().call(windy, at(11), "K Ostler")
    twice = once.call(windy, at(11, 15), "K Ostler")
    assert len(twice) == len(once)


def test_a_clear_reading_opens_nothing():
    register = HoldRegister.of().call(decision(), at(11), "K Ostler")
    assert len(register) == 0
    assert not register.is_held(JOHN_PAUL)


def test_a_wind_hold_clears_as_soon_as_the_reading_is_inside_the_limit():
    register = HoldRegister.of().call(decision(wind=f"{ACROSS} 30G60"), at(11), "K Ostler")
    gust = next(r for r in register.open_holds(JOHN_PAUL) if r.reason is HoldReason.GUST)
    calm = decision(wind="W 10")
    assert register.may_clear(gust, at(11, 20), observed(wind="W 10"), calm)


def test_a_wind_hold_does_not_clear_while_the_wind_is_still_over():
    register = HoldRegister.of().call(decision(wind=f"{ACROSS} 30G60"), at(11), "K Ostler")
    gust = next(r for r in register.open_holds(JOHN_PAUL) if r.reason is HoldReason.GUST)
    still_windy = decision(wind=f"{ACROSS} 30G60")
    assert not register.may_clear(gust, at(11, 20), observed(wind=f"{ACROSS} 30G60"), still_windy)


def test_a_lightning_hold_waits_thirty_minutes_whatever_the_wind_is_doing():
    strike = decision(lightning_feet=10000)
    register = HoldRegister.of().call(strike, at(11), "K Ostler")
    bolt = next(r for r in register.open_holds(JOHN_PAUL) if r.reason is HoldReason.LIGHTNING)
    quiet = observed(taken=at(11, 5))
    calm = decision()
    assert not register.may_clear(bolt, at(11, 20), quiet, calm)
    assert register.may_clear(bolt, at(11, 40), quiet, calm)


def test_a_fresh_strike_restarts_the_lightning_clock():
    strike = decision(lightning_feet=10000)
    register = HoldRegister.of().call(strike, at(11), "K Ostler")
    bolt = next(r for r in register.open_holds(JOHN_PAUL) if r.reason is HoldReason.LIGHTNING)
    another = observed(taken=at(11, 35), lightning_feet=8000)
    assert not register.may_clear(bolt, at(12, 30), another, decision())


def test_a_closed_hold_cannot_be_cleared_again():
    register = HoldRegister.of([record(ended=at(11))])
    closed = register.records[0]
    assert not register.may_clear(closed, at(12), observed(), decision())


def test_clearing_a_hold_closes_it_in_the_register():
    register = HoldRegister.of().call(decision(wind=f"{ACROSS} 30G60"), at(11), "K Ostler")
    gust = next(r for r in register.open_holds(JOHN_PAUL) if r.reason is HoldReason.GUST)
    after = register.clear(gust, at(11, 40), "D Ruiz")
    assert HoldReason.GUST not in after.open_reasons(JOHN_PAUL)
    assert len(after) == len(register)


def test_clearing_a_hold_from_another_register_is_refused():
    with pytest.raises(WeatherError):
        HoldRegister.of().clear(record(), at(12), "D Ruiz")


def test_the_register_totals_and_sorts():
    register = HoldRegister.of(
        [
            record(HoldReason.GUST, at(9), at(9, 30)),
            record(HoldReason.LIGHTNING, at(10), at(11)),
            record(HoldReason.CHILL, at(12)),
        ]
    )
    assert len(register.by_reason(HoldReason.LIGHTNING)) == 1
    assert register.longest().reason is HoldReason.LIGHTNING
    assert register.total_held(JOHN_PAUL) == Duration.from_minutes(90)


def test_a_register_with_nothing_closed_has_no_longest():
    with pytest.raises(WeatherError):
        HoldRegister.of([record()]).longest()
