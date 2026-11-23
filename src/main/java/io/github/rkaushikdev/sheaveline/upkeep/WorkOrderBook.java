package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Every work order raised on the hill.
 *
 * @param orders the orders, in number order when built with {@link #of(List)}
 */
public record WorkOrderBook(List<WorkOrder> orders) {

    /**
     * A book holding these orders in the order given.
     *
     * @throws UpkeepError when two orders share a number
     */
    public WorkOrderBook {
        orders = List.copyOf(orders);
        Set<Integer> numbers = new HashSet<>();
        for (WorkOrder order : orders) {
            numbers.add(order.number());
        }
        if (numbers.size() != orders.size()) {
            throw new UpkeepError("two work orders share a number");
        }
    }

    /**
     * An empty book.
     *
     * @return a book with nothing in it
     */
    public static WorkOrderBook of() {
        return new WorkOrderBook(List.of());
    }

    /**
     * A book with its orders put in number order.
     *
     * @param orders the orders, in any order
     * @return the book
     * @throws UpkeepError when two orders share a number
     */
    public static WorkOrderBook of(List<WorkOrder> orders) {
        return new WorkOrderBook(orders.stream()
                .sorted(Comparator.comparingInt(WorkOrder::number))
                .toList());
    }

    /**
     * The number the next order takes.
     *
     * @return one for an empty book, otherwise one past the last order's number
     */
    public int nextNumber() {
        return orders.isEmpty() ? 1 : orders.get(orders.size() - 1).number() + 1;
    }

    /**
     * The book with an order raised off a defect, under the next number.
     *
     * @param lift the machine
     * @param defect the defect
     * @param raisedBy who raised it
     * @return a new book; this one is unchanged
     */
    public WorkOrderBook raiseFrom(LiftCode lift, Defect defect, String raisedBy) {
        List<WorkOrder> all = new ArrayList<>(orders);
        all.add(WorkOrder.fromDefect(nextNumber(), lift, defect, raisedBy));
        return of(all);
    }

    /**
     * One order by its number.
     *
     * @param number the number
     * @return the order
     * @throws UpkeepError when no order has that number
     */
    public WorkOrder get(int number) {
        for (WorkOrder order : orders) {
            if (order.number() == number) {
                return order;
            }
        }
        throw new UpkeepError("there is no work order " + number);
    }

    /**
     * The book with one order closed.
     *
     * @param number the order's number
     * @param on the day the work was finished
     * @param by who finished it
     * @return a new book in number order; this one is unchanged
     * @throws UpkeepError when there is no such order, or it cannot be closed
     */
    public WorkOrderBook close(int number, LocalDate on, String by) {
        WorkOrder order = get(number);
        List<WorkOrder> all = new ArrayList<>();
        for (WorkOrder other : orders) {
            if (other.number() != number) {
                all.add(other);
            }
        }
        all.add(order.close(on, by));
        return of(all);
    }

    /**
     * Every order still open.
     *
     * @return those orders, in book order
     */
    public List<WorkOrder> openOrders() {
        return openOrders(Optional.empty());
    }

    /**
     * The orders still open on one machine.
     *
     * @param lift the machine
     * @return those orders, in book order
     */
    public List<WorkOrder> openOrders(LiftCode lift) {
        return openOrders(Optional.of(lift));
    }

    /**
     * Every order late on that day.
     *
     * @param on the day asked about
     * @return those orders, in book order
     */
    public List<WorkOrder> overdue(LocalDate on) {
        return overdue(on, Optional.empty());
    }

    /**
     * The orders on one machine late on that day.
     *
     * @param on the day asked about
     * @param lift the machine
     * @return those orders, in book order
     */
    public List<WorkOrder> overdue(LocalDate on, LiftCode lift) {
        return overdue(on, Optional.of(lift));
    }

    /**
     * The orders at one priority, open or closed.
     *
     * @param priority the priority
     * @return those orders, in book order
     */
    public List<WorkOrder> ofPriority(Priority priority) {
        return orders.stream().filter(order -> order.priority() == priority).toList();
    }

    /**
     * The machines an open order keeps from carrying the public on a day, an urgent order that has
     * run past its deadline included.
     *
     * @param on the day asked about
     * @return an unmodifiable set, in code order
     */
    public Set<LiftCode> holdingMachines(LocalDate on) {
        Set<LiftCode> held = openOrders().stream()
                .filter(order -> order.stopsTheMachine(on))
                .map(WorkOrder::lift)
                .collect(Collectors.toCollection(TreeSet::new));
        return Collections.unmodifiableSet(held);
    }

    /**
     * The orders at one place, open or closed.
     *
     * @param where the place, such as {@code JP-T4}
     * @return those orders, in book order
     */
    public List<WorkOrder> at(String where) {
        return orders.stream()
                .filter(order -> order.where().equals(Optional.of(where)))
                .toList();
    }

    /**
     * How many orders are in the book.
     *
     * @return the count
     */
    public int size() {
        return orders.size();
    }

    private List<WorkOrder> openOrders(Optional<LiftCode> lift) {
        return orders.stream()
                .filter(order -> order.isOpen() && onMachine(order, lift))
                .toList();
    }

    private List<WorkOrder> overdue(LocalDate on, Optional<LiftCode> lift) {
        return orders.stream()
                .filter(order -> order.isOverdue(on) && onMachine(order, lift))
                .toList();
    }

    private static boolean onMachine(WorkOrder order, Optional<LiftCode> lift) {
        return lift.isEmpty() || order.lift().equals(lift.get());
    }
}
