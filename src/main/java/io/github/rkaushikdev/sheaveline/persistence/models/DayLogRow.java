package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One machine's day on the hill: the window it ran, the lines written in its book, and the morning
 * walk. One log a machine a day.
 *
 * <p>The day log is the parent and almost everything else on a given day hangs off it: an inspector
 * asks for the fifteenth of January on John Paul and gets one row and everything under it. Stops
 * and holds are the exceptions and sit against the machine, because a wind hold called at four in
 * the afternoon and cleared at nine the next morning belongs to two days.
 */
@Entity
@Table(
        name = "day_logs",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_log_a_day",
                        columnNames = {"lift_code", "day"}))
@Check(name = "ck_day_logs_loads_in_order", constraints = "last_load >= first_load")
@Check(name = "ck_day_logs_sweep_after_last_load", constraints = "sweep_by >= last_load")
public class DayLogRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "first_load", nullable = false)
    private TimeOfDay firstLoad;

    @Column(name = "last_load", nullable = false)
    private TimeOfDay lastLoad;

    @Column(name = "sweep_by", nullable = false)
    private TimeOfDay sweepBy;

    @Column(name = "opened")
    private TimeOfDay opened;

    @Column(name = "closed")
    private TimeOfDay closed;

    @Column(name = "mechanic", length = 64)
    private String mechanic;

    @Column(name = "operator", length = 64)
    private String operator;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_day_logs_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "dayLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LogEntryRow> entries = new ArrayList<>();

    @OneToMany(mappedBy = "dayLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreOpResultRow> checks = new ArrayList<>();

    /** For the persistence provider. */
    protected DayLogRow() {}

    /**
     * A day not yet opened, with nobody signed on and nothing written.
     *
     * @param liftCode the machine
     * @param day the operating day
     * @param firstLoad when the public may first load
     * @param lastLoad when the public may last load
     * @param sweepBy when the sweep has to be down
     */
    public DayLogRow(LiftCode liftCode, LocalDate day, TimeOfDay firstLoad, TimeOfDay lastLoad, TimeOfDay sweepBy) {
        this.liftCode = liftCode;
        this.day = day;
        this.firstLoad = firstLoad;
        this.lastLoad = lastLoad;
        this.sweepBy = sweepBy;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public LocalDate getDay() {
        return day;
    }

    public TimeOfDay getFirstLoad() {
        return firstLoad;
    }

    public void setFirstLoad(TimeOfDay firstLoad) {
        this.firstLoad = firstLoad;
    }

    public TimeOfDay getLastLoad() {
        return lastLoad;
    }

    public void setLastLoad(TimeOfDay lastLoad) {
        this.lastLoad = lastLoad;
    }

    public TimeOfDay getSweepBy() {
        return sweepBy;
    }

    public void setSweepBy(TimeOfDay sweepBy) {
        this.sweepBy = sweepBy;
    }

    public Optional<TimeOfDay> getOpened() {
        return Optional.ofNullable(opened);
    }

    /**
     * Record when the machine opened.
     *
     * @param opened the time, or null for not yet
     */
    public void setOpened(TimeOfDay opened) {
        this.opened = opened;
    }

    public Optional<TimeOfDay> getClosed() {
        return Optional.ofNullable(closed);
    }

    /**
     * Record when the machine closed.
     *
     * @param closed the time, or null for not yet
     */
    public void setClosed(TimeOfDay closed) {
        this.closed = closed;
    }

    public Optional<String> getMechanic() {
        return Optional.ofNullable(mechanic);
    }

    /**
     * Sign the mechanic on.
     *
     * @param mechanic the mechanic, or null for nobody yet
     */
    public void setMechanic(String mechanic) {
        this.mechanic = mechanic;
    }

    public Optional<String> getOperator() {
        return Optional.ofNullable(operator);
    }

    /**
     * Sign the operator on.
     *
     * @param operator the operator, or null for nobody yet
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * The lines written in the book.
     *
     * @return the entries, read only
     */
    public List<LogEntryRow> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Write a line in the book.
     *
     * @param entry the line, which now points back at this day
     */
    public void addEntry(LogEntryRow entry) {
        entry.writtenIn(this);
        entries.add(entry);
    }

    /**
     * The items walked on the morning check.
     *
     * @return the results, read only
     */
    public List<PreOpResultRow> getChecks() {
        return Collections.unmodifiableList(checks);
    }

    /**
     * Record one item of the morning walk.
     *
     * @param check the result, which now points back at this day
     */
    public void addCheck(PreOpResultRow check) {
        check.walkedOn(this);
        checks.add(check);
    }
}
