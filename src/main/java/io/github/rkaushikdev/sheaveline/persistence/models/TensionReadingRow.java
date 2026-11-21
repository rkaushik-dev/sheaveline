package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One reading of a tension system: where the carriage sat, the tension, and the temperature. */
@Entity
@Table(name = "tension_readings")
public class TensionReadingRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "taken", nullable = false)
    private LocalDateTime taken;

    @Column(name = "carriage", nullable = false)
    private Length carriage;

    @Column(name = "measured", nullable = false)
    private Load measured;

    @Column(name = "temperature", nullable = false)
    private Temperature temperature;

    /** The system, written through {@code lift_code} and brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_tension_readings_lift_code_tension_systems"))
    private TensionSystemRow system;

    /** For the persistence provider. */
    protected TensionReadingRow() {}

    /**
     * A reading of no system yet.
     *
     * @param taken when it was taken
     * @param carriage where the carriage sat
     * @param measured the tension measured
     * @param temperature the air temperature
     */
    public TensionReadingRow(LocalDateTime taken, Length carriage, Load measured, Temperature temperature) {
        this.taken = taken;
        this.carriage = carriage;
        this.measured = measured;
        this.temperature = temperature;
    }

    void takenOf(TensionSystemRow owner) {
        this.system = owner;
        this.liftCode = owner.getLiftCode();
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    /**
     * The machine whose system was read.
     *
     * @return the code, or null until the reading is of a system
     */
    public LiftCode getLiftCode() {
        return liftCode;
    }

    public TensionSystemRow getSystem() {
        return system;
    }

    public LocalDateTime getTaken() {
        return taken;
    }

    public Length getCarriage() {
        return carriage;
    }

    public Load getMeasured() {
        return measured;
    }

    public Temperature getTemperature() {
        return temperature;
    }
}
