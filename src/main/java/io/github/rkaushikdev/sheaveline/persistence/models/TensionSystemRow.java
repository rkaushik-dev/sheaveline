package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.ropework.TensionKind;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JavaType;

/**
 * A machine's tension system, keyed by the machine, and every reading taken of it.
 *
 * <p>This is the one part of the rope file that belongs to the machine rather than to the rope,
 * because the counterweight does not travel with the rope.
 */
@Entity
@Table(name = "tension_systems")
@Check(name = "ck_tension_systems_travel_positive", constraints = "travel > 0")
public class TensionSystemRow {

    @Id
    @JavaType(ColumnTypes.LiftCodeKey.class)
    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private TensionKind kind;

    @Column(name = "nominal", nullable = false)
    private Load nominal;

    @Column(name = "travel", nullable = false)
    private Length travel;

    @Column(name = "carriage", nullable = false)
    private Length carriage;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_tension_systems_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "system", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TensionReadingRow> readings = new ArrayList<>();

    /** For the persistence provider. */
    protected TensionSystemRow() {}

    /**
     * A tension system with no readings yet.
     *
     * @param liftCode the machine it tensions
     * @param kind how it tensions
     * @param nominal the tension it is set to hold
     * @param travel how far its carriage can travel
     * @param carriage where its carriage sits at rest
     */
    public TensionSystemRow(LiftCode liftCode, TensionKind kind, Load nominal, Length travel, Length carriage) {
        this.liftCode = liftCode;
        this.kind = kind;
        this.nominal = nominal;
        this.travel = travel;
        this.carriage = carriage;
    }

    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public TensionKind getKind() {
        return kind;
    }

    public void setKind(TensionKind kind) {
        this.kind = kind;
    }

    public Load getNominal() {
        return nominal;
    }

    public void setNominal(Load nominal) {
        this.nominal = nominal;
    }

    public Length getTravel() {
        return travel;
    }

    public void setTravel(Length travel) {
        this.travel = travel;
    }

    public Length getCarriage() {
        return carriage;
    }

    public void setCarriage(Length carriage) {
        this.carriage = carriage;
    }

    /**
     * The readings taken.
     *
     * @return the readings, read only
     */
    public List<TensionReadingRow> getReadings() {
        return Collections.unmodifiableList(readings);
    }

    /**
     * Add a reading of this system.
     *
     * @param reading the reading, which now points back at this system
     */
    public void addReading(TensionReadingRow reading) {
        reading.takenOf(this);
        readings.add(reading);
    }
}
