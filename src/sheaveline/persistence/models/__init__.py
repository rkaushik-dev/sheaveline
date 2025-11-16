"""Every table in the system, imported so the metadata knows about all of them."""

from sheaveline.persistence.models.ops import (
    AssignmentRow,
    DayLogRow,
    HoldRow,
    LogEntryRow,
    MeterReadingRow,
    PreOpResultRow,
    StopRow,
)
from sheaveline.persistence.models.plant import (
    BrakeRow,
    CarrierRow,
    HaulRopeRow,
    LiftRow,
    RopeSpecRow,
    SurveyShotRow,
    TerminalRow,
    TowerRow,
)
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
from sheaveline.persistence.models.upkeep import (
    DefectRow,
    InspectionRecordRow,
    WorkOrderRow,
)

__all__ = [
    "AssignmentRow",
    "BrakeRow",
    "BreakObservationRow",
    "BreakSurveyRow",
    "CarrierRow",
    "DayLogRow",
    "DefectRow",
    "DiameterReadingRow",
    "HaulRopeRow",
    "HoldRow",
    "IndicationRow",
    "InspectionRecordRow",
    "LayReadingRow",
    "LiftRow",
    "LogEntryRow",
    "MagneticTestRow",
    "MeterReadingRow",
    "PreOpResultRow",
    "RopeSpecRow",
    "SpliceRow",
    "StopRow",
    "SurveyShotRow",
    "TensionReadingRow",
    "TensionSystemRow",
    "TerminalRow",
    "TowerRow",
    "WearSurveyRow",
    "WorkOrderRow",
]
