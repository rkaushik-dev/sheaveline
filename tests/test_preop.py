from datetime import date

import pytest

from hill import john_paul, strawberry, wildcat
from sheaveline.ops.preop import (
    STANDARD_SHEET,
    CheckItem,
    CheckOutcome,
    CheckResult,
    OperationsError,
    PreOpSheet,
    brake_items,
)

DAY = date(2025, 1, 15)


def item(code):
    for candidate in STANDARD_SHEET:
        if candidate.code == code:
            return candidate
    raise AssertionError(f"no item {code}")


def result(code, outcome=CheckOutcome.PASS, note="", by="M Vetter"):
    return CheckResult(item(code), outcome, by, note)


def sheet(lift=None, results=None, mechanic="M Vetter", operator="K Ostler"):
    machine = lift or john_paul()
    if results is None:
        applicable = [i for i in STANDARD_SHEET if i.applies_to(machine)]
        results = [CheckResult(i, CheckOutcome.PASS, mechanic) for i in applicable]
    return PreOpSheet.of(machine, DAY, results, mechanic, operator)


def test_the_sheet_a_machine_gets_depends_on_what_it_is():
    quad = sheet(john_paul())
    triple = sheet(strawberry())
    tow = sheet(wildcat())
    assert "C1" in {i.code for i in quad.items()}
    assert "C1" not in {i.code for i in triple.items()}
    assert "A2" in {i.code for i in triple.items()}
    assert "A2" not in {i.code for i in tow.items()}
    assert "D3" not in {i.code for i in tow.items()}


def test_every_machine_still_gets_the_common_lines():
    for machine in (john_paul(), strawberry(), wildcat()):
        codes = {i.code for i in sheet(machine).items()}
        assert {"A1", "B1", "B2", "D1", "D2"} <= codes


def test_a_walked_sheet_with_everything_passing_opens_the_lift():
    walked = sheet()
    assert walked.is_complete()
    assert walked.may_open()
    assert walked.blockers() == ()
    assert str(walked) == "JP 2025-01-15, clear to open"


def test_an_unwalked_line_stops_the_lift_opening():
    machine = john_paul()
    partial = [
        CheckResult(i, CheckOutcome.PASS, "M Vetter")
        for i in STANDARD_SHEET
        if i.applies_to(machine) and i.code != "B2"
    ]
    walked = sheet(machine, partial)
    assert not walked.is_complete()
    assert [i.code for i in walked.missing()] == ["B2"]
    assert not walked.may_open()
    assert walked.blockers() == ("B2 not walked",)


def test_a_critical_failure_stops_the_lift():
    machine = john_paul()
    results = [
        CheckResult(
            i,
            CheckOutcome.FAIL if i.code == "B2" else CheckOutcome.PASS,
            "M Vetter",
            "rope out of the train at T4" if i.code == "B2" else "",
        )
        for i in STANDARD_SHEET
        if i.applies_to(machine)
    ]
    walked = sheet(machine, results)
    assert walked.is_complete()
    assert not walked.may_open()
    assert [r.item.code for r in walked.critical_failures()] == ["B2"]


def test_a_failure_on_something_that_is_not_critical_does_not_stop_it():
    machine = john_paul()
    results = [
        CheckResult(
            i,
            CheckOutcome.FAIL if i.code == "E2" else CheckOutcome.PASS,
            "M Vetter",
            "maze sign blown over" if i.code == "E2" else "",
        )
        for i in STANDARD_SHEET
        if i.applies_to(machine)
    ]
    walked = sheet(machine, results)
    assert walked.failures()
    assert walked.critical_failures() == ()
    assert walked.may_open()


def test_a_non_critical_line_can_be_deferred():
    deferred = result("E1", CheckOutcome.DEFERRED, "cat is on it, will groom at ten")
    assert deferred.outcome is CheckOutcome.DEFERRED
    assert not deferred.outcome.is_satisfied


def test_a_critical_line_cannot_be_deferred():
    with pytest.raises(OperationsError):
        result("B1", CheckOutcome.DEFERRED, "will ride it later")


def test_anything_that_did_not_pass_carries_a_note():
    with pytest.raises(OperationsError):
        result("E2", CheckOutcome.FAIL)
    with pytest.raises(OperationsError):
        result("E1", CheckOutcome.DEFERRED)


def test_a_result_is_signed():
    with pytest.raises(OperationsError):
        result("A1", by="  ")


def test_a_sheet_carries_a_mechanic_and_an_operator():
    with pytest.raises(OperationsError):
        sheet(mechanic="  ")
    with pytest.raises(OperationsError):
        sheet(operator="")


def test_an_item_is_walked_once():
    with pytest.raises(OperationsError):
        sheet(john_paul(), [result("A1"), result("A1")])


def test_an_item_that_is_not_on_this_machine_is_refused():
    with pytest.raises(OperationsError):
        sheet(strawberry(), [result("C1")])


def test_a_result_can_be_looked_up_by_code():
    walked = sheet()
    found = walked.result_for("B1")
    assert found is not None
    assert found.item.code == "B1"
    assert walked.result_for("Z9") is None


def test_the_deferrals_are_listed_for_the_work_order_book():
    machine = john_paul()
    results = [
        CheckResult(
            i,
            CheckOutcome.DEFERRED if i.code == "E3" else CheckOutcome.PASS,
            "M Vetter",
            "kit is being restocked" if i.code == "E3" else "",
        )
        for i in STANDARD_SHEET
        if i.applies_to(machine)
    ]
    walked = sheet(machine, results)
    assert [r.item.code for r in walked.deferrals()] == ["E3"]
    assert walked.may_open()


def test_the_brake_lines_can_be_pulled_out_on_their_own():
    codes = {i.code for i in brake_items()}
    assert codes == {"A1", "A2", "A3"}


def test_an_item_prints_its_code_and_what_it_is():
    assert str(item("B1")) == "B1 line ridden top to bottom"
    assert str(result("B1")) == "B1 pass"
    assert str(result("E2", CheckOutcome.FAIL, "blown over")) == "E2 fail (blown over)"


def test_a_custom_item_can_be_written_for_one_class():
    special = CheckItem("Z1", "tram car doors", True, frozenset({john_paul().lift_class}))
    assert special.applies_to(john_paul())
    assert not special.applies_to(strawberry())
