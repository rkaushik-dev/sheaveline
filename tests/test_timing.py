from datetime import date

import pytest

from hill import john_paul, the_site
from sheaveline.evac.plan import EvacuationPlan, Method
from sheaveline.evac.profile import EvacuationError, EvacuationProfile
from sheaveline.evac.team import RescueKit, RescueTeam, TeamPool
from sheaveline.evac.timing import EvacuationTiming, approach_distance
from sheaveline.line.catenary import SagModel
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.load import Load, LoadPerFoot
from sheaveline.measures.speed import Speed
from sheaveline.schedule.hours import TimeOfDay

ON = date(2025, 1, 15)
SAG = SagModel(LoadPerFoot.from_pounds_per_foot("6.20"), Load.from_pounds(34500))
CREW = ("R Diaz", "T Woo", "J Marsh")


def team(name="alpha", ladder=24, rope=200, staged=0):
    return RescueTeam.of(
        name,
        CREW,
        RescueKit(
            ladder=Length.from_feet(ladder),
            rope=None if rope is None else Length.from_feet(rope),
            seats=2,
            harnesses=3,
            radios=1,
        ),
        Length.from_feet(staged),
        date(2025, 12, 1),
    )


def full_pool():
    """What the department actually puts on the hill, staged along the line."""
    return [team(name=f"team{index}", staged=index * 600) for index in range(8)]


def timing(*teams, seats=3, snow_feet=2):
    profile = EvacuationProfile(john_paul(), SAG, Length.from_feet(snow_feet))
    pool = TeamPool.of(teams or full_pool())
    plan = EvacuationPlan(profile, pool, Length.from_feet(0), ON)
    return EvacuationTiming(plan, seats_filled=seats)


def test_a_timing_needs_people_and_teams_that_can_walk():
    with pytest.raises(EvacuationError):
        timing().with_load(0)
    with pytest.raises(EvacuationError):
        EvacuationTiming(timing().plan, 3, Duration.from_minutes(15), Speed(0))


def test_every_step_is_costed_in_three_parts():
    costed = timing().timings()
    assert costed
    for one in costed:
        assert one.total == one.approach + one.setup + one.lowering


def test_a_rope_job_takes_longer_than_a_ladder_job():
    ladder = timing(*[team(name=f"t{i}", ladder=200, rope=None, staged=i * 600) for i in range(8)])
    rope = timing(*[team(name=f"t{i}", ladder=12, rope=200, staged=i * 600) for i in range(8)])
    assert rope.total_time() > ladder.total_time()


def test_more_people_in_a_carrier_takes_longer():
    light = timing(seats=1)
    full = timing(seats=4)
    assert full.total_time() > light.total_time()
    assert full.people() > light.people()


def test_a_step_off_costs_almost_nothing_and_needs_no_walk():
    low = timing(snow_feet=30)
    step_offs = [t for t in low.timings() if t.step.method is Method.STEP_OFF]
    assert step_offs
    for one in step_offs:
        assert one.approach.is_zero()
        assert one.setup == Duration.from_minutes(1)


def test_the_finish_is_set_by_the_busiest_team_not_by_the_total_work():
    one_team = timing(team(name="alpha", staged=0))
    assert one_team.total_time() == one_team.serial_time()
    two_teams = timing(team(name="base", staged=0), team(name="summit", staged=4800))
    assert two_teams.total_time() < two_teams.serial_time()


def test_a_second_team_does_not_halve_the_time():
    two_teams = timing(team(name="base", staged=0), team(name="summit", staged=4800))
    assert two_teams.speed_up() > 1
    assert two_teams.speed_up() < 2


def test_the_busiest_team_can_be_named():
    two_teams = timing(team(name="base", staged=0), team(name="summit", staged=4800))
    work = two_teams.work_by_team()
    busiest = two_teams.busiest_team()
    assert work[busiest] == max(work.values(), key=lambda spent: spent.seconds)


def test_the_call_out_is_in_the_total_whatever_else_happens():
    costed = timing()
    assert costed.total_time() > Duration.from_minutes(15)
    assert costed.serial_time() > Duration.from_minutes(15)


def test_a_team_staged_further_away_walks_longer():
    near = timing(team(name="near", staged=2400))
    far = timing(team(name="far", staged=0))
    assert approach_distance(far.plan) != approach_distance(near.plan)


def test_the_finish_time_falls_out_of_the_start():
    costed = timing()
    started = TimeOfDay.at(13)
    assert costed.finishes_by(started) == started.plus(costed.total_time())


def test_whether_the_job_fits_in_the_light():
    costed = timing()
    site = the_site()
    assert costed.fits_in_daylight(site, ON, TimeOfDay.at(9))
    assert not costed.fits_in_daylight(site, ON, TimeOfDay.at(15, 30))


def test_the_latest_start_is_the_number_the_board_asks_for():
    costed = timing()
    site = the_site()
    latest = costed.latest_start(site, ON)
    assert costed.fits_in_daylight(site, ON, latest)
    assert latest < site.sunset(ON)


def test_a_heavier_load_pulls_the_latest_start_earlier():
    site = the_site()
    light = timing(seats=1).latest_start(site, ON)
    heavy = timing(seats=4).latest_start(site, ON)
    assert heavy < light


def test_a_timing_prints_the_two_numbers_that_matter():
    printed = str(timing())
    assert "people," in printed
    assert printed.endswith(timing().total_time().format_hms())


def test_a_step_timing_prints_short():
    one = timing().timings()[0]
    assert str(one).startswith("carrier ")
