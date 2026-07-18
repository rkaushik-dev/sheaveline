from datetime import date, timedelta
from decimal import Decimal

import pytest
from fastapi import status

from hill import john_paul
from sheaveline.api.paging import DEFAULT_LIMIT, MOST_AT_ONCE, Paged, Paging
from sheaveline.api.security import Role
from sheaveline.ops.meters import MeterReading
from sheaveline.persistence.uow import UnitOfWork
from wire import client_for, headers, settings

ON = date(2025, 1, 15)
LETTERS = [chr(one) for one in range(ord("a"), ord("a") + 10)]


def test_a_page_hands_back_the_slice_it_was_asked_for():
    made = Paged.of(LETTERS, Paging(limit=3, offset=0))
    assert made.items == ["a", "b", "c"]
    assert made.total == 10
    assert made.next_offset == 3
    assert not made.is_last()
    assert len(made) == 3


def test_the_last_page_says_it_is_the_last():
    made = Paged.of(LETTERS, Paging(limit=3, offset=9))
    assert made.items == ["j"]
    assert made.next_offset is None
    assert made.is_last()


def test_a_page_past_the_end_is_empty_rather_than_an_error():
    made = Paged.of(LETTERS, Paging(limit=3, offset=99))
    assert made.items == []
    assert made.total == 10
    assert made.is_last()


def test_a_limit_bigger_than_the_list_takes_all_of_it():
    made = Paged.of(LETTERS, Paging(limit=100))
    assert made.items == LETTERS
    assert made.is_last()


def test_an_empty_list_pages_to_nothing():
    made = Paged.of([], Paging())
    assert made.items == []
    assert made.total == 0
    assert made.is_last()


def test_walking_the_pages_visits_everything_exactly_once():
    seen = []
    offset = 0
    while offset is not None:
        page = Paged.of(LETTERS, Paging(limit=4, offset=offset))
        seen.extend(page.items)
        offset = page.next_offset
    assert seen == LETTERS


@pytest.fixture
def client():
    built = settings()
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        for offset in range(12):
            work.day_logs.read_meter(
                MeterReading(
                    john_paul().code,
                    ON + timedelta(days=offset),
                    Decimal(40000 + offset),
                    1_400_000 + offset,
                    "R Skaggs",
                )
            )
        work.commit()
    with client_for(built) as opened:
        yield opened


def test_a_list_route_hands_back_a_page_with_a_total_on_it(client):
    body = client.get("/lifts/JP/meters", headers=headers(Role.MECHANIC)).json()
    assert body["total"] == 12
    assert body["limit"] == DEFAULT_LIMIT
    assert body["offset"] == 0
    assert body["next_offset"] is None
    assert len(body["items"]) == 12


def test_a_smaller_limit_cuts_the_page_and_names_the_next_offset(client):
    body = client.get("/lifts/JP/meters?limit=5", headers=headers(Role.MECHANIC)).json()
    assert len(body["items"]) == 5
    assert body["total"] == 12
    assert body["next_offset"] == 5


def test_the_offset_moves_the_window(client):
    first = client.get("/lifts/JP/meters?limit=5", headers=headers(Role.MECHANIC)).json()
    second = client.get("/lifts/JP/meters?limit=5&offset=5", headers=headers(Role.MECHANIC)).json()
    assert first["items"][0] != second["items"][0]
    assert second["offset"] == 5
    assert second["next_offset"] == 10


def test_a_limit_nobody_should_be_asking_for(client):
    too_many = client.get(
        f"/lifts/JP/meters?limit={MOST_AT_ONCE + 1}", headers=headers(Role.MECHANIC)
    )
    assert too_many.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY
    none_at_all = client.get("/lifts/JP/meters?limit=0", headers=headers(Role.MECHANIC))
    assert none_at_all.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


def test_an_offset_before_the_start(client):
    reply = client.get("/lifts/JP/meters?offset=-1", headers=headers(Role.MECHANIC))
    assert reply.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


def test_the_days_list_is_paged_the_same_way(client):
    body = client.get("/lifts/JP/days?limit=2", headers=headers()).json()
    assert body["total"] == 0
    assert body["items"] == []


def test_the_meter_readings_survive_the_round_trip_through_a_page(client):
    body = client.get("/lifts/JP/meters?limit=1", headers=headers(Role.MECHANIC)).json()
    one = body["items"][0]
    assert one["lift"] == "JP"
    assert one["day"] == ON.isoformat()
    assert one["cycles"] == 1_400_000
    assert one["read_by"] == "R Skaggs"
