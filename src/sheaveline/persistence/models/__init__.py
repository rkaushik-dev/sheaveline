"""Every table in the system, imported so the metadata knows about all of them."""

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

__all__ = [
    "BrakeRow",
    "BreakObservationRow",
    "BreakSurveyRow",
    "CarrierRow",
    "DiameterReadingRow",
    "HaulRopeRow",
    "IndicationRow",
    "LayReadingRow",
    "LiftRow",
    "MagneticTestRow",
    "RopeSpecRow",
    "SpliceRow",
    "SurveyShotRow",
    "TensionReadingRow",
    "TensionSystemRow",
    "TerminalRow",
    "TowerRow",
    "WearSurveyRow",
]
