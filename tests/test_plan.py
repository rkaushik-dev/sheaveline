from datetime import date

import pytest

from hill import john_paul
from sheaveline.evac.plan import STEP_OFF_HEIGHT, EvacuationPlan, Method, PlanStep
from sheaveline.evac.profile import EvacuationError, EvacuationProfile
from sheaveline.evac.team import RescueKit, RescueTeam, TeamPool
from sheaveline.line.catenary import SagModel
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot

ON = date(2025, 1, 15)
SAG = SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(34500))
CREW = ("R Diaz", "T Woo", "J Marsh")


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


def plan(*teams, offset_feet=0, snow_feet=2):
    pool = TeamPool.of(teams or [team()])
    return EvacuationPlan(profile(snow_feet), pool, Length.from_feet(offset_feet), ON)


def test_every_carrier_gets_a_step_when_the_pool_can_do_the_work():
    made = plan()
    assert len(made.steps()) == len(made.stops())
    assert made.is_workable()
    assert made.unassigned() == ()


def test_the_steps_are_numbered_from_one_without_gaps():
    made = plan()
    orders = [step.order for step in made.steps()]
    assert orders == list(range(1, len(orders) + 1))


def test_the_most_exposed_carrier_is_worked_first():
    made = plan()
    heights = [step.stop.height.mils for step in made.steps()]
    assert heights == sorted(heights, reverse=True)


def test_a_carrier_low_enough_to_step_off_needs_nobody():
    low = plan(team(), snow_feet=30)
    step_offs = low.step_offs()
    assert step_offs
    for step in step_offs:
        assert step.team is None
        assert step.stop.height <= STEP_OFF_HEIGHT


def test_a_carrier_inside_the_ladder_reach_is_worked_off_a_ladder():
    made = plan(team(ladder=200, rope=None))
    assert all(step.method is Method.LADDER for step in made.steps() if step.method.needs_a_team)


def test_a_carrier_beyond_the_ladder_needs_the_rope():
    made = plan(team(ladder=12, rope=200))
    technical = made.technical_steps()
    assert technical
    assert all(step.method is Method.ROPE for step in technical)


def test_a_carrier_nobody_can_reach_is_left_out_and_reported():
    short = plan(team(ladder=12, rope=None))
    assert not short.is_workable()
    assert short.unassigned()
    assert len(short.steps()) < len(short.stops())
    assert "unreachable" in str(short)


def test_a_lapsed_pool_can_do_nothing_at_all():
    lapsed = plan(team(until=date(2024, 6, 1)), snow_feet=2)
    assert not lapsed.is_workable()
    assert len(lapsed.unassigned()) > 0


def test_the_nearest_team_takes_the_job_when_two_can_do_it():
    made = plan(team(name="base", staged=0), team(name="summit", staged=4800))
    grouped = made.by_team()
    assert set(grouped) == {"base", "summit"}
    for name, steps in grouped.items():
        for step in steps:
            assert step.team is not None
            assert step.team.name == name


def test_the_work_is_split_by_where_the_carriers_are():
    made = plan(team(name="base", staged=0), team(name="summit", staged=4800))
    grouped = made.by_team()
    base_stations = [step.stop.station.feet for step in grouped["base"]]
    summit_stations = [step.stop.station.feet for step in grouped["summit"]]
    assert sum(base_stations) / len(base_stations) < sum(summit_stations) / len(summit_stations)


def test_a_step_needs_a_team_when_the_method_does():
    made = plan()
    stop = made.ordered_stops()[0]
    with pytest.raises(EvacuationError):
        PlanStep(1, stop, Method.ROPE, None)
    with pytest.raises(EvacuationError):
        PlanStep(1, stop, Method.STEP_OFF, team())
    with pytest.raises(EvacuationError):
        PlanStep(0, stop, Method.ROPE, team())


def test_the_methods_know_what_they_need():
    assert Method.ROPE.needs_a_team
    assert Method.LADDER.needs_a_team
    assert not Method.STEP_OFF.needs_a_team
    assert Method.ROPE.is_technical
    assert not Method.LADDER.is_technical


def test_a_step_prints_a_line_somebody_can_read_in_a_storm():
    made = plan()
    printed = str(made.steps()[0])
    assert printed.startswith("1. carrier ")
    assert "ft," in printed


def test_the_plan_counts_the_people_on_the_line():
    made = plan()
    assert made.people_on_the_line(4) == len(made.stops()) * 4
    assert made.people_on_the_line(0) == 0
    with pytest.raises(EvacuationError):
        made.people_on_the_line(-1)


def test_a_plan_prints_its_state():
    assert str(plan()).endswith("workable")
