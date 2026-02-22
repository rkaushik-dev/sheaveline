from datetime import date

from hill import JOHN_PAUL, STRAWBERRY, WILDCAT
from sheaveline.reports.forms import PAGE_WIDTH
from sheaveline.reports.morning import MachineLine, Morning
from sheaveline.upkeep.status import ServiceStatus
from sheaveline.upkeep.workorder import Priority, WorkOrder
from sheaveline.weather.hold import Hold, HoldReason

DAY = date(2025, 1, 15)


def order(number=1, lift=JOHN_PAUL, priority=Priority.IMMEDIATE):
    return WorkOrder(
        number=number,
        lift=lift,
        raised=DAY,
        raised_by="M Vetter",
        detail="crossarm bolt sheared",
        priority=priority,
        where="JP-T7",
    )


def machine(
    lift=JOHN_PAUL,
    name="John Paul",
    status=ServiceStatus.IN_SERVICE,
    walked=True,
    blockers=(),
    holds=(),
    orders=(),
    due=(),
):
    return MachineLine(
        lift=lift,
        name=name,
        status=status,
        sheet_walked=walked,
        blockers=tuple(blockers),
        holds=tuple(holds),
        orders=tuple(orders),
        upkeep_due=tuple(due),
    )


def morning(*machines, snow="46 in"):
    return Morning.of(DAY, "Ogden Peak", snow, machines or [machine()])


def test_a_machine_with_nothing_against_it_is_opening():
    one = machine()
    assert one.opening()
    assert one.verdict() == "opening"
    assert one.reasons() == ()


def test_an_unsigned_morning_walk_stops_it():
    one = machine(walked=False)
    assert not one.opening()
    assert one.verdict() == "not opening"
    assert "morning walk not signed" in one.reasons()


def test_a_blocker_stops_it_and_is_named():
    one = machine(blockers=("B2 failed",))
    assert not one.opening()
    assert "B2 failed" in one.reasons()


def test_a_weather_hold_is_told_apart_from_a_blocker():
    held = machine(holds=(Hold(HoldReason.GUST, "52 mph against a limit of 45 mph"),))
    assert held.verdict() == "held"
    assert "gust hold" in held.reasons()
    both = machine(holds=(Hold(HoldReason.GUST, "52 mph"),), blockers=("B2 failed",))
    assert both.verdict() == "not opening"


def test_a_machine_out_of_service_is_not_opening_whatever_else_is_true():
    one = machine(status=ServiceStatus.OUT_OF_SERVICE)
    assert not one.opening()
    assert "out of service" in one.reasons()


def test_a_restricted_machine_still_opens():
    one = machine(status=ServiceStatus.RESTRICTED)
    assert one.opening()


def test_the_report_counts_what_is_opening():
    page = morning(
        machine(lift=JOHN_PAUL),
        machine(lift=STRAWBERRY, name="Strawberry", walked=False),
        machine(lift=WILDCAT, name="Wildcat"),
    )
    assert len(page.opening()) == 2
    assert len(page.held()) == 1
    text = page.text()
    assert "machines opening         2 of 3" in text


def test_the_page_heads_with_the_resort_and_the_day():
    text = morning().text()
    assert "OGDEN PEAK MORNING REPORT" in text
    assert "day                      2025-01-15" in text
    assert "settled snow             46 in" in text


def test_every_machine_gets_a_row():
    page = morning(
        machine(lift=JOHN_PAUL),
        machine(lift=STRAWBERRY, name="Strawberry"),
    )
    text = page.text()
    assert "JP    John Paul" in text
    assert "SB    Strawberry" in text


def test_the_reasons_are_printed_under_the_machine_they_belong_to():
    text = morning(
        machine(lift=JOHN_PAUL, blockers=("B2 failed", "D1 not walked")),
        machine(lift=STRAWBERRY, name="Strawberry"),
    ).text()
    assert "what is stopping them" in text
    assert "JP John Paul" in text
    assert "    B2 failed" in text
    assert "    D1 not walked" in text


def test_a_clean_morning_prints_no_stopping_section():
    assert "what is stopping them" not in morning().text()


def test_an_order_that_stops_a_machine_gets_its_own_section():
    text = morning(machine(orders=(order(),))).text()
    assert "work orders that stop a machine" in text
    assert "crossarm bolt sheared" in text


def test_an_ordinary_order_does_not_reach_that_section():
    text = morning(machine(orders=(order(priority=Priority.SCHEDULED),))).text()
    assert "work orders that stop a machine" not in text


def test_the_jobs_falling_due_are_listed_with_their_machine():
    text = morning(machine(due=("A1", "H1"))).text()
    assert "falling due" in text
    assert "JP    A1" in text
    assert "JP    H1" in text


def test_nothing_falling_due_prints_no_section():
    assert "falling due" not in morning().text()


def test_the_whole_page_fits_eighty_columns():
    page = morning(
        machine(lift=JOHN_PAUL, blockers=("B2 failed",), orders=(order(),), due=("A1",)),
        machine(lift=STRAWBERRY, name="Strawberry", status=ServiceStatus.RESTRICTED),
        machine(lift=WILDCAT, name="Wildcat", holds=(Hold(HoldReason.CHILL, "-31 F"),)),
    )
    assert all(len(one) <= PAGE_WIDTH for one in page.render().lines())


def test_the_page_says_what_it_was_printed_for():
    assert morning().text().rstrip().endswith("printed for Ogden Peak, 2025-01-15")


def test_the_urgent_orders_are_gathered_across_every_machine():
    page = morning(
        machine(lift=JOHN_PAUL, orders=(order(number=1),)),
        machine(lift=STRAWBERRY, name="Strawberry", orders=(order(number=2, lift=STRAWBERRY),)),
    )
    assert [one.number for one in page.urgent_orders()] == [1, 2]
