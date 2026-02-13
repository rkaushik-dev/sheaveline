import pytest
from fastapi import status

from hill import john_paul, strawberry, wildcat
from sheaveline.api.security import Role
from sheaveline.persistence.uow import UnitOfWork
from wire import client_for, headers, settings

NOON = "2025-01-15T12:00:00"


@pytest.fixture
def client():
    built = settings()
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.lifts.add(wildcat())
        work.commit()
    with client_for(built) as opened:
        stations(opened)
        yield opened


def stations(client):
    for name, feet in (("summit", "7981 ft"), ("mid", "7100 ft"), ("base", "6400 ft")):
        client.post(
            "/weather/stations",
            json={"name": name, "elevation": feet},
            headers=headers(Role.MANAGER),
        )


def reading(
    station="summit",
    taken=NOON,
    wind="18 mph",
    bearing="270",
    gust=None,
    degrees="8 F",
    **rest,
):
    body = {
        "station": station,
        "taken": taken,
        "wind": wind,
        "bearing": bearing,
        "temperature": degrees,
    }
    if gust is not None:
        body["gust"] = gust
    body.update(rest)
    return body


def test_a_manager_sets_up_a_station(client):
    reply = client.post(
        "/weather/stations",
        json={"name": "cirque", "elevation": "8200 ft"},
        headers=headers(Role.MANAGER),
    )
    assert reply.status_code == status.HTTP_201_CREATED
    assert reply.json()["elevation"] == "8200.00 ft"


def test_a_station_goes_in_once(client):
    reply = client.post(
        "/weather/stations",
        json={"name": "summit", "elevation": "7981 ft"},
        headers=headers(Role.MANAGER),
    )
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_an_operator_key_may_not_set_up_a_station(client):
    reply = client.post(
        "/weather/stations",
        json={"name": "cirque", "elevation": "8200 ft"},
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_the_stations_come_back_in_name_order(client):
    body = client.get("/weather/stations", headers=headers()).json()
    assert [one["name"] for one in body] == ["base", "mid", "summit"]


def test_an_operator_records_a_reading(client):
    reply = client.post(
        "/weather/observations",
        json=reading(gust="38 mph", wind="24 mph", bearing="SW"),
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_201_CREATED
    body = reply.json()
    assert body["wind"] == "24 mph"
    assert body["gust"] == "38 mph"
    assert body["peak"] == "38 mph"
    assert body["bearing"] == "225 deg"


def test_the_chill_comes_back_with_the_reading(client):
    body = client.post(
        "/weather/observations",
        json=reading(wind="20 mph", degrees="5 F"),
        headers=headers(Role.OPERATOR),
    ).json()
    assert body["chill"] == "-15.4 F"


def test_a_reading_against_a_station_that_is_not_there(client):
    reply = client.post(
        "/weather/observations",
        json=reading(station="cirque"),
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_a_gust_below_the_sustained_wind_is_refused(client):
    reply = client.post(
        "/weather/observations",
        json=reading(wind="30 mph", gust="20 mph"),
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_400_BAD_REQUEST


def test_the_readings_come_back_and_can_be_narrowed(client):
    client.post("/weather/observations", json=reading(), headers=headers(Role.OPERATOR))
    client.post(
        "/weather/observations",
        json=reading(station="base", degrees="24 F"),
        headers=headers(Role.OPERATOR),
    )
    assert len(client.get("/weather/observations", headers=headers()).json()) == 2
    summit = client.get("/weather/observations?station=summit", headers=headers()).json()
    assert [one["station"] for one in summit] == ["summit"]


def test_readings_can_be_narrowed_by_time(client):
    for minute in ("05", "35"):
        client.post(
            "/weather/observations",
            json=reading(taken=f"2025-01-15T12:{minute}:00"),
            headers=headers(Role.OPERATOR),
        )
    body = client.get("/weather/observations?since=2025-01-15T12:20:00", headers=headers()).json()
    assert [one["taken"] for one in body] == ["2025-01-15T12:35:00"]


def test_a_quiet_reading_leaves_the_machine_clear(client):
    client.post("/weather/observations", json=reading(), headers=headers(Role.OPERATOR))
    body = client.get(f"/lifts/JP/hold?now={NOON}", headers=headers()).json()
    assert body["held"] is False
    assert body["may_run"] is True
    assert body["reasons"] == []
    assert body["reading"]["station"] == "summit"
    assert body["reading_age"] == "0:00:00"


def test_the_hold_reads_the_station_nearest_the_top_terminal(client):
    client.post(
        "/weather/observations",
        json=reading(station="summit", wind="60 mph", bearing="SSE"),
        headers=headers(Role.OPERATOR),
    )
    client.post(
        "/weather/observations",
        json=reading(station="base", wind="2 mph"),
        headers=headers(Role.OPERATOR),
    )
    body = client.get(f"/lifts/JP/hold?now={NOON}", headers=headers()).json()
    assert body["reading"]["station"] == "summit"
    assert body["held"] is True


def test_the_hold_names_which_weather_stopped_it(client):
    client.post(
        "/weather/observations",
        json=reading(wind="30 mph", gust="60 mph", bearing="SSE"),
        headers=headers(Role.OPERATOR),
    )
    body = client.get(f"/lifts/JP/hold?now={NOON}", headers=headers()).json()
    reasons = {one["reason"] for one in body["reasons"]}
    assert "gust" in reasons
    assert "crosswind" in reasons
    assert body["gust_limit"] == "45 mph"


def test_a_lightning_hold_says_it_runs_on_a_clock(client):
    client.post(
        "/weather/observations",
        json=reading(lightning_within="20000 ft"),
        headers=headers(Role.OPERATOR),
    )
    body = client.get(f"/lifts/JP/hold?now={NOON}", headers=headers()).json()
    assert body["needs_a_timer"] is True
    bolt = next(one for one in body["reasons"] if one["reason"] == "lightning")
    assert bolt["clears_on_reading"] is False


def test_a_surface_tow_reads_the_base_and_has_no_crosswind_limit(client):
    client.post("/weather/observations", json=reading(), headers=headers(Role.OPERATOR))
    client.post(
        "/weather/observations",
        json=reading(station="base", degrees="24 F"),
        headers=headers(Role.OPERATOR),
    )
    body = client.get(f"/lifts/WC/hold?now={NOON}", headers=headers()).json()
    assert body["reading"]["station"] == "base"
    assert body["crosswind_limit"] is None


def test_a_stale_reading_is_refused_rather_than_answered_from(client):
    client.post(
        "/weather/observations",
        json=reading(taken="2025-01-15T11:00:00"),
        headers=headers(Role.OPERATOR),
    )
    reply = client.get(f"/lifts/JP/hold?now={NOON}", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND
    assert "stale" in reply.json()["detail"]


def test_the_staleness_window_can_be_widened_by_the_caller(client):
    client.post(
        "/weather/observations",
        json=reading(taken="2025-01-15T11:00:00"),
        headers=headers(Role.OPERATOR),
    )
    reply = client.get(f"/lifts/JP/hold?now={NOON}&stale_after_minutes=120", headers=headers())
    assert reply.status_code == status.HTTP_200_OK
    assert reply.json()["reading_age"] == "1:00:00"


def test_a_machine_with_no_reading_anywhere_is_a_four_oh_four(client):
    reply = client.get(f"/lifts/JP/hold?now={NOON}", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND
