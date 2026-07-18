from datetime import date

import pytest
from fastapi import status

from hill import john_paul, strawberry, wildcat
from sheaveline.api.security import Role
from sheaveline.persistence.uow import UnitOfWork
from wire import client_for, headers, settings

ON = date(2025, 1, 15)
SERIAL = "FAT-2019-04412"


@pytest.fixture
def client():
    built = settings()
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.ropes.add(wildcat().rope)
        work.commit()
    with client_for(built) as opened:
        yield opened


def survey_body(inches="1.376", taken="2025-01-15"):
    return {
        "taken": taken,
        "inspector": "R Halvorsen",
        "diameters": [
            {
                "station": "0 ft",
                "across_crowns": f"{inches} in",
                "across_valleys": f"{inches} in",
            },
            {
                "station": "1200 ft",
                "across_crowns": f"{inches} in",
                "across_valleys": f"{inches} in",
            },
        ],
    }


def test_every_rope_on_file_comes_back(client):
    body = client.get("/ropes", headers=headers()).json()
    assert len(body) == 3
    assert {one["serial"] for one in body} >= {SERIAL}


def test_a_rope_with_no_machine_under_it_is_a_spare(client):
    body = client.get("/ropes/spares", headers=headers()).json()
    assert [one["serial"] for one in body] == ["BRI-2016-00294"]


def test_one_rope_comes_back_with_the_figures_off_its_spec(client):
    body = client.get(f"/ropes/{SERIAL}", headers=headers()).json()
    assert body["construction"] == "6x25 filler wire"
    assert body["diameter"] == '1.378"'
    assert body["condemning_diameter"] == '1.282"'
    assert body["maximum_tension"] == "49,000 lb"
    assert body["endless"] is True


def test_a_rope_that_is_not_on_file_is_a_four_oh_four(client):
    reply = client.get("/ropes/FAT-2019-99999", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_something_that_is_not_a_serial_is_a_four_hundred(client):
    reply = client.get("/ropes/not-a-serial", headers=headers())
    assert reply.status_code == status.HTTP_400_BAD_REQUEST


def test_a_rope_starts_with_no_walks_against_it(client):
    assert client.get(f"/ropes/{SERIAL}/surveys", headers=headers()).json()["items"] == []


def test_a_mechanic_records_a_walk_and_gets_the_verdict_back(client):
    reply = client.post(
        f"/ropes/{SERIAL}/surveys", json=survey_body(), headers=headers(Role.MECHANIC)
    )
    assert reply.status_code == status.HTTP_201_CREATED
    body = reply.json()
    assert body["verdict"] == "serviceable"
    assert body["inspector"] == "R Halvorsen"
    assert len(body["readings"]) == 2


def test_a_worn_rope_comes_back_condemned_in_the_same_reply(client):
    reply = client.post(
        f"/ropes/{SERIAL}/surveys",
        json=survey_body(inches="1.270"),
        headers=headers(Role.MECHANIC),
    )
    assert reply.status_code == status.HTTP_201_CREATED
    assert reply.json()["verdict"] == "condemned"


def test_a_recorded_walk_comes_back_on_the_list(client):
    client.post(f"/ropes/{SERIAL}/surveys", json=survey_body(), headers=headers(Role.MECHANIC))
    body = client.get(f"/ropes/{SERIAL}/surveys", headers=headers()).json()["items"]
    assert len(body) == 1
    assert body[0]["taken"] == "2025-01-15"
    assert body[0]["smallest"]["station"] == "0.00 ft"


def test_a_reader_key_may_not_record_a_walk(client):
    reply = client.post(
        f"/ropes/{SERIAL}/surveys", json=survey_body(), headers=headers(Role.READER)
    )
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_a_walk_with_no_readings_is_refused(client):
    body = survey_body()
    body["diameters"] = []
    reply = client.post(f"/ropes/{SERIAL}/surveys", json=body, headers=headers(Role.MECHANIC))
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY
    assert reply.json()["kind"] == "RequestValidationError"


def test_a_reading_that_does_not_parse_is_refused(client):
    body = survey_body()
    body["diameters"][0]["across_crowns"] = "about an inch and a bit"
    reply = client.post(f"/ropes/{SERIAL}/surveys", json=body, headers=headers(Role.MECHANIC))
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


def test_a_walk_taken_before_the_rope_went_on_is_refused(client):
    reply = client.post(
        f"/ropes/{SERIAL}/surveys",
        json=survey_body(taken="2015-01-15"),
        headers=headers(Role.MECHANIC),
    )
    assert reply.status_code == status.HTTP_409_CONFLICT
    assert reply.json()["kind"] == "RopeworkError"


def test_two_walks_on_one_day_are_refused_by_the_database(client):
    client.post(f"/ropes/{SERIAL}/surveys", json=survey_body(), headers=headers(Role.MECHANIC))
    reply = client.post(
        f"/ropes/{SERIAL}/surveys", json=survey_body(), headers=headers(Role.MECHANIC)
    )
    assert reply.status_code >= 400


def test_the_rope_file_reads_the_latest_walk(client):
    assert client.get(f"/ropes/{SERIAL}/file", headers=headers()).json() == {
        "serial": SERIAL,
        "verdict": "serviceable",
        "may_run": True,
        "sources": [],
        "findings": [],
    }
    client.post(
        f"/ropes/{SERIAL}/surveys",
        json=survey_body(inches="1.270"),
        headers=headers(Role.MECHANIC),
    )
    body = client.get(f"/ropes/{SERIAL}/file", headers=headers()).json()
    assert body["verdict"] == "condemned"
    assert body["may_run"] is False
    assert body["sources"] == ["diameter"]
    assert body["findings"][0]["source"] == "diameter"
