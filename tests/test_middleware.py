import io

import pytest
from fastapi import status

from hill import john_paul
from sheaveline.api.app import Settings
from sheaveline.api.middleware import REQUEST_ID_HEADER
from sheaveline.journal import Journal, Level
from sheaveline.persistence.uow import UnitOfWork
from wire import RING, TODAY, fresh_client, headers


@pytest.fixture
def written():
    return io.StringIO()


@pytest.fixture
def client(written):
    book = Journal(written, Level.INFO, clock="2025-01-15T07:00:00")
    built = Settings.in_memory(keys=RING, today=lambda: TODAY, journal=book)
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        work.commit()
    with fresh_client(built) as opened:
        yield opened


def lines(written):
    return [one for one in written.getvalue().splitlines() if one]


def test_a_request_writes_one_line_with_the_status_on_it(client, written):
    reply = client.get("/lifts", headers=headers())
    assert reply.status_code == status.HTTP_200_OK
    assert len(lines(written)) == 1
    assert "method=GET" in lines(written)[0]
    assert "path=/lifts" in lines(written)[0]
    assert "status=200" in lines(written)[0]
    assert "ms=" in lines(written)[0]


def test_the_id_comes_back_on_the_response_and_is_on_the_line(client, written):
    reply = client.get("/lifts", headers=headers())
    given = reply.headers[REQUEST_ID_HEADER]
    assert len(given) == 12
    assert f"request={given}" in lines(written)[0]


def test_an_id_the_caller_supplies_is_kept(client, written):
    reply = client.get("/lifts", headers={**headers(), REQUEST_ID_HEADER: "radio-call-41"})
    assert reply.headers[REQUEST_ID_HEADER] == "radio-call-41"
    assert "request=radio-call-41" in lines(written)[0]


def test_two_requests_get_two_different_ids(client):
    first = client.get("/lifts", headers=headers()).headers[REQUEST_ID_HEADER]
    second = client.get("/lifts", headers=headers()).headers[REQUEST_ID_HEADER]
    assert first != second


def test_a_refused_request_still_gets_its_line(client, written):
    reply = client.get("/lifts/ZZ", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND
    assert "status=404" in lines(written)[0]


def test_a_request_with_no_key_still_gets_its_line(client, written):
    assert client.get("/lifts").status_code == status.HTTP_401_UNAUTHORIZED
    assert "status=401" in lines(written)[0]


def test_a_silent_journal_writes_nothing_but_still_hands_out_ids():
    built = Settings.in_memory(keys=RING, today=lambda: TODAY)
    with fresh_client(built) as opened:
        reply = opened.get("/health")
    assert reply.status_code == status.HTTP_200_OK
    assert len(reply.headers[REQUEST_ID_HEADER]) == 12
