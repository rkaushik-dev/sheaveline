package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Work orders, by their number. */
public final class WorkOrderRepository extends Repository {

    /** The number the first order ever raised takes. */
    public static final int FIRST_NUMBER = 1;

    /**
     * The work orders in a session.
     *
     * @param session the session
     */
    public WorkOrderRepository(Session session) {
        super(session);
    }

    /**
     * File a work order.
     *
     * @param order the order
     * @throws StorageError when an order with its number is already on file
     */
    public void add(WorkOrder order) {
        if (find(WorkOrderRow.class, order.number()).isPresent()) {
            throw new StorageError("work order " + order.number() + " is already on file");
        }
        WorkOrderRow row = new WorkOrderRow(
                order.number(), order.lift(), order.raised(), order.raisedBy(), order.detail(), order.priority());
        row.setWhere(order.where().orElse(null));
        row.setClosed(order.closed().orElse(null));
        row.setClosedBy(order.closedBy().orElse(null));
        session().persist(row);
    }

    /**
     * A work order by its number.
     *
     * @param number the number
     * @return the order
     * @throws StorageError when there is no such order
     */
    public WorkOrder get(int number) {
        return toDomain(row(number));
    }

    /**
     * The number the next order raised should take.
     *
     * @return one past the highest number on file, or one when nothing is
     */
    public int nextNumber() {
        return first(query("select w.number from WorkOrderRow w order by w.number desc", Integer.class))
                .map(highest -> highest + 1)
                .orElse(FIRST_NUMBER);
    }

    /**
     * Every order still open across the hill, by number.
     *
     * @return the orders
     */
    public List<WorkOrder> openOrders() {
        return openOrders(Optional.empty());
    }

    /**
     * The orders still open on one machine, by number.
     *
     * @param code the machine
     * @return the orders
     */
    public List<WorkOrder> openOrders(LiftCode code) {
        return openOrders(Optional.of(code));
    }

    private List<WorkOrder> openOrders(Optional<LiftCode> code) {
        String statement = "select w from WorkOrderRow w where w.closed is null"
                + code.map(any -> " and w.liftCode = :code").orElse("")
                + " order by w.number";
        TypedQuery<WorkOrderRow> query = query(statement, WorkOrderRow.class);
        code.ifPresent(wanted -> query.setParameter("code", wanted));
        return many(query).stream().map(WorkOrderRepository::toDomain).toList();
    }

    /**
     * Every order at one priority, open or closed, by number.
     *
     * @param priority the priority
     * @return the orders
     */
    public List<WorkOrder> ofPriority(Priority priority) {
        return many(query(
                                "select w from WorkOrderRow w where w.priority = :priority order by w.number",
                                WorkOrderRow.class)
                        .setParameter("priority", priority))
                .stream()
                .map(WorkOrderRepository::toDomain)
                .toList();
    }

    /**
     * Close an order and write the closure back.
     *
     * @param number the order
     * @param on the day the work was done
     * @param by who signed it off
     * @return the closed order
     * @throws StorageError when there is no such order
     * @throws io.github.rkaushikdev.sheaveline.upkeep.UpkeepError when it is already closed, or
     *     cannot close on that day
     */
    public WorkOrder close(int number, LocalDate on, String by) {
        WorkOrderRow row = row(number);
        WorkOrder closed = toDomain(row).close(on, by);
        row.setClosed(closed.closed().orElse(null));
        row.setClosedBy(closed.closedBy().orElse(null));
        return closed;
    }

    private WorkOrderRow row(int number) {
        return fetch(WorkOrderRow.class, number, "there is no work order " + number);
    }

    private static WorkOrder toDomain(WorkOrderRow row) {
        return new WorkOrder(
                row.getNumber(),
                row.getLiftCode(),
                row.getRaised(),
                row.getRaisedBy(),
                row.getDetail(),
                row.getPriority(),
                row.getWhere(),
                row.getClosed(),
                row.getClosedBy());
    }
}
