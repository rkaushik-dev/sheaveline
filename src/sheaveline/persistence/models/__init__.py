"""Every table in the system, imported so the metadata knows about all of them.

The counter is in here for exactly that reason. It lives with the numbering
rather than with the board tables, and a table nothing imports is a table
create_all does not build.
"""

from sheaveline.persistence.models.board import (
    CertificateClassRow,
    CertificateRow,
    DrillRow,
    DrillTeamRow,
    FilingRow,
    IncidentRow,
)
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
from sheaveline.persistence.models.weather import (
    ObservationRow,
    StationRow,
)
from sheaveline.persistence.numbering import FilingCounterRow

__all__ = [
    "AssignmentRow",
    "BrakeRow",
    "BreakObservationRow",
    "BreakSurveyRow",
    "CarrierRow",
    "CertificateClassRow",
    "CertificateRow",
    "DayLogRow",
    "DefectRow",
    "DiameterReadingRow",
    "DrillRow",
    "DrillTeamRow",
    "FilingCounterRow",
    "FilingRow",
    "HaulRopeRow",
    "HoldRow",
    "IncidentRow",
    "IndicationRow",
    "InspectionRecordRow",
    "LayReadingRow",
    "LiftRow",
    "LogEntryRow",
    "MagneticTestRow",
    "MeterReadingRow",
    "ObservationRow",
    "PreOpResultRow",
    "RopeSpecRow",
    "SpliceRow",
    "StationRow",
    "StopRow",
    "SurveyShotRow",
    "TensionReadingRow",
    "TensionSystemRow",
    "TerminalRow",
    "TowerRow",
    "WearSurveyRow",
    "WorkOrderRow",
]
