package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * One reading of a machine's hour meter and cycle counter: one a machine a day, and never below
 * zero. The hours keep their tenths.
 */
@Entity
@Table(
        name = "meter_readings",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_reading_a_day",
                        columnNames = {"lift_code", "day"}))
@Check(name = "ck_meter_readings_cycles_not_negative", constraints = "cycles >= 0")
@Check(name = "ck_meter_readings_hours_not_negative", constraints = "hours >= 0")
public class MeterReadingRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Convert(converter = ColumnTypes.DecimalColumn.class)
    @Column(name = "hours", nullable = false)
    private BigDecimal hours;

    @Column(name = "cycles", nullable = false)
    private int cycles;

    @Column(name = "read_by", length = 64, nullable = false)
    private String readBy;

    @Column(name = "meter_replaced", nullable = false)
    private boolean meterReplaced;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_meter_readings_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected MeterReadingRow() {}

    /**
     * A reading of a meter that has not been replaced.
     *
     * @param liftCode the machine
     * @param day the day it was read
     * @param hours the hour meter
     * @param cycles the cycle counter
     * @param readBy who read it
     */
    public MeterReadingRow(LiftCode liftCode, LocalDate day, BigDecimal hours, int cycles, String readBy) {
        this.liftCode = liftCode;
        this.day = day;
        this.hours = hours;
        this.cycles = cycles;
        this.readBy = readBy;
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

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public int getCycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    public String getReadBy() {
        return readBy;
    }

    public void setReadBy(String readBy) {
        this.readBy = readBy;
    }

    /**
     * Whether the meter was replaced and came back reading zero, so the count starts again.
     *
     * @return true when it was
     */
    public boolean isMeterReplaced() {
        return meterReplaced;
    }

    public void setMeterReplaced(boolean meterReplaced) {
        this.meterReplaced = meterReplaced;
    }
}
