package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceStatus;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import io.github.rkaushikdev.sheaveline.weather.Hold;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One machine's morning, gathered by whoever walked it.
 *
 * @param lift the machine
 * @param name what the machine is called
 * @param status what it may do today
 * @param sheetWalked whether the morning walk was signed
 * @param blockers what keeps it shut, in words
 * @param holds the weather holds on it
 * @param orders its open work orders
 * @param upkeepDue the jobs falling due on it
 */
public record MachineLine(
        LiftCode lift,
        String name,
        ServiceStatus status,
        boolean sheetWalked,
        List<String> blockers,
        List<Hold> holds,
        List<WorkOrder> orders,
        List<String> upkeepDue) {

    /** A machine's morning. */
    public MachineLine {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        blockers = List.copyOf(blockers);
        holds = List.copyOf(holds);
        orders = List.copyOf(orders);
        upkeepDue = List.copyOf(upkeepDue);
    }

    /**
     * A machine's morning with nothing against it beyond its status and its walk.
     *
     * @param lift the machine
     * @param name what the machine is called
     * @param status what it may do today
     * @param sheetWalked whether the morning walk was signed
     */
    public MachineLine(LiftCode lift, String name, ServiceStatus status, boolean sheetWalked) {
        this(lift, name, status, sheetWalked, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Whether the machine opens. Everything has to be true at once, which is the point of the page:
     * it may carry the public, the walk is signed, and there is no blocker and no hold.
     *
     * @return true when it opens
     */
    public boolean opening() {
        return status.mayCarryPublic() && sheetWalked && blockers.isEmpty() && holds.isEmpty();
    }

    /**
     * The one word the page prints for the machine.
     *
     * @return {@code opening}, {@code held} when only the weather is stopping it, or {@code not
     *     opening}
     */
    public String verdict() {
        if (opening()) {
            return "opening";
        }
        return !holds.isEmpty() && blockers.isEmpty() ? "held" : "not opening";
    }

    /**
     * What is stopping it: the blockers, an unsigned walk, each hold, then the status.
     *
     * @return the reasons in that order, none for a machine that is opening
     */
    public List<String> reasons() {
        List<String> found = new ArrayList<>(blockers);
        if (!sheetWalked) {
            found.add("morning walk not signed");
        }
        holds.forEach(one -> found.add(one.reason().value() + " hold"));
        if (!status.mayCarryPublic()) {
            found.add(status.value());
        }
        return List.copyOf(found);
    }
}
