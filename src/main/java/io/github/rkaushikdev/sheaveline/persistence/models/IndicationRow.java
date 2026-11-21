package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.ropework.IndicationKind;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** One indication a magnetic test flagged, with its size held to three places. */
@Entity
@Table(name = "indications")
public class IndicationRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "test_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_indications_test_id_magnetic_tests"))
    private MagneticTestRow test;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private IndicationKind kind;

    @Convert(converter = ColumnTypes.DecimalColumn.class)
    @Column(name = "magnitude", nullable = false)
    private BigDecimal magnitude;

    /** For the persistence provider. */
    protected IndicationRow() {}

    /**
     * An indication on no test yet.
     *
     * @param station where along the rope
     * @param kind what the instrument saw
     * @param magnitude how big it was
     */
    public IndicationRow(Length station, IndicationKind kind, BigDecimal magnitude) {
        this.station = station;
        this.kind = kind;
        this.magnitude = magnitude;
    }

    void flaggedBy(MagneticTestRow owner) {
        this.test = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public MagneticTestRow getTest() {
        return test;
    }

    public Length getStation() {
        return station;
    }

    public IndicationKind getKind() {
        return kind;
    }

    public BigDecimal getMagnitude() {
        return magnitude;
    }
}
