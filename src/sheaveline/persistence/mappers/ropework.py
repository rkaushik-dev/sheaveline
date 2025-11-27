"""Turning the rope file into rows and back again.

Every loader here takes the rope as an argument rather than reading it back
through the row's relationship. That is not laziness, it is the point: a survey
row knows a serial, and the caller is the one that decides which version of that
rope it is talking about, because a rope that has been shortened twice has three
lengths in its history and the survey does not say which one it was taken
against.
"""

from __future__ import annotations

from datetime import datetime, time

from sheaveline.persistence.base import StorageError
from sheaveline.persistence.models.ropework import (
    BreakObservationRow,
    BreakSurveyRow,
    DiameterReadingRow,
    IndicationRow,
    LayReadingRow,
    MagneticTestRow,
    SpliceRow,
    TensionReadingRow,
    TensionSystemRow,
    WearSurveyRow,
)
from sheaveline.plant.identity import LiftCode
from sheaveline.plant.rope import HaulRope
from sheaveline.ropework.breaks import BreakObservation, BreakSurvey
from sheaveline.ropework.mrt import Indication, MagneticTest
from sheaveline.ropework.splice import Splice, SpliceRegister
from sheaveline.ropework.tension import TensionReading, TensionSystem
from sheaveline.ropework.wear import DiameterReading, LayReading, WearSurvey


def wear_to_row(survey: WearSurvey) -> WearSurveyRow:
    row = WearSurveyRow(
        rope_serial=survey.rope.serial,
        taken=survey.taken,
        inspector=survey.inspector,
    )
    for reading in survey.diameters:
        row.diameters.append(
            DiameterReadingRow(
                station=reading.station,
                across_crowns=reading.across_crowns,
                across_valleys=reading.across_valleys,
            )
        )
    for lay in survey.lays:
        row.lays.append(LayReadingRow(station=lay.station, measured=lay.measured))
    return row


def wear_from_row(row: WearSurveyRow, rope: HaulRope) -> WearSurvey:
    _same_rope(row.rope_serial, rope)
    return WearSurvey.of(
        rope=rope,
        taken=row.taken,
        inspector=row.inspector,
        diameters=[
            DiameterReading(one.station, one.across_crowns, one.across_valleys)
            for one in sorted(row.diameters, key=lambda one: one.station.mils)
        ],
        lays=[
            LayReading(one.station, one.measured)
            for one in sorted(row.lays, key=lambda one: one.station.mils)
        ],
    )


def breaks_to_row(survey: BreakSurvey) -> BreakSurveyRow:
    row = BreakSurveyRow(
        rope_serial=survey.rope.serial,
        taken=survey.taken,
        inspector=survey.inspector,
    )
    for seen in survey.observations:
        row.observations.append(
            BreakObservationRow(
                station=seen.station,
                in_one_lay=seen.in_one_lay,
                in_six_lays=seen.in_six_lays,
                valley_breaks=seen.valley_breaks,
                at_splice=seen.at_splice,
            )
        )
    return row


def breaks_from_row(row: BreakSurveyRow, rope: HaulRope) -> BreakSurvey:
    _same_rope(row.rope_serial, rope)
    return BreakSurvey.of(
        rope=rope,
        taken=row.taken,
        inspector=row.inspector,
        observations=[
            BreakObservation(
                station=one.station,
                in_one_lay=one.in_one_lay,
                in_six_lays=one.in_six_lays,
                valley_breaks=one.valley_breaks,
                at_splice=one.at_splice,
            )
            for one in sorted(row.observations, key=lambda one: one.station.mils)
        ],
    )


def magnetic_to_row(test: MagneticTest) -> MagneticTestRow:
    row = MagneticTestRow(
        rope_serial=test.rope.serial,
        taken=test.taken,
        contractor=test.contractor,
        instrument=test.instrument,
    )
    for mark in test.indications:
        row.indications.append(
            IndicationRow(station=mark.station, kind=mark.kind, magnitude=mark.magnitude)
        )
    return row


def magnetic_from_row(row: MagneticTestRow, rope: HaulRope) -> MagneticTest:
    _same_rope(row.rope_serial, rope)
    return MagneticTest.of(
        rope=rope,
        taken=row.taken,
        contractor=row.contractor,
        instrument=row.instrument,
        indications=[
            Indication(one.station, one.kind, one.magnitude)
            for one in sorted(row.indications, key=lambda one: one.station.mils)
        ],
    )


def splices_to_rows(register: SpliceRegister) -> list[SpliceRow]:
    return [
        SpliceRow(
            rope_serial=register.rope.serial,
            station=splice.station,
            made=splice.made,
            made_by=splice.made_by,
            tucks=splice.tucks,
            length=splice.length,
            mill=splice.mill,
        )
        for splice in register.splices
    ]


def splices_from_rows(rows: list[SpliceRow], rope: HaulRope) -> SpliceRegister:
    for row in rows:
        _same_rope(row.rope_serial, rope)
    return SpliceRegister.of(
        rope,
        [
            Splice(
                station=row.station,
                made=row.made,
                made_by=row.made_by,
                tucks=row.tucks,
                length=row.length,
                mill=row.mill,
            )
            for row in rows
        ],
    )


def tension_to_row(system: TensionSystem, lift_code: LiftCode) -> TensionSystemRow:
    return TensionSystemRow(
        lift_code=lift_code,
        kind=system.kind,
        nominal=system.nominal,
        travel=system.travel,
        carriage=system.carriage,
    )


def tension_from_row(row: TensionSystemRow) -> TensionSystem:
    return TensionSystem(
        kind=row.kind,
        nominal=row.nominal,
        travel=row.travel,
        carriage=row.carriage,
    )


def reading_to_row(reading: TensionReading, lift_code: LiftCode) -> TensionReadingRow:
    return TensionReadingRow(
        lift_code=lift_code,
        taken=datetime.combine(reading.taken, time()),
        carriage=reading.carriage,
        measured=reading.measured,
        temperature=reading.temperature,
    )


def reading_from_row(row: TensionReadingRow) -> TensionReading:
    return TensionReading(
        taken=row.taken.date(),
        carriage=row.carriage,
        measured=row.measured,
        temperature=row.temperature,
    )


def _same_rope(serial: object, rope: HaulRope) -> None:
    if serial != rope.serial:
        raise StorageError(f"that row is against {serial}, not {rope.serial}")
