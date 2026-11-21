package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One job done on one machine on one day, with the meters as they read and every defect it found.
 *
 * <p>A defect hangs off the record that found it, so it can never exist without an inspection to
 * point at, and it goes when the record goes. A work order does not hang off the defect at all: see
 * {@link WorkOrderRow}.
 */
@Entity
@Table(
        name = "inspection_records",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_record_a_job_a_day",
                        columnNames = {"lift_code", "code", "done"}))
public class InspectionRecordRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "code", length = 4, nullable = false)
    private String code;

    @Column(name = "done", nullable = false)
    private LocalDate done;

    @Column(name = "by", length = 64, nullable = false)
    private String by;

    @Convert(converter = ColumnTypes.DecimalColumn.class)
    @Column(name = "hours")
    private BigDecimal hours;

    @Column(name = "cycles")
    private Integer cycles;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inspection_records_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DefectRow> defects = new ArrayList<>();

    /** For the persistence provider. */
    protected InspectionRecordRow() {}

    /**
     * A record with no meter readings and no defects yet.
     *
     * @param liftCode the machine
     * @param code the job's code in the library, such as {@code W1}
     * @param done the day it was done
     * @param by who signed it
     */
    public InspectionRecordRow(LiftCode liftCode, String code, LocalDate done, String by) {
        this.liftCode = liftCode;
        this.code = code;
        this.done = done;
        this.by = by;
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

    /**
     * The machine, read when first asked for.
     *
     * @return the machine
     */
    public LiftRow getLift() {
        return lift;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getDone() {
        return done;
    }

    public String getBy() {
        return by;
    }

    public Optional<BigDecimal> getHours() {
        return Optional.ofNullable(hours);
    }

    /**
     * Record the hour meter, kept to three places.
     *
     * @param hours the hours the meter read, or null when the job does not run on hours
     */
    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public Optional<Integer> getCycles() {
        return Optional.ofNullable(cycles);
    }

    /**
     * Record the cycle counter.
     *
     * @param cycles the count, or null when the job does not run on cycles
     */
    public void setCycles(Integer cycles) {
        this.cycles = cycles;
    }

    /**
     * The defects the job found.
     *
     * @return the defects, read only
     */
    public List<DefectRow> getDefects() {
        return Collections.unmodifiableList(defects);
    }

    /**
     * Write a defect against this record.
     *
     * @param defect the defect, which now points back at this record
     */
    public void addDefect(DefectRow defect) {
        defect.foundBy(this);
        defects.add(defect);
    }
}
