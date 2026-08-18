import pytest
from fastapi import status

from hill import john_paul
from sheaveline import __version__
from sheaveline.api.app import Settings
from sheaveline.api.security import Role
from sheaveline.persistence.base import build_engine, table_names
from sheaveline.persistence.uow import UnitOfWork
from wire import RING, TODAY, client_for, fresh_client, headers, settings


@pytest.fixture
def built():
    made = settings()
    with UnitOfWork.on(made.engine) as work:
        work.lifts.add(john_paul())
        work.commit()
    return made


@pytest.fixture
def client(built):
    with client_for(built) as opened:
        yield opened


def test_health_answers_without_a_key(client):
    reply = client.get("/health")
    assert reply.status_code == status.HTTP_200_OK
    assert reply.json()["service"] == "sheaveline"
    assert reply.json()["version"] == __version__


def test_health_carries_the_resort_it_was_built_for():
    with client_for(settings(resort="Snowbasin")) as opened:
        assert opened.get("/health").json()["resort"] == "Snowbasin"


def test_health_does_not_touch_the_database():
    """A health check that opens a connection takes the service down when the disk is slow."""
    engine = build_engine("sqlite+pysqlite:///nowhere/at/all.sqlite3")
    with fresh_client(Settings(engine, keys=RING, today=lambda: TODAY)) as opened:
        assert opened.get("/health").status_code == status.HTTP_200_OK
    engine.dispose()


def test_ready_says_what_it_found(client):
    reply = client.get("/ready")
    assert reply.status_code == status.HTTP_200_OK
    body = reply.json()
    assert body["ready"]
    assert body["tables"] == len(table_names())
    assert body["missing"] == []
    assert body["lifts"] == 1
    assert body["today"] == TODAY.isoformat()
    assert body["detail"] == "ready"


def test_ready_needs_no_key_either(client):
    assert client.get("/ready").status_code == status.HTTP_200_OK


def test_a_database_with_no_tables_is_not_ready():
    engine = build_engine()
    with fresh_client(Settings(engine, keys=RING, today=lambda: TODAY)) as opened:
        reply = opened.get("/ready")
    engine.dispose()
    assert reply.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
    body = reply.json()
    assert not body["ready"]
    assert body["missing"]
    assert "tables are not built yet" in body["detail"]


def test_the_two_checks_answer_differently_on_the_same_broken_database():
    engine = build_engine()
    with fresh_client(Settings(engine, keys=RING, today=lambda: TODAY)) as opened:
        assert opened.get("/health").status_code == status.HTTP_200_OK
        assert opened.get("/ready").status_code == status.HTTP_503_SERVICE_UNAVAILABLE
    engine.dispose()


def team(name="alpha", ladder=24, rope=200):
    return {"name": name, "ladder_feet": ladder, "rope_feet": rope}


def test_a_staging_plan_comes_back_as_a_beat_sheet(client):
    reply = client.post(
        "/lifts/JP/evacuation",
        json={"teams": [team("alpha"), team("bravo")], "seats_filled": 3},
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_200_OK
    body = reply.json()
    assert body["workable"]
    assert body["carriers"] == 84
    assert len(body["beats"]) == 2
    assert sum(one["carriers"] for one in body["beats"]) == 84
    assert body["clears_in"] is not None


def test_a_second_team_cuts_the_clearing_time(client):
    def clears(count):
        reply = client.post(
            "/lifts/JP/evacuation",
            json={"teams": [team(f"team{one}") for one in range(count)]},
            headers=headers(Role.OPERATOR),
        )
        return reply.json()["clears_in"]

    assert clears(1) > clears(2)


def test_a_team_that_cannot_reach_the_line_is_refused_rather_than_hidden(client):
    reply = client.post(
        "/lifts/JP/evacuation",
        json={"teams": [team("short", ladder=8, rope=None)]},
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_200_OK
    body = reply.json()
    assert not body["workable"]
    assert body["stranded"] > 0
    assert body["clears_in"] is None
    assert any("nobody reaches it" in one for one in body["refusals"])


def test_a_reader_may_not_ask_for_a_plan(client):
    reply = client.post("/lifts/JP/evacuation", json={"teams": [team()]}, headers=headers())
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_a_plan_with_no_teams_in_it(client):
    reply = client.post("/lifts/JP/evacuation", json={"teams": []}, headers=headers(Role.OPERATOR))
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


def test_a_machine_that_is_not_on_the_register(client):
    reply = client.post(
        "/lifts/ZZ/evacuation", json={"teams": [team()]}, headers=headers(Role.OPERATOR)
    )
    assert reply.status_code == status.HTTP_404_NOT_FOUND
