from datetime import date, timedelta
from decimal import Decimal

from hill import JOHN_PAUL, STRAWBERRY, the_season
from sheaveline.measures.duration import Duration
from sheaveline.ops.stop import StopKind
from sheaveline.reports.forms import PAGE_WIDTH
from sheaveline.reports.season import DayRecord, SeasonSummary

SEASON = the_season()


def record(
    lift=JOHN_PAUL,
    day=date(2025, 1, 15),
    opened=True,
    scheduled_hours=7,
    charged_minutes=0,
    down_minutes=0,
    stops=(),
):
    scheduled = Duration.from_hours(scheduled_hours)
    downtime = Duration.from_minutes(down_minutes or charged_minutes)
    return DayRecord(
        lift=lift,
        day=day,
        opened=opened,
        scheduled=scheduled,
        running=scheduled - downtime,
        downtime=downtime,
        chargeable=Duration.from_minutes(charged_minutes),
        stops=tuple(stops),
    )


def summary(*records):
    return SeasonSummary.of(SEASON, records or [record()])


def test_a_day_with_nothing_charged_is_fully_available():
    assert record().availability() == 1


def test_a_day_with_an_hour_charged_loses_a_seventh():
    day = record(scheduled_hours=7, charged_minutes=60)
    assert day.availability().quantize(Decimal("0.001")) == Decimal("0.857")


def test_a_day_that_was_never_scheduled_has_no_availability():
    assert (
        DayRecord(
            JOHN_PAUL,
            date(2025, 1, 15),
            opened=False,
            scheduled=Duration(0),
            running=Duration(0),
            downtime=Duration(0),
            chargeable=Duration(0),
        ).availability()
        == 0
    )


def test_the_machines_come_back_in_code_order():
    held = summary(record(lift=STRAWBERRY), record(lift=JOHN_PAUL))
    assert held.lifts() == (JOHN_PAUL, STRAWBERRY)


def test_the_days_a_machine_opened_and_did_not():
    held = summary(
        record(day=date(2025, 1, 15)),
        record(day=date(2025, 1, 16), opened=False, scheduled_hours=0),
        record(day=date(2025, 1, 17)),
    )
    assert held.days_open(JOHN_PAUL) == 2
    assert held.days_shut(JOHN_PAUL) == 1


def test_the_totals_add_up_across_the_season():
    held = summary(
        record(day=date(2025, 1, 15), charged_minutes=30),
        record(day=date(2025, 1, 16), charged_minutes=90),
    )
    assert held.scheduled(JOHN_PAUL) == Duration.from_hours(14)
    assert held.chargeable(JOHN_PAUL) == Duration.from_hours(2)
    assert held.downtime(JOHN_PAUL) == Duration.from_hours(2)
    assert held.running(JOHN_PAUL) == Duration.from_hours(12)


def test_availability_charges_only_the_machines_own_stops():
    held = summary(
        record(day=date(2025, 1, 15), down_minutes=120, charged_minutes=30),
    )
    assert held.downtime(JOHN_PAUL) == Duration.from_hours(2)
    assert held.chargeable(JOHN_PAUL) == Duration.from_minutes(30)
    assert held.availability(JOHN_PAUL).quantize(Decimal("0.001")) == Decimal("0.929")


def test_a_machine_with_nothing_scheduled_has_no_availability():
    held = summary(record(opened=False, scheduled_hours=0))
    assert held.availability(JOHN_PAUL) == 0


def test_the_worst_day_is_the_one_that_cost_the_most():
    held = summary(
        record(day=date(2025, 1, 15), charged_minutes=30),
        record(day=date(2025, 1, 16), charged_minutes=180),
        record(day=date(2025, 1, 17), charged_minutes=45),
    )
    worst = held.worst_day(JOHN_PAUL)
    assert worst is not None
    assert worst.day == date(2025, 1, 16)
    assert held.worst_day(STRAWBERRY) is None


def test_the_stops_are_counted_by_kind_a_machine():
    held = summary(
        record(stops=(StopKind.GATE, StopKind.GATE, StopKind.WEATHER)),
        record(day=date(2025, 1, 16), stops=(StopKind.GATE,)),
    )
    counted = held.stops_by_kind(JOHN_PAUL)
    assert counted[StopKind.GATE] == 3
    assert counted[StopKind.WEATHER] == 1
    assert held.stops_by_kind(STRAWBERRY) == {}


def test_the_hill_availability_reads_every_machine_at_once():
    held = summary(
        record(lift=JOHN_PAUL, charged_minutes=0),
        record(lift=STRAWBERRY, charged_minutes=420),
    )
    assert held.hill_availability() == Decimal("0.5")


def test_a_season_with_nothing_in_it_has_no_hill_availability():
    assert SeasonSummary.of(SEASON, []).hill_availability() == 0


def test_the_page_heads_with_the_season():
    text = summary().text()
    assert "SEASON SUMMARY 2024/25" in text
    assert "opened                   2024-11-29" in text
    assert "closed                   2025-04-20" in text


def test_every_machine_gets_a_row_on_the_page():
    text = summary(record(lift=JOHN_PAUL), record(lift=STRAWBERRY)).text()
    assert "by machine" in text
    assert "\nJP " in text
    assert "\nSB " in text


def test_the_worst_day_section_names_the_day():
    text = summary(record(day=date(2025, 1, 16), charged_minutes=180)).text()
    assert "worst day a machine" in text
    assert "2025-01-16" in text


def test_the_stop_table_has_a_column_a_machine():
    text = summary(
        record(lift=JOHN_PAUL, stops=(StopKind.GATE,)),
        record(lift=STRAWBERRY, stops=(StopKind.WEATHER,)),
    ).text()
    assert "stops by kind" in text
    assert "loading gate" in text
    assert "weather" in text


def test_the_whole_page_fits_eighty_columns():
    days = []
    for index in range(20):
        day = date(2025, 1, 15) + timedelta(days=index)
        days.append(record(lift=JOHN_PAUL, day=day, charged_minutes=index, stops=(StopKind.GATE,)))
        days.append(record(lift=STRAWBERRY, day=day, charged_minutes=index * 2))
    page = summary(*days)
    assert all(len(one) <= PAGE_WIDTH for one in page.render().lines())


def test_the_records_come_back_sorted_by_day_then_machine():
    held = summary(
        record(lift=STRAWBERRY, day=date(2025, 1, 16)),
        record(lift=JOHN_PAUL, day=date(2025, 1, 16)),
        record(lift=JOHN_PAUL, day=date(2025, 1, 15)),
    )
    assert [(one.day.day, one.lift.value) for one in held.records] == [
        (15, "JP"),
        (16, "JP"),
        (16, "SB"),
    ]


def test_every_machine_is_tallied_once_on_the_way_in():
    held = summary(
        record(lift=JOHN_PAUL, day=date(2025, 1, 15)),
        record(lift=JOHN_PAUL, day=date(2025, 1, 16)),
        record(lift=STRAWBERRY, day=date(2025, 1, 15)),
    )
    assert set(held.tallies) == {JOHN_PAUL, STRAWBERRY}
    assert len(held.tally(JOHN_PAUL).days) == 2
    assert len(held.tally(STRAWBERRY).days) == 1


def test_the_tally_holds_the_same_answers_the_summary_gives():
    held = summary(
        record(day=date(2025, 1, 15), scheduled_hours=7, charged_minutes=30),
        record(day=date(2025, 1, 16), scheduled_hours=8, opened=False),
    )
    one = held.tally(JOHN_PAUL)
    assert one.days_open == held.days_open(JOHN_PAUL)
    assert one.days_shut == held.days_shut(JOHN_PAUL)
    assert one.scheduled == held.scheduled(JOHN_PAUL)
    assert one.running == held.running(JOHN_PAUL)
    assert one.downtime == held.downtime(JOHN_PAUL)
    assert one.chargeable == held.chargeable(JOHN_PAUL)
    assert one.availability() == held.availability(JOHN_PAUL)
    assert one.worst_day() == held.worst_day(JOHN_PAUL)


def test_a_machine_with_no_days_tallies_to_nothing_rather_than_failing():
    held = summary(record(lift=JOHN_PAUL))
    empty = held.tally(STRAWBERRY)
    assert empty.days == ()
    assert empty.days_open == 0
    assert empty.scheduled.is_zero()
    assert empty.availability() == Decimal(0)
    assert empty.worst_day() is None


def test_the_stop_counts_are_added_up_once_and_handed_out_as_copies():
    held = summary(
        record(stops=[StopKind.WEATHER, StopKind.WEATHER]),
        record(day=date(2025, 1, 16), stops=[StopKind.WEATHER, StopKind.POWER]),
    )
    assert held.tally(JOHN_PAUL).stops == {StopKind.WEATHER: 3, StopKind.POWER: 1}
    handed = held.stops_by_kind(JOHN_PAUL)
    handed[StopKind.WEATHER] = 99
    assert held.tally(JOHN_PAUL).stops[StopKind.WEATHER] == 3


def test_the_records_are_only_walked_once_however_many_questions_are_asked():
    days = [
        record(lift=lift, day=date(2025, 1, 1) + timedelta(days=offset))
        for lift in (JOHN_PAUL, STRAWBERRY)
        for offset in range(40)
    ]
    walked = 0

    class Counted(tuple):  # type: ignore[type-arg]
        def __iter__(self):
            nonlocal walked
            walked += 1
            return super().__iter__()

    held = summary(*days)
    object.__setattr__(held, "records", Counted(held.records))
    held.render()
    assert walked == 0
