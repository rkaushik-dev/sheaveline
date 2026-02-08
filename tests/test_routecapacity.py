import pytest
from fastapi import status

from hill import john_paul, strawberry, wildcat
from sheaveline.persistence.uow import UnitOfWork
from wire import client_for, headers, settings


@pytest.fixture
def client():
    built = settings()
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.lifts.add(wildcat())
        work.commit()
    with client_for(built) as opened:
        yield opened


def test_the_spacing_falls_out_of_the_machine_on_file(client):
    body = client.get("/lifts/JP/spacing", headers=headers()).json()
    assert body["carriers"] == 84
    assert body["speed"] == "1000 fpm"
    assert body["spacing"].endswith(" ft")
    assert body["meets_the_floor"] is True
    assert body["shortfall"] == "0:00:00"


def test_taking_carriers_off_opens_the_interval(client):
    full = client.get("/lifts/JP/spacing", headers=headers()).json()
    thin = client.get("/lifts/JP/spacing?carriers=42", headers=headers()).json()
    assert thin["carriers"] == 42
    assert thin["interval"] > full["interval"]


def test_slowing_the_rope_opens_it_too(client):
    fast = client.get("/lifts/JP/spacing", headers=headers()).json()
    slow = client.get("/lifts/JP/spacing?speed=500+fpm", headers=headers()).json()
    assert slow["speed"] == "500 fpm"
    assert slow["interval"] > fast["interval"]
    assert slow["spacing"] == fast["spacing"]


def test_the_class_floor_comes_back_with_the_answer(client):
    quad = client.get("/lifts/JP/spacing", headers=headers()).json()
    triple = client.get("/lifts/SB/spacing", headers=headers()).json()
    assert quad["class_floor"] == "0:00:06"
    assert triple["class_floor"] == "0:00:09"


def test_a_line_run_too_full_says_how_far_under_the_floor_it_is(client):
    body = client.get("/lifts/JP/spacing?carriers=2000", headers=headers()).json()
    assert body["meets_the_floor"] is False
    assert body["shortfall"] != "0:00:00"
    assert body["most_carriers"] < 2000


def test_a_speed_that_does_not_parse_is_a_four_hundred(client):
    reply = client.get("/lifts/JP/spacing?speed=quite+fast", headers=headers())
    assert reply.status_code == status.HTTP_400_BAD_REQUEST


def test_a_carrier_count_of_nothing_is_refused_before_it_reaches_the_domain(client):
    reply = client.get("/lifts/JP/spacing?carriers=0", headers=headers())
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


def test_the_capacity_reads_three_ways(client):
    body = client.get("/lifts/JP/capacity", headers=headers()).json()
    assert body["seats_per_hour"] > body["persons_per_hour"]
    assert body["carriers_per_hour"] * 4 == body["seats_per_hour"]
    assert body["vertical_feet_per_hour"] > body["persons_per_hour"]


def test_a_worse_loading_efficiency_moves_only_the_persons_figure(client):
    good = client.get("/lifts/JP/capacity?occupancy=0.95", headers=headers()).json()
    poor = client.get("/lifts/JP/capacity?occupancy=0.60", headers=headers()).json()
    assert good["seats_per_hour"] == poor["seats_per_hour"]
    assert good["persons_per_hour"] > poor["persons_per_hour"]


def test_the_capacity_answers_a_what_if_without_storing_it(client):
    thin = client.get("/lifts/JP/capacity?carriers=42", headers=headers()).json()
    assert thin["carriers"] == 42
    again = client.get("/lifts/JP/capacity", headers=headers()).json()
    assert again["carriers"] == 84


def test_an_occupancy_outside_the_range_is_refused(client):
    reply = client.get("/lifts/JP/capacity?occupancy=1.4", headers=headers())
    assert reply.status_code == status.HTTP_409_CONFLICT
    assert reply.json()["kind"] == "CapacityError"


def test_the_line_walk_comes_back_fit_in_a_thin_year(client):
    body = client.get("/lifts/JP/geometry?snow=2+ft", headers=headers()).json()
    assert body["verdict"] == "fit"
    assert body["may_run"] is True
    assert body["worst_margin"].endswith(" ft")
    # A note about a tower reading off its design figure is information, not a
    # reason to stop, so a fit line may still carry some.
    assert all(one["severity"] == "note" for one in body["findings"])


def test_deep_snow_takes_the_line_out_and_says_where(client):
    body = client.get("/lifts/JP/geometry?snow=40+ft", headers=headers()).json()
    assert body["verdict"] == "unfit"
    assert body["may_run"] is False
    assert body["findings"]
    assert any(one["severity"] == "stop" for one in body["findings"])


def test_dropping_the_tension_lets_the_rope_down(client):
    tight = client.get("/lifts/JP/geometry?snow=6+ft&tension=40000+lb", headers=headers()).json()
    slack = client.get("/lifts/JP/geometry?snow=6+ft&tension=12000+lb", headers=headers()).json()
    assert slack["worst_clearance"] < tight["worst_clearance"]


def test_the_conditions_come_back_with_the_answer(client):
    body = client.get("/lifts/JP/geometry?snow=6+ft&tension=34500+lb", headers=headers()).json()
    assert body["snow"] == "6.00 ft"
    assert body["tension"] == "34,500 lb"


def test_a_snow_depth_that_does_not_parse_is_a_four_hundred(client):
    reply = client.get("/lifts/JP/geometry?snow=deep", headers=headers())
    assert reply.status_code == status.HTTP_400_BAD_REQUEST


def test_a_machine_that_is_not_there_is_a_four_oh_four_on_every_route(client):
    for path in ("spacing", "capacity", "geometry"):
        reply = client.get(f"/lifts/ZZ/{path}", headers=headers())
        assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_the_surface_tow_answers_the_same_questions(client):
    body = client.get("/lifts/WC/spacing", headers=headers()).json()
    assert body["carriers"] == 22
    assert body["class_floor"] == "0:00:05"
