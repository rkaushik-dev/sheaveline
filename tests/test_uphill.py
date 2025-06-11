from datetime import date
from decimal import Decimal

import pytest

from hill import john_paul, strawberry
from sheaveline.capacity.spacing import CapacityError, LineSpacing
from sheaveline.capacity.uphill import CapacityCertificate, UphillCapacity
from sheaveline.measures.length import Length
from sheaveline.measures.speed import Speed
from sheaveline.plant.identity import FilingNumber

FILING = FilingNumber.parse("UT-2004-0311")


def capacity(loop_feet=10000, carriers=100, fpm=1000, seats=4, occupancy="0.85"):
    line = LineSpacing(
        loop=Length.from_feet(loop_feet),
        carriers=carriers,
        speed=Speed.from_feet_per_minute(fpm),
    )
    return UphillCapacity(line, seats, Decimal(occupancy))


def certificate(pph=2000, carriers=100, issued=date(2004, 12, 3)):
    return CapacityCertificate(
        filing=FILING,
        issued=issued,
        rated_persons_per_hour=pph,
        rated_carriers=carriers,
    )


def test_carriers_an_hour_falls_out_of_the_interval():
    # A hundred foot spacing at a thousand feet a minute is six seconds.
    assert capacity().spacing.interval().seconds == 6
    assert capacity().carriers_per_hour() == 600


def test_seats_an_hour_is_every_place_filled():
    assert capacity(seats=4).seats_per_hour() == 2400
    assert capacity(seats=6).seats_per_hour() == 3600


def test_persons_an_hour_takes_the_loading_efficiency_off():
    assert capacity(seats=4, occupancy="0.85").persons_per_hour() == 2040
    assert capacity(seats=4, occupancy="1.0").persons_per_hour() == 2400


def test_vertical_feet_an_hour_is_the_figure_that_compares_two_lifts():
    quick = capacity(seats=4)
    assert quick.vertical_feet_per_hour(Length.from_feet(1500)) == 3_060_000
    with pytest.raises(CapacityError):
        quick.vertical_feet_per_hour(Length.from_feet(0))


def test_a_capacity_checks_its_own_numbers():
    with pytest.raises(CapacityError):
        capacity(seats=0)
    with pytest.raises(CapacityError):
        capacity(occupancy="0")
    with pytest.raises(CapacityError):
        capacity(occupancy="1.2")


def test_a_capacity_built_from_a_lift_reads_its_carrier_type():
    built = UphillCapacity.of(john_paul())
    assert built.seats == 4
    assert built.persons_per_hour() > 1500
    assert UphillCapacity.of(strawberry()).seats == 3


def test_a_worse_loading_efficiency_moves_only_the_persons_figure():
    good = capacity(occupancy="0.90")
    poor = good.at_occupancy("0.60")
    assert poor.seats_per_hour() == good.seats_per_hour()
    assert poor.persons_per_hour() < good.persons_per_hour()


def test_thinning_the_line_costs_capacity():
    full = capacity(carriers=100)
    thinned = full.with_spacing(full.spacing.with_carriers(50))
    assert thinned.persons_per_hour() < full.persons_per_hour()


def test_the_carriers_needed_to_reach_a_target():
    line = capacity(carriers=100, seats=4, occupancy="0.85")
    assert line.persons_per_hour() == 2040
    assert line.carriers_for_persons_per_hour(2040) == 100
    assert line.carriers_for_persons_per_hour(1020) == 50
    assert line.carriers_for_persons_per_hour(1021) == 51


def test_a_target_of_nothing_has_no_answer():
    with pytest.raises(CapacityError):
        capacity().carriers_for_persons_per_hour(0)


def test_a_capacity_prints_both_figures():
    assert str(capacity()) == "2,040 people an hour, 2,400 seats"


def test_a_certificate_checks_its_own_numbers():
    with pytest.raises(CapacityError):
        certificate(pph=0)
    with pytest.raises(CapacityError):
        certificate(carriers=0)


def test_a_machine_running_at_its_filed_figure_agrees_with_the_certificate():
    filed = certificate(pph=2040)
    assert filed.agrees_with(capacity())
    assert not filed.exceeded_by(capacity())


def test_the_band_is_two_percent_either_side():
    assert certificate(pph=2000).band() == (1960, 2040)


def test_a_machine_running_above_the_band_is_a_matter_for_the_board():
    filed = certificate(pph=1800)
    assert not filed.agrees_with(capacity())
    assert filed.exceeded_by(capacity())


def test_a_machine_running_under_the_band_disagrees_without_exceeding():
    filed = certificate(pph=2600)
    assert not filed.agrees_with(capacity())
    assert not filed.exceeded_by(capacity())


def test_the_certificate_notices_carriers_added_since_it_was_issued():
    filed = certificate(carriers=84)
    assert filed.carriers_changed(capacity(carriers=100)) == 16
    assert filed.carriers_changed(capacity(carriers=70)) == -14


def test_a_certificate_reports_its_age():
    filed = certificate(issued=date(2004, 12, 3))
    assert filed.age_in_years(date(2025, 1, 15)) == 20
    assert filed.age_in_years(date(2024, 12, 2)) == 19
    with pytest.raises(CapacityError):
        filed.age_in_years(date(2000, 1, 1))


def test_a_certificate_prints_its_filing_number():
    assert str(certificate(pph=2040)) == "UT-2004-0311, 2,040 an hour"
