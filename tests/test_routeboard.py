from datetime import date

import pytest
from fastapi import status

from hill import john_paul, strawberry
from sheaveline.api.security import Role
from sheaveline.persistence.uow import UnitOfWork
from wire import client_for, headers, settings

ON = date(2025, 1, 15)


@pytest.fixture
def client():
    built = settings()
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.commit()
    with client_for(built) as opened:
        yield opened


def deropement(at="2025-01-15T11:30:00"):
    return {
        "at": at,
        "kind": "deropement",
        "injury": "none",
        "people": 0,
        "narrative": "chair 34 off the sheave train at T4",
        "reported_by": "K Ostler",
    }


def sprain(at="2025-01-15T10:00:00"):
    return {
        "at": at,
        "kind": "loading",
        "injury": "first aid",
        "people": 1,
        "narrative": "pole caught on the ramp",
        "reported_by": "K Ostler",
    }


def test_an_operator_records_an_incident_and_gets_the_deadline_back(client):
    reply = client.post("/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR))
    assert reply.status_code == status.HTTP_201_CREATED
    body = reply.json()
    assert body["kind"] == "deropement"
    assert body["deadline"] == "2:00:00"
    assert body["deadline_driver"] == "kind"
    assert body["due_by"] == "2025-01-15T13:30:00"
    assert body["holds_the_machine"] is True
    assert body["filing"] is None


def test_a_gentle_incident_goes_in_the_monthly_return(client):
    body = client.post("/lifts/JP/incidents", json=sprain(), headers=headers(Role.OPERATOR)).json()
    assert body["deadline_driver"] == "both"
    assert body["holds_the_machine"] is False


def test_a_reader_key_may_not_record_an_incident(client):
    reply = client.post("/lifts/JP/incidents", json=deropement(), headers=headers(Role.READER))
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_an_incident_against_a_machine_that_is_not_there(client):
    reply = client.post("/lifts/ZZ/incidents", json=deropement(), headers=headers(Role.OPERATOR))
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_somebody_hurt_with_nobody_involved_is_refused(client):
    body = sprain()
    body["people"] = 0
    reply = client.post("/lifts/JP/incidents", json=body, headers=headers(Role.OPERATOR))
    assert reply.status_code == status.HTTP_409_CONFLICT
    assert reply.json()["kind"] == "BoardError"


def test_an_incident_kind_that_does_not_exist_is_refused(client):
    body = deropement()
    body["kind"] = "something else"
    reply = client.post("/lifts/JP/incidents", json=body, headers=headers(Role.OPERATOR))
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


def test_the_incidents_on_one_machine_come_back_in_order(client):
    client.post(
        "/lifts/JP/incidents",
        json=deropement("2025-01-15T14:00:00"),
        headers=headers(Role.OPERATOR),
    )
    client.post("/lifts/JP/incidents", json=sprain(), headers=headers(Role.OPERATOR))
    client.post("/lifts/SB/incidents", json=sprain(), headers=headers(Role.OPERATOR))
    body = client.get("/lifts/JP/incidents", headers=headers()).json()
    assert [one["at"] for one in body] == [
        "2025-01-15T10:00:00",
        "2025-01-15T14:00:00",
    ]


def test_one_incident_comes_back_by_its_id(client):
    made = client.post(
        "/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR)
    ).json()
    body = client.get(f"/incidents/{made['id']}", headers=headers()).json()
    assert body["narrative"].startswith("chair 34")


def test_an_incident_that_is_not_there(client):
    assert client.get("/incidents/99", headers=headers()).status_code == 404


def test_the_outstanding_list_is_in_deadline_order_not_event_order(client):
    client.post("/lifts/JP/incidents", json=sprain(), headers=headers(Role.OPERATOR))
    client.post(
        "/lifts/JP/incidents",
        json=deropement("2025-01-15T14:00:00"),
        headers=headers(Role.OPERATOR),
    )
    body = client.get("/incidents/outstanding?now=2025-01-15T17:00:00", headers=headers()).json()
    assert [one["kind"] for one in body] == ["deropement", "loading"]
    assert body[0]["overdue"] is True
    assert body[0]["outstanding_for"] == "3:00:00"


def test_a_manager_files_an_incident_and_takes_the_next_number(client):
    made = client.post(
        "/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR)
    ).json()
    reply = client.post(
        f"/incidents/{made['id']}/filing",
        json={"filed": "2025-01-15T12:30:00", "filed_by": "H Okafor"},
        headers=headers(Role.MANAGER),
    )
    assert reply.status_code == status.HTTP_201_CREATED
    body = reply.json()
    assert body["number"] == "UT-2025-0001"
    assert body["late"] is False
    assert body["lateness"] == "0:00:00"


def test_a_late_filing_says_how_late_it_was(client):
    made = client.post(
        "/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR)
    ).json()
    body = client.post(
        f"/incidents/{made['id']}/filing",
        json={"filed": "2025-01-17T09:00:00", "filed_by": "H Okafor"},
        headers=headers(Role.MANAGER),
    ).json()
    assert body["late"] is True
    assert body["lateness"] == "43:30:00"


def test_a_filed_incident_drops_off_the_outstanding_list(client):
    made = client.post(
        "/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR)
    ).json()
    client.post(
        f"/incidents/{made['id']}/filing",
        json={"filed": "2025-01-15T12:30:00", "filed_by": "H Okafor"},
        headers=headers(Role.MANAGER),
    )
    assert client.get("/incidents/outstanding", headers=headers()).json() == []
    body = client.get(f"/incidents/{made['id']}", headers=headers()).json()
    assert body["filing"] == "UT-2025-0001"


def test_an_incident_is_filed_once(client):
    made = client.post(
        "/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR)
    ).json()
    payload = {"filed": "2025-01-15T12:30:00", "filed_by": "H Okafor"}
    client.post(f"/incidents/{made['id']}/filing", json=payload, headers=headers(Role.MANAGER))
    again = client.post(
        f"/incidents/{made['id']}/filing", json=payload, headers=headers(Role.MANAGER)
    )
    assert again.status_code == status.HTTP_404_NOT_FOUND


def test_the_numbers_run_in_sequence_across_incidents(client):
    numbers = []
    for hour in (11, 14):
        made = client.post(
            "/lifts/JP/incidents",
            json=deropement(f"2025-01-15T{hour}:30:00"),
            headers=headers(Role.OPERATOR),
        ).json()
        numbers.append(
            client.post(
                f"/incidents/{made['id']}/filing",
                json={"filed": "2025-01-15T16:00:00", "filed_by": "H Okafor"},
                headers=headers(Role.MANAGER),
            ).json()["number"]
        )
    assert numbers == ["UT-2025-0001", "UT-2025-0002"]


def test_an_operator_key_may_not_file(client):
    made = client.post(
        "/lifts/JP/incidents", json=deropement(), headers=headers(Role.OPERATOR)
    ).json()
    reply = client.post(
        f"/incidents/{made['id']}/filing",
        json={"filed": "2025-01-15T12:30:00", "filed_by": "H Okafor"},
        headers=headers(Role.OPERATOR),
    )
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED
