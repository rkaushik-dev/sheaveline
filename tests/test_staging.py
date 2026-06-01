from datetime import date

import pytest

from hill import john_paul
from sheaveline.evac.plan import EvacuationPlan, Method
from sheaveline.evac.profile import EvacuationError, EvacuationProfile
from sheaveline.evac.staging import Beat, restage, stage, staging_point
from sheaveline.evac.team import RescueKit, RescueTeam, TeamPool
from sheaveline.evac.timing import EvacuationTiming
from sheaveline.line.catenary import SagModel
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot

ON = date(2025, 1, 15)
SAG = SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(34500))
CREW = ("R Diaz", "T Woo", "J Marsh")
NOWHERE = Length.from_feet(0)


def profile(snow_feet=2):
    return EvacuationProfile(john_paul(), SAG, Length.from_feet(snow_feet))


def team(name="alpha", ladder=24, rope=200, staged=0, until=date(2025, 12, 1)):
    return RescueTeam.of(
        name,
        CREW,
        RescueKit(
            ladder=None if ladder is None else Length.from_feet(ladder),
            rope=None if rope is None else Length.from_feet(rope),
            seats=2,
            harnesses=3,
            radios=1,
        ),
        Length.from_feet(staged),
        until,
    )


def staged(*teams, offset_feet=0, seats=3):
    pool = TeamPool.of(teams or [team()])
    return stage(profile(), pool, Length.from_feet(offset_feet), ON, seats_filled=seats)


def test_one_team_walks_the_whole_line():
    plan = staged(team())
    assert len(plan) == 1
    assert len(plan.stops()) == 84
    assert plan.is_workable()


def test_the_middle_of_a_run_is_between_its_ends():
    stops = list(profile().stops_at_offset(NOWHERE))[:5]
    middle = staging_point(stops)
    stations = [one.station for one in stops]
    assert min(stations) <= middle <= max(stations)


def test_an_empty_run_has_no_middle():
    with pytest.raises(EvacuationError):
        staging_point([])


def test_a_beat_with_nothing_in_it_is_refused():
    with pytest.raises(EvacuationError):
        Beat(team(), (), NOWHERE, 3)


def test_a_beat_with_nobody_on_the_line_is_refused():
    stops = tuple(profile().stops_at_offset(NOWHERE))[:2]
    with pytest.raises(EvacuationError):
        Beat(team(), stops, NOWHERE, 0)


def test_a_second_team_cuts_the_worst_share_down():
    alone = staged(team("alpha"))
    pair = staged(team("alpha"), team("bravo"))
    assert len(pair) == 2
    assert pair.busiest().work() < alone.busiest().work()
    assert pair.clears_in() < alone.clears_in()


def test_more_teams_keep_cutting_it_down():
    times = [
        staged(*[team(f"team{one}") for one in range(count)]).clears_in() for count in (1, 2, 4)
    ]
    assert times[0] > times[1] > times[2]


def test_every_carrier_lands_in_exactly_one_beat():
    plan = staged(team("alpha"), team("bravo"), team("charlie"))
    numbers = [stop.number for beat in plan.beats for stop in beat.stops]
    assert sorted(numbers) == sorted({one.number for one in plan.stops()})
    assert len(numbers) == 84


def test_the_beats_are_contiguous_along_the_line():
    plan = staged(team("alpha"), team("bravo"), team("charlie"))
    ends = [max(one.station.mils for one in beat.stops) for beat in plan.beats]
    starts = [min(one.station.mils for one in beat.stops) for beat in plan.beats]
    for earlier, later in zip(ends, starts[1:], strict=False):
        assert earlier <= later


def test_the_cut_is_tighter_than_handing_the_line_out_by_nearest_team():
    teams = [team("alpha", staged=0), team("bravo", staged=4800)]
    pool = TeamPool.of(teams)
    nearest = EvacuationTiming(EvacuationPlan(profile(), pool, NOWHERE, ON), seats_filled=3)
    cut = stage(profile(), pool, NOWHERE, ON, seats_filled=3)
    assert cut.clears_in() < nearest.total_time()


def test_the_spread_between_busiest_and_quietest_is_small():
    plan = staged(team("alpha"), team("bravo"), team("charlie"), team("delta"))
    assert plan.spread() < plan.busiest().work()


def test_a_team_whose_training_has_lapsed_is_not_staged():
    plan = staged(team("alpha"), team("stale", until=date(2024, 12, 1)))
    assert [one.name for one in plan.teams()] == ["alpha"]


def test_a_hill_with_nobody_fit_to_work_refuses():
    with pytest.raises(EvacuationError):
        staged(team("stale", until=date(2024, 12, 1)))


def test_a_carrier_nobody_can_reach_is_reported_rather_than_hidden():
    plan = staged(team("short", ladder=8, rope=None))
    assert plan.stranded
    assert not plan.is_workable()
    assert any("nobody reaches it" in one for one in plan.refusals())


def test_the_smallest_kit_that_covers_a_run_takes_it():
    plan = staged(team("ladders", ladder=24, rope=None), team("ropes", ladder=24, rope=200))
    for beat in plan.beats:
        if beat.team.name == "ladders":
            assert all(beat.method_for(one) is not Method.ROPE for one in beat.stops)


def test_a_beat_prints_where_it_is_and_how_long_it_takes():
    plan = staged(team("alpha"))
    printed = str(plan.busiest())
    assert "alpha at" in printed
    assert "84 carriers" in printed


def test_the_plan_prints_what_the_manager_asks():
    plan = staged(team("alpha"), team("bravo"))
    assert str(plan).startswith("2 teams, 84 carriers, clear in")


def test_the_steps_are_numbered_from_one_without_a_gap():
    plan = staged(team("alpha"), team("bravo"))
    assert [one.order for one in plan.steps()] == list(range(1, 85))


def test_a_fuller_chair_takes_longer_on_the_same_cut():
    quiet = staged(team("alpha"), team("bravo"), seats=2)
    busy = restage(quiet, 4)
    assert busy.clears_in() > quiet.clears_in()
    assert [len(one.stops) for one in busy.beats] == [len(one.stops) for one in quiet.beats]


def test_working_serially_takes_longer_than_working_at_once():
    plan = staged(team("alpha"), team("bravo"), team("charlie"))
    assert plan.serial_time() > plan.clears_in()


def test_the_walking_is_less_when_the_teams_are_staged_on_their_own_runs():
    parked = staged(team("alpha", staged=0), team("bravo", staged=0))
    assert parked.walking() < parked.profile.slope_length().scaled(84)
