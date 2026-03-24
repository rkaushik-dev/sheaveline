from decimal import Decimal

import pytest

from sheaveline.capacity.queue import Maze, Queue
from sheaveline.capacity.spacing import CapacityError, LineSpacing
from sheaveline.capacity.uphill import UphillCapacity
from sheaveline.measures.duration import Duration
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed


def capacity(carriers=100, seats=4, occupancy="0.85"):
    line = LineSpacing(
        loop=Length.from_feet(10000),
        carriers=carriers,
        speed=Speed.from_feet_per_minute(1000),
    )
    return UphillCapacity(line, seats, Decimal(occupancy))


def maze(lanes=6, people=40, singles=False):
    return Maze(lanes, people, singles)


def queue(arrivals=1500, **kwargs):
    return Queue(capacity(**kwargs), maze(), arrivals)


def test_a_maze_holds_its_lanes_times_its_depth():
    assert maze(lanes=6, people=40).holds() == 240
    with pytest.raises(CapacityError):
        maze(lanes=0)
    with pytest.raises(CapacityError):
        maze(people=0)


def test_a_singles_lane_is_a_property_of_the_maze():
    assert not maze().singles_lane
    assert maze().with_singles().singles_lane


def test_people_cannot_arrive_at_a_negative_rate():
    with pytest.raises(CapacityError):
        queue(arrivals=-1)


def test_the_throughput_is_the_capacity_at_the_loading_efficiency():
    assert queue().throughput() == capacity().persons_per_hour()


def test_a_singles_lane_lifts_the_throughput_without_touching_the_machine():
    plain = queue()
    singles = plain.with_maze(maze().with_singles())
    assert singles.throughput() > plain.throughput()
    assert singles.capacity.spacing.speed == plain.capacity.spacing.speed


def test_the_singles_lift_is_capped_at_a_full_chair():
    full = Queue(capacity(occupancy="0.95"), maze().with_singles(), 1000)
    assert full.occupancy() <= Decimal("0.98")


def test_a_machine_keeping_up_has_no_queue_at_all():
    quiet = queue(arrivals=1000)
    assert quiet.keeps_up()
    assert quiet.surplus_an_hour() == 0
    assert quiet.length_after(Duration.from_hours(3)) == 0
    assert quiet.wait_after(Duration.from_hours(3)) == Duration(0)


def test_a_machine_keeping_up_reports_its_spare():
    quiet = queue(arrivals=1000)
    assert quiet.spare_an_hour() == quiet.throughput() - 1000


def test_a_queue_grows_by_the_difference_every_hour():
    busy = queue(arrivals=2500)
    surplus = busy.surplus_an_hour()
    assert surplus > 0
    assert busy.length_after(Duration.from_hours(1)) == surplus
    assert busy.length_after(Duration.from_hours(2)) == surplus * 2


def test_the_wait_is_the_queue_over_the_throughput():
    busy = queue(arrivals=2500)
    standing = busy.length_after(Duration.from_hours(1))
    expected = Duration.from_seconds(int(Decimal(standing) * 3600 / busy.throughput()))
    assert busy.wait_after(Duration.from_hours(1)) == expected


def test_the_wait_grows_with_the_time_at_the_rate():
    busy = queue(arrivals=2500)
    assert busy.wait_after(Duration.from_hours(2)) > busy.wait_after(Duration.from_hours(1))


def test_a_singles_lane_shortens_the_wait():
    busy = queue(arrivals=2500)
    singles = busy.with_maze(maze().with_singles())
    assert singles.wait_after(Duration.from_hours(1)) < busy.wait_after(Duration.from_hours(1))


def test_the_queue_leaves_the_maze_eventually():
    busy = queue(arrivals=2500)
    assert not busy.overflows_after(Duration.from_minutes(10))
    assert busy.overflows_after(Duration.from_hours(4))


def test_the_maze_fills_at_a_time_that_can_be_worked_out():
    busy = queue(arrivals=2500)
    fills = busy.fills_in()
    assert fills is not None
    assert not busy.overflows_after(Duration.from_seconds(fills.seconds - 60))
    assert busy.overflows_after(Duration.from_seconds(fills.seconds + 60))


def test_a_machine_keeping_up_never_fills_its_maze():
    assert queue(arrivals=1000).fills_in() is None


def test_a_bigger_maze_takes_longer_to_fill():
    busy = queue(arrivals=2500)
    bigger = busy.with_maze(maze(lanes=12))
    small = busy.fills_in()
    large = bigger.fills_in()
    assert small is not None
    assert large is not None
    assert large > small


def test_clearing_a_queue_takes_the_queue_over_the_throughput():
    busy = queue(arrivals=2500)
    assert busy.clears_in(0) == Duration(0)
    cleared = busy.clears_in(busy.throughput())
    assert cleared == Duration.from_hours(1)
    with pytest.raises(CapacityError):
        busy.clears_in(-1)


def test_changing_the_arrivals_makes_a_new_queue():
    quiet = queue(arrivals=1000)
    busy = quiet.with_arrivals(3000)
    assert quiet.keeps_up()
    assert not busy.keeps_up()


def test_a_queue_prints_whether_it_is_keeping_up():
    assert "keeping up" in str(queue(arrivals=1000))
    assert "behind" in str(queue(arrivals=3000))


def test_a_line_with_almost_no_capacity_still_answers_rather_than_dividing_by_nothing():
    thin = Queue(capacity(carriers=1, seats=1, occupancy="0.01"), maze(), 500)
    assert thin.throughput() >= 0
    if thin.throughput() == 0:
        with pytest.raises(CapacityError):
            thin.clears_in(10)
