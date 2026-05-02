"""Reading one named thing out of storage, and saying so plainly when it is gone.

Six route modules were each carrying their own version of this. Three wrote a
private helper called _lift, two wrote one called _known_lift that differed only
in whether it handed back the machine or just its code, and the rest inlined the
try and the raise wherever they needed it. They did not all word the sentence the
same way, which is how a client ends up parsing our prose.

The lookup does the parse and the fetch together on purpose. A code that will not
parse and a code that parses but is not on the register are the same thing to
whoever is holding the tablet, and splitting them across a four hundred and a
four oh four only means two screens instead of one.
"""

from __future__ import annotations

from fastapi import Depends

from sheaveline.api.deps import Work
from sheaveline.api.errors import NotFound
from sheaveline.board.incident import Incident
from sheaveline.persistence.base import StorageError
from sheaveline.persistence.uow import UnitOfWork
from sheaveline.plant.identity import FilingNumber, LiftCode, RopeSerial
from sheaveline.plant.lift import Lift
from sheaveline.plant.rope import HaulRope
from sheaveline.upkeep.workorder import WorkOrder
from sheaveline.weather.observation import Station


class Lookup:
    """The one place a route turns a piece of the path into a thing on file."""

    __slots__ = ("work",)

    def __init__(self, work: UnitOfWork) -> None:
        self.work = work

    def lift_code(self, code: str) -> LiftCode:
        """The code, checked against the register without loading the machine."""
        wanted = LiftCode.parse(code)
        if not self.work.lifts.exists(wanted):
            raise NotFound(f"there is no lift {wanted}")
        return wanted

    def lift(self, code: str) -> Lift:
        wanted = LiftCode.parse(code)
        try:
            return self.work.lifts.get(wanted)
        except StorageError as missing:
            raise NotFound(f"there is no lift {wanted}") from missing

    def rope(self, serial: str) -> HaulRope:
        wanted = RopeSerial.parse(serial)
        try:
            return self.work.ropes.get(wanted)
        except StorageError as missing:
            raise NotFound(f"there is no rope {wanted}") from missing

    def station(self, name: str) -> Station:
        try:
            return self.work.weather.station(name)
        except StorageError as missing:
            raise NotFound(f"there is no station called {name}") from missing

    def work_order(self, number: int) -> WorkOrder:
        try:
            return self.work.work_orders.get(number)
        except StorageError as missing:
            raise NotFound(f"there is no work order {number}") from missing

    def incident(self, incident_id: int) -> tuple[Incident, FilingNumber | None]:
        try:
            return self.work.incidents.get(incident_id)
        except StorageError as missing:
            raise NotFound(f"there is no incident {incident_id}") from missing


def lookup_of(work: UnitOfWork = Work) -> Lookup:
    return Lookup(work)


Look = Depends(lookup_of)
