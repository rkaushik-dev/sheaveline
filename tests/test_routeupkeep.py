import pytest
from fastapi import status

from hill import john_paul, strawberry
from sheaveline.api.security import Role
from sheaveline.persistence.uow import UnitOfWork
from wire import client_for, headers, settings


@pytest.fixture
def client():
    built = settings()
    with UnitOfWork.on(built.engine) as work:
        work.lifts.add(john_paul())
        work.lifts.add(strawberry())
        work.commit()
    with client_for(built) as opened:
        yield opened


def inspection(code="W1", done="2025-01-15", defects=None):
    return {"code": code, "done": done, "by": "M Vetter", "defects": defects or []}


def defect(level="defect", where="JP-T4", detail="sheave liner cracked"):
    return {"where": where, "level": level, "detail": detail}


def order_body(level="defect", raised="2025-01-15"):
    return {
        "where": "JP-T4",
        "detail": "sheave liner cracked",
        "level": level,
        "raised": raised,
        "raised_by": "M Vetter",
    }


def test_a_mechanic_signs_off_a_clean_walk(client):
    reply = client.post("/lifts/JP/inspections", json=inspection(), headers=headers(Role.MECHANIC))
    assert reply.status_code == status.HTTP_201_CREATED
    body = reply.json()
    assert body["code"] == "W1"
    assert body["clean"] is True
    assert body["defects"] == []


def test_a_walk_that_found_something_comes_back_with_it(client):
    body = client.post(
        "/lifts/JP/inspections",
        json=inspection(defects=[defect()]),
        headers=headers(Role.MECHANIC),
    ).json()
    assert body["clean"] is False
    assert body["defects"][0]["where"] == "JP-T4"
    assert body["defects"][0]["open"] is True


def test_a_job_that_runs_on_the_meter_needs_a_reading(client):
    reply = client.post(
        "/lifts/JP/inspections", json=inspection(code="H1"), headers=headers(Role.MECHANIC)
    )
    assert reply.status_code == status.HTTP_409_CONFLICT
    assert reply.json()["kind"] == "UpkeepError"


def test_a_job_that_is_not_in_the_library_is_refused(client):
    reply = client.post(
        "/lifts/JP/inspections", json=inspection(code="Z9"), headers=headers(Role.MECHANIC)
    )
    assert reply.status_code == status.HTTP_409_CONFLICT


def test_a_reader_key_may_not_sign_off_a_walk(client):
    reply = client.post("/lifts/JP/inspections", json=inspection(), headers=headers(Role.READER))
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_the_records_on_one_machine_come_back_in_order(client):
    for day in ("2025-01-08", "2025-01-15"):
        client.post(
            "/lifts/JP/inspections",
            json=inspection(done=day),
            headers=headers(Role.MECHANIC),
        )
    client.post("/lifts/JP/inspections", json=inspection(code="M1"), headers=headers(Role.MECHANIC))
    body = client.get("/lifts/JP/inspections", headers=headers()).json()
    assert len(body) == 3
    assert [one["done"] for one in body][:2] == ["2025-01-08", "2025-01-15"]


def test_the_records_can_be_narrowed_to_one_job(client):
    client.post("/lifts/JP/inspections", json=inspection(), headers=headers(Role.MECHANIC))
    client.post("/lifts/JP/inspections", json=inspection(code="M1"), headers=headers(Role.MECHANIC))
    body = client.get("/lifts/JP/inspections?job=m1", headers=headers()).json()
    assert [one["code"] for one in body] == ["M1"]


def test_the_open_defects_on_a_machine_come_back(client):
    client.post(
        "/lifts/JP/inspections",
        json=inspection(defects=[defect(), defect(where="JP-T7", detail="bolt loose")]),
        headers=headers(Role.MECHANIC),
    )
    body = client.get("/lifts/JP/defects", headers=headers()).json()
    assert {one["where"] for one in body} == {"JP-T4", "JP-T7"}
    assert all(one["open"] for one in body)


def test_defects_stay_with_the_machine_they_were_found_on(client):
    client.post(
        "/lifts/JP/inspections",
        json=inspection(defects=[defect()]),
        headers=headers(Role.MECHANIC),
    )
    assert client.get("/lifts/SB/defects", headers=headers()).json() == []


def test_an_order_takes_its_priority_from_the_defect_and_not_the_request(client):
    body = client.post(
        "/lifts/JP/work-orders",
        json=order_body(level="serious"),
        headers=headers(Role.MECHANIC),
    ).json()
    assert body["priority"] == "immediate"
    assert body["due_by"] == "2025-01-15"
    assert body["holds_the_machine"] is True


def test_an_ordinary_defect_gets_a_month(client):
    body = client.post(
        "/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC)
    ).json()
    assert body["priority"] == "scheduled"
    assert body["due_by"] == "2025-02-14"
    assert body["holds_the_machine"] is False


def test_orders_are_numbered_from_one_in_sequence(client):
    numbers = [
        client.post(
            "/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC)
        ).json()["number"]
        for _ in range(3)
    ]
    assert numbers == [1, 2, 3]


def test_the_open_orders_can_be_narrowed_by_machine_and_by_priority(client):
    client.post("/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC))
    client.post(
        "/lifts/SB/work-orders",
        json={**order_body(level="serious"), "where": "SB-T2"},
        headers=headers(Role.MECHANIC),
    )
    assert len(client.get("/work-orders", headers=headers()).json()) == 2
    assert len(client.get("/work-orders?lift=JP", headers=headers()).json()) == 1
    urgent = client.get("/work-orders?priority=immediate", headers=headers()).json()
    assert [one["lift"] for one in urgent] == ["SB"]


def test_one_order_comes_back_by_number(client):
    client.post("/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC))
    body = client.get("/work-orders/1", headers=headers()).json()
    assert body["detail"] == "sheave liner cracked"
    assert client.get("/work-orders/99", headers=headers()).status_code == 404


def test_a_manager_closes_an_order(client):
    client.post("/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC))
    reply = client.post(
        "/work-orders/1/closure",
        json={"closed": "2025-01-20", "closed_by": "M Vetter"},
        headers=headers(Role.MANAGER),
    )
    assert reply.status_code == status.HTTP_200_OK
    assert reply.json()["open"] is False
    assert client.get("/work-orders", headers=headers()).json() == []


def test_a_mechanic_key_may_not_close_an_order(client):
    client.post("/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC))
    reply = client.post(
        "/work-orders/1/closure",
        json={"closed": "2025-01-20", "closed_by": "M Vetter"},
        headers=headers(Role.MECHANIC),
    )
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_closing_an_order_that_is_not_there(client):
    reply = client.post(
        "/work-orders/99/closure",
        json={"closed": "2025-01-20", "closed_by": "M Vetter"},
        headers=headers(Role.MANAGER),
    )
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_the_overdue_list_reads_the_day_the_service_thinks_it_is(client):
    client.post(
        "/lifts/JP/work-orders",
        json=order_body(level="serious", raised="2025-01-10"),
        headers=headers(Role.MECHANIC),
    )
    client.post("/lifts/JP/work-orders", json=order_body(), headers=headers(Role.MECHANIC))
    body = client.get("/work-orders/overdue", headers=headers()).json()
    assert [one["priority"] for one in body] == ["immediate"]


def test_an_order_against_a_machine_that_is_not_there(client):
    reply = client.post("/lifts/ZZ/work-orders", json=order_body(), headers=headers(Role.MECHANIC))
    assert reply.status_code == status.HTTP_404_NOT_FOUND
