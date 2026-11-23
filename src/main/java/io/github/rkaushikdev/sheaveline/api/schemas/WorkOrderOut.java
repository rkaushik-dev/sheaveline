package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A work order, with what its priority means for the machine.
 *
 * @param number its number
 * @param lift the machine
 * @param raised the day it was raised
 * @param raisedBy who raised it
 * @param detail what is wrong
 * @param priority how soon it is due, from the defect it came off
 * @param where where on the machine, when it says
 * @param dueBy the day it is due
 * @param open whether it is still to be done
 * @param closed the day it was finished, once it has been
 * @param holdsTheMachine whether the machine stays stopped until it is done, as at the service's
 *     today, which an urgent order past its deadline does
 */
public record WorkOrderOut(
        int number,
        @LiftCodeText LiftCode lift,
        LocalDate raised,
        String raisedBy,
        String detail,
        String priority,
        Optional<String> where,
        LocalDate dueBy,
        boolean open,
        Optional<LocalDate> closed,
        boolean holdsTheMachine) {

    /**
     * An order as the tablet shows it.
     *
     * @param order the order
     * @param today the day it is read on
     * @return its shape
     */
    public static WorkOrderOut of(WorkOrder order, LocalDate today) {
        return new WorkOrderOut(
                order.number(),
                order.lift(),
                order.raised(),
                order.raisedBy(),
                order.detail(),
                order.priority().value(),
                order.where(),
                order.dueBy(),
                order.isOpen(),
                order.closed(),
                order.stopsTheMachine(today));
    }
}
