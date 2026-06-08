from datetime import date, datetime
from decimal import Decimal

import pytest
from fastapi import status

from hill import john_paul, strawberry
from sheaveline.api.security import Role
from sheaveline.ops.logbook import DayLog
from sheaveline.ops.meters import MeterReading
from sheaveline.ops.preop import STANDARD_SHEET, CheckOutcome, CheckResult, PreOpSheet
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.schedule.hours import OperatingWindow
from wire import client_for, headers, settings

ON = date(2025, 1, 15)
WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30")
STOPPED = datetime(2025, 1, 15, 11, 30).isoformat()


def sheet(machine, day=ON):
    return PreOpSheet.of(
        lift=machine,
        day=day,
        results=[
            CheckResult(item=item, outcome=CheckOutcome.PASS, by="R Skaggs")
            for item in STANDARD_SHEET
            if item.applies_to(machine)
        ],
        mechanic="R Skaggs",
        operator="D Chin",
    )


@pytest.fixture
def built():
    made = settings()
    with UnitOfWork.on(made.engine) as work:
        for machine in (john_paul(), strawberry()):
            work.lifts.add(machine)
        work.day_logs.add(DayLog.start(john_paul().code, ON, WINDOW, sheet=sheet(john_paul())))
        work.day_logs.read_meter(
            MeterReading(john_paul().code, ON, Decimal("40120.5"), 1_402_000, "R Skaggs")
        )
        work.commit()
    return made


@pytest.fixture
def client(built):
    with client_for(built) as opened:
        yield opened


def stop_body(kind="operator stop", note=""):
    return {"kind": kind, "stopped": STOPPED, "stopped_by": "D Chin", "note": note}


def restart_body(hour=11, minute=45, line_checked=False):
    return {
        "stopped": STOPPED,
        "restarted": datetime(2025, 1, 15, hour, minute).isoformat(),
        "restarted_by": "D Chin",
        "line_checked": line_checked,
    }


def test_one_day_comes_back_with_the_answers_worked_out(client):
    reply = client.get("/lifts/JP/days/2025-01-15", headers=headers())
    assert reply.status_code == status.HTTP_200_OK
    body = reply.json()
    assert body["sheet_walked"]
    assert body["may_open"]
    assert body["first_load"] == "9:00"
    assert body["scheduled"] == "7:30:00"
    assert body["meter"]["cycles"] == 1_402_000


def test_a_day_nobody_wrote_up(client):
    reply = client.get("/lifts/JP/days/2025-01-16", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_a_machine_that_is_not_on_the_register(client):
    reply = client.get("/lifts/ZZ/days/2025-01-15", headers=headers())
    assert reply.status_code == status.HTTP_404_NOT_FOUND
    assert reply.json()["detail"] == "there is no lift ZZ"


def test_the_days_a_machine_has_been_written_up(client):
    assert client.get("/lifts/JP/days", headers=headers()).json() == ["2025-01-15"]
    assert client.get("/lifts/SB/days", headers=headers()).json() == []


def test_a_stop_goes_in_and_says_what_follows_from_its_kind(client):
    reply = client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    assert reply.status_code == status.HTTP_201_CREATED
    body = reply.json()
    assert body["open"]
    assert body["counts_against_the_machine"]
    assert not body["reportable"]
    assert body["length"] is None


def test_a_power_cut_does_not_count_against_the_machine(client):
    reply = client.post(
        "/lifts/JP/stops", json=stop_body(kind="power"), headers=headers(Role.OPERATOR)
    )
    assert not reply.json()["counts_against_the_machine"]


def test_a_reportable_stop_will_not_go_in_without_a_write_up(client):
    reply = client.post(
        "/lifts/JP/stops", json=stop_body(kind="deropement"), headers=headers(Role.OPERATOR)
    )
    assert reply.status_code == status.HTTP_409_CONFLICT
    assert "written up" in reply.json()["detail"]


def test_a_reader_may_not_stop_a_machine(client):
    reply = client.post("/lifts/JP/stops", json=stop_body(), headers=headers())
    assert reply.status_code == status.HTTP_401_UNAUTHORIZED


def test_two_stops_cannot_start_at_the_same_moment(client):
    client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    again = client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    assert again.status_code == status.HTTP_404_NOT_FOUND
    assert "already stopped" in again.json()["detail"]


def test_a_restart_closes_the_stop_and_measures_it(client):
    client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    reply = client.post(
        "/lifts/JP/stops/restart", json=restart_body(), headers=headers(Role.OPERATOR)
    )
    assert reply.status_code == status.HTTP_200_OK
    assert reply.json()["length"] == "0:15:00"
    assert not reply.json()["open"]


def test_a_long_stop_will_not_restart_until_the_line_is_walked(client):
    client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    refused = client.post(
        "/lifts/JP/stops/restart",
        json=restart_body(hour=12, minute=30),
        headers=headers(Role.OPERATOR),
    )
    assert refused.status_code == status.HTTP_409_CONFLICT
    assert "line check" in refused.json()["detail"]
    walked = client.post(
        "/lifts/JP/stops/restart",
        json=restart_body(hour=12, minute=30, line_checked=True),
        headers=headers(Role.OPERATOR),
    )
    assert walked.status_code == status.HTTP_200_OK


def test_restarting_a_stop_nobody_called(client):
    reply = client.post(
        "/lifts/JP/stops/restart", json=restart_body(), headers=headers(Role.OPERATOR)
    )
    assert reply.status_code == status.HTTP_404_NOT_FOUND


def test_the_stops_on_a_day_are_totalled(client):
    client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    client.post("/lifts/JP/stops/restart", json=restart_body(), headers=headers(Role.OPERATOR))
    reply = client.get("/lifts/JP/days/2025-01-15/stops", headers=headers())
    assert reply.status_code == status.HTTP_200_OK
    assert reply.json()["downtime"] == "0:15:00"
    assert reply.json()["chargeable"] == "0:15:00"


def test_what_is_stopped_right_now(client):
    assert client.get("/stops/open", headers=headers()).json() == []
    client.post("/lifts/JP/stops", json=stop_body(), headers=headers(Role.OPERATOR))
    reply = client.get("/stops/open", headers=headers())
    assert [one["lift"] for one in reply.json()] == ["JP"]
    assert client.get("/stops/open?lift=SB", headers=headers()).json() == []


def test_the_meter_history_is_a_mechanics_key(client):
    plain = client.get("/lifts/JP/meters", headers=headers())
    assert plain.status_code == status.HTTP_401_UNAUTHORIZED
    reply = client.get("/lifts/JP/meters", headers=headers(Role.MECHANIC))
    assert reply.status_code == status.HTTP_200_OK
    assert reply.json()[0]["cycles"] == 1_402_000
    assert client.get("/lifts/SB/meters", headers=headers(Role.MECHANIC)).json() == []
