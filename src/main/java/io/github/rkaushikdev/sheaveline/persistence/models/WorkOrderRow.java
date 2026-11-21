package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One work order, by its number, carrying its own place, detail and priority.
 *
 * <p>That looks like duplication of the defect it was raised from, and is not. An order is raised
 * from a defect once, at one moment, with the priority the defect's level implied at that moment.
 * If the order read through to the defect, somebody downgrading a defect in April would quietly
 * rewrite the priority of a job that was done in January, and the record of what the department
 * actually decided would be gone.
 */
@Entity
@Table(
        name = "work_orders",
        indexes = @Index(name = "ix_work_orders_open_by_machine", columnList = "lift_code, closed"))
@Check(name = "ck_work_orders_orders_from_one", constraints = "number >= 1")
@Check(name = "ck_work_orders_closed_after_raised", constraints = "closed is null or closed >= raised")
public class WorkOrderRow {

    @Id
    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "raised", nullable = false)
    private LocalDate raised;

    @Column(name = "raised_by", length = 64, nullable = false)
    private String raisedBy;

    @Column(name = "detail", length = 500, nullable = false)
    private String detail;

    @Column(name = "priority", length = ENUM_LENGTH, nullable = false)
    private Priority priority;

    @Column(name = "\"WHERE\"", length = 32)
    private String where;

    @Column(name = "closed")
    private LocalDate closed;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_work_orders_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected WorkOrderRow() {}

    /**
     * An open order with no place given.
     *
     * @param number its number, from one
     * @param liftCode the machine
     * @param raised the day it was raised
     * @param raisedBy who raised it
     * @param detail what is to be done
     * @param priority how soon, decided when it was raised
     */
    public WorkOrderRow(
            int number, LiftCode liftCode, LocalDate raised, String raisedBy, String detail, Priority priority) {
        this.number = number;
        this.liftCode = liftCode;
        this.raised = raised;
        this.raisedBy = raisedBy;
        this.detail = detail;
        this.priority = priority;
    }

    public int getNumber() {
        return number;
    }

    public LiftCode getLiftCode() {
        return liftCode;
    }

    /**
     * The machine, read when first asked for.
     *
     * @return the machine
     */
    public LiftRow getLift() {
        return lift;
    }

    public LocalDate getRaised() {
        return raised;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public String getDetail() {
        return detail;
    }

    public Priority getPriority() {
        return priority;
    }

    public Optional<String> getWhere() {
        return Optional.ofNullable(where);
    }

    /**
     * Say where the work is.
     *
     * @param where the place, such as a tower reference, or null for the whole machine
     */
    public void setWhere(String where) {
        this.where = where;
    }

    public Optional<LocalDate> getClosed() {
        return Optional.ofNullable(closed);
    }

    /**
     * Record the day the work was done.
     *
     * @param closed the day, or null while it is open
     */
    public void setClosed(LocalDate closed) {
        this.closed = closed;
    }

    public Optional<String> getClosedBy() {
        return Optional.ofNullable(closedBy);
    }

    /**
     * Record who signed the work off.
     *
     * @param closedBy who closed it, or null while it is open
     */
    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }
}
