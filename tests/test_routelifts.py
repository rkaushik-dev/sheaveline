from datetime import date

import pytest
from fastapi import status
from fastapi.testclient import TestClient

from hill import john_paul, strawberry, wildcat
from sheaveline.api.app import Settings, build_app
from sheaveline.api.security import ApiKey, KeyRing, Role
from sheaveline.persistence.uow import UnitOfWork

ON = date(2025, 1, 15)
SECRETS: dict[Role, str] = {}


def ring() -> KeyRing:
    keys = []
    for index, role in enumerate(Role):
        key, secret = ApiKey.issue(
            holder=f"{role.value} client",
            role=role,
            issued=date(2024, 11, 1),
            expires=date(2025, 12, 1),
            key_id=f"{index:012d}",
        )
        SECRETS[role] = f"{key.key_id}.{secret}"
        keys.append(key)
    return KeyRing.of(keys)


@pytest.fixture
def client():
    settings = Settings.in_memory(keys=ring(), today=lambda: ON)
    with UnitOfWork.on(settings.engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.lifts.add(wildcat())
        work.commit()
    app = build_app(settings)
    with TestClient(app, raise_server_exceptions=False) as opened:
        yield opened


def headers(role=Role.READER):
    return {"x-api-key": SECRETS[role]}


def test_the_list_comes_back_in_code_order(client):
    reply = client.get("/lifts", headers=headers())
    assert reply.status_code == status.HTTP_200_OK
    assert [one["code"] for one in reply.json()] == ["JP", "SB", "WC"]


def test_one_machine_comes_back_in_full(client):
    reply = client.get("/lifts/JP", headers=headers())
    assert reply.status_code == status.HTTP_200_OK
    body = reply.json()
    assert body["name"] == "John Paul"
    assert body["horizontal_length"] == "4800.00 ft"
    assert len(body["towers"]) == 7
    assert len(body["spans"]) == 8


def test_a_lower_case_code_is_read_the_same_way(client):
    assert client.get("/lifts/jp", headers=headers()).status_code == status.HTTP_200_OK


def test_a_machine_that_is_not_there_is_a_four_oh_four(client):
    reply = client.get("/lifts/ZZ", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND
    assert reply.json()["kind"] == "NotFound"


def test_something_that_is_not_a_code_is_a_four_hundred(client):
    reply = client.get("/lifts/1A", headers=headers())
    assert reply.status_code == status.HTTP_400_BAD_REQUEST
    assert reply.json()["field"] == "a lift code"


def test_the_towers_come_back_on_their_own(client):
    reply = client.get("/lifts/JP/towers", headers=headers())
    body = reply.json()
    assert [one["number"] for one in body] == [1, 2, 3, 4, 5, 6, 7]
    assert body[0]["reference"] == "JP-T1"


def test_the_terminals_come_back_on_their_own(client):
    body = client.get("/lifts/JP/terminals", headers=headers()).json()
    assert [one["role"] for one in body] == ["drive", "return tension"]


def test_the_spans_come_back_on_their_own(client):
    body = client.get("/lifts/JP/spans", headers=headers()).json()
    assert [one["index"] for one in body] == list(range(1, 9))
    assert body[0]["horizontal"].endswith(" ft")


def test_a_request_with_no_key_is_refused(client):
    reply = client.get("/lifts")
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED
    assert reply.json()["kind"] == "AuthError"


def test_a_key_that_is_not_on_the_ring_is_refused(client):
    reply = client.get("/lifts", headers={"x-api-key": "ffffffffffff.nonsense"})
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_a_reader_key_may_read_and_may_not_withdraw(client):
    assert client.get("/lifts", headers=headers(Role.READER)).status_code == 200
    reply = client.delete("/lifts/WC", headers=headers(Role.READER))
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_a_manager_key_may_withdraw_a_machine(client):
    reply = client.delete("/lifts/WC", headers=headers(Role.MANAGER))
    assert reply.status_code == status.HTTP_204_NO_CONTENT
    assert reply.content == b""
    assert [one["code"] for one in client.get("/lifts", headers=headers()).json()] == [
        "JP",
        "SB",
    ]


def test_withdrawing_a_machine_that_is_not_there(client):
    reply = client.delete("/lifts/ZZ", headers=headers(Role.MANAGER))
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_a_higher_role_covers_a_lower_one(client):
    assert client.get("/lifts", headers=headers(Role.MANAGER)).status_code == 200
    assert client.get("/lifts", headers=headers(Role.MECHANIC)).status_code == 200


def test_the_health_route_needs_no_key(client):
    assert client.get("/health").status_code == status.HTTP_200_OK


def test_a_lapsed_key_is_refused_even_with_the_right_role():
    keys = ring()
    settings = Settings.in_memory(keys=keys, today=lambda: date(2026, 6, 1))
    app = build_app(settings)
    with TestClient(app, raise_server_exceptions=False) as client:
        reply = client.get("/lifts", headers=headers(Role.MANAGER))
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED
