from datetime import date

import pytest

from hill import JOHN_PAUL, STRAWBERRY, the_season
from sheaveline.evac.drill import Drill, DrillKind, DrillRegister
from sheaveline.evac.profile import EvacuationError
from sheaveline.measures.duration import Duration

SEASON = the_season()
TEAMS = ("alpha", "bravo", "charlie")


def drill(
    lift=JOHN_PAUL,
    on=date(2024, 12, 5),
    kind=DrillKind.FULL_LINE,
    teams=TEAMS,
    carriers=8,
    minutes=95,
    observer="H Okafor",
):
    return Drill.of(lift, on, kind, teams, carriers, Duration.from_minutes(minutes), observer)


def register(*drills):
    return DrillRegister.of(drills)


def test_a_drill_needs_teams_an_observer_and_no_duplicates():
    with pytest.raises(EvacuationError):
        drill(teams=())
    with pytest.raises(EvacuationError):
        drill(teams=("alpha", "alpha"))
    with pytest.raises(EvacuationError):
        drill(observer="  ")


def test_a_drill_on_the_line_works_at_least_one_carrier():
    with pytest.raises(EvacuationError):
        drill(kind=DrillKind.PARTIAL, carriers=0)
    with pytest.raises(EvacuationError):
        drill(carriers=-1)


def test_a_tabletop_drill_touches_no_carriers():
    with pytest.raises(EvacuationError):
        drill(kind=DrillKind.TABLETOP, carriers=4)
    assert drill(kind=DrillKind.TABLETOP, carriers=0).carriers_worked == 0


def test_only_a_full_line_drill_with_enough_carriers_counts_for_the_annual():
    assert drill(kind=DrillKind.FULL_LINE, carriers=8).counts_for_the_annual()
    assert not drill(kind=DrillKind.FULL_LINE, carriers=2).counts_for_the_annual()
    assert not drill(kind=DrillKind.PARTIAL, carriers=8).counts_for_the_annual()
    assert not drill(kind=DrillKind.TABLETOP, carriers=0).counts_for_the_annual()


def test_the_kinds_know_what_they_exercise():
    assert DrillKind.FULL_LINE.puts_people_on_the_line
    assert DrillKind.PARTIAL.puts_people_on_the_line
    assert not DrillKind.TABLETOP.puts_people_on_the_line
    assert DrillKind.FULL_LINE.satisfies_the_annual
    assert not DrillKind.PARTIAL.satisfies_the_annual


def test_a_drill_reports_the_rate_it_achieved():
    assert drill(carriers=8, minutes=96).minutes_per_carrier() == Duration.from_minutes(12)
    with pytest.raises(EvacuationError):
        drill(kind=DrillKind.TABLETOP, carriers=0).minutes_per_carrier()


def test_a_drill_expires_a_year_after_it_was_run():
    assert drill(on=date(2024, 12, 5)).expires() == date(2025, 12, 5)


def test_a_drill_prints_a_one_liner():
    assert str(drill()) == "JP full line on 2024-12-05, 8 carriers"


def test_the_lift_is_current_for_a_year_after_a_full_drill():
    held = register(drill(on=date(2024, 12, 5)))
    assert held.lift_is_current(JOHN_PAUL, date(2025, 1, 15))
    assert not held.lift_is_current(JOHN_PAUL, date(2026, 1, 15))
    assert held.lift_expires(JOHN_PAUL) == date(2025, 12, 5)


def test_a_machine_with_no_full_drill_is_never_current():
    held = register(drill(kind=DrillKind.PARTIAL, carriers=3))
    assert held.last_full(JOHN_PAUL) is None
    assert not held.lift_is_current(JOHN_PAUL, date(2025, 1, 15))
    assert held.lift_expires(JOHN_PAUL) is None


def test_drills_are_kept_apart_by_machine():
    held = register(drill(lift=JOHN_PAUL), drill(lift=STRAWBERRY))
    assert len(held) == 2
    assert len(held.on_lift(JOHN_PAUL)) == 1
    assert held.lift_is_current(STRAWBERRY, date(2025, 1, 15))


def test_a_team_is_current_only_if_it_was_in_a_drill_this_season():
    held = register(drill(teams=("alpha", "bravo")))
    assert held.team_took_part("alpha", JOHN_PAUL, SEASON)
    assert not held.team_took_part("charlie", JOHN_PAUL, SEASON)


def test_a_drill_from_last_season_does_not_make_a_team_current():
    held = register(drill(on=date(2024, 3, 1), teams=("alpha",)))
    assert not held.team_took_part("alpha", JOHN_PAUL, SEASON)


def test_the_teams_still_short_are_listed():
    held = register(drill(teams=("alpha", "bravo")))
    assert held.teams_short(JOHN_PAUL, SEASON, TEAMS) == ("charlie",)


def test_a_machine_with_new_teams_is_not_current_however_good_the_drill_was():
    held = register(drill(teams=("alpha", "bravo")))
    assert held.lift_is_current(JOHN_PAUL, date(2025, 1, 15))
    assert not held.is_current(JOHN_PAUL, date(2025, 1, 15), SEASON, TEAMS)


def test_a_machine_with_everybody_in_is_current_on_both_counts():
    held = register(drill(teams=TEAMS))
    assert held.is_current(JOHN_PAUL, date(2025, 1, 15), SEASON, TEAMS)


def test_the_best_rate_the_hill_has_achieved_is_what_a_plan_is_checked_against():
    held = register(
        drill(on=date(2024, 12, 5), carriers=8, minutes=96),
        drill(on=date(2025, 1, 5), kind=DrillKind.PARTIAL, carriers=4, minutes=32),
    )
    assert held.rate_for(JOHN_PAUL) == Duration.from_minutes(8)
    with pytest.raises(EvacuationError):
        held.rate_for(STRAWBERRY)


def test_adding_a_drill_makes_a_new_register():
    held = register()
    longer = held.with_drill(drill())
    assert len(held) == 0
    assert len(longer) == 1
