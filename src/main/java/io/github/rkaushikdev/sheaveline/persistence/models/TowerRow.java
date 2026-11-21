package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

/**
 * One tower on a line, with the sheave assembly on each side of it.
 *
 * <p>Towers are numbered up the line from one, and a number is used once on a line.
 */
@Entity
@Table(
        name = "towers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_number_a_line",
                        columnNames = {"lift_code", "number"}))
@Check(name = "ck_towers_towers_from_one", constraints = "number >= 1")
public class TowerRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "base_elevation", nullable = false)
    private Length baseElevation;

    @Column(name = "height", nullable = false)
    private Length height;

    @Column(name = "uphill_kind", length = ENUM_LENGTH, nullable = false)
    private AssemblyKind uphillKind;

    @Column(name = "uphill_sheaves", nullable = false)
    private int uphillSheaves;

    @Column(name = "uphill_deviation", nullable = false)
    private Angle uphillDeviation;

    @Column(name = "downhill_kind", length = ENUM_LENGTH, nullable = false)
    private AssemblyKind downhillKind;

    @Column(name = "downhill_sheaves", nullable = false)
    private int downhillSheaves;

    @Column(name = "downhill_deviation", nullable = false)
    private Angle downhillDeviation;

    /** The machine, written through {@code lift_code} and brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_towers_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected TowerRow() {}

    /**
     * A tower on no line yet.
     *
     * @param number its number up the line, from one
     * @param station how far up the line it stands
     * @param baseElevation the elevation of its footing
     * @param height from the footing to the crossarm
     * @param uphillKind the assembly on the uphill side
     * @param uphillSheaves how many sheaves that assembly has
     * @param uphillDeviation how far the uphill rope turns over it
     * @param downhillKind the assembly on the downhill side
     * @param downhillSheaves how many sheaves that assembly has
     * @param downhillDeviation how far the downhill rope turns over it
     */
    public TowerRow(
            int number,
            Length station,
            Length baseElevation,
            Length height,
            AssemblyKind uphillKind,
            int uphillSheaves,
            Angle uphillDeviation,
            AssemblyKind downhillKind,
            int downhillSheaves,
            Angle downhillDeviation) {
        this.number = number;
        this.station = station;
        this.baseElevation = baseElevation;
        this.height = height;
        this.uphillKind = uphillKind;
        this.uphillSheaves = uphillSheaves;
        this.uphillDeviation = uphillDeviation;
        this.downhillKind = downhillKind;
        this.downhillSheaves = downhillSheaves;
        this.downhillDeviation = downhillDeviation;
    }

    void placeOn(LiftRow owner) {
        this.lift = owner;
        this.liftCode = owner.getCode();
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
     * The machine's code.
     *
     * @return the code, or null until the tower is on a line
     */
    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public Length getStation() {
        return station;
    }

    public void setStation(Length station) {
        this.station = station;
    }

    public Length getBaseElevation() {
        return baseElevation;
    }

    public void setBaseElevation(Length baseElevation) {
        this.baseElevation = baseElevation;
    }

    public Length getHeight() {
        return height;
    }

    public void setHeight(Length height) {
        this.height = height;
    }

    public AssemblyKind getUphillKind() {
        return uphillKind;
    }

    public void setUphillKind(AssemblyKind uphillKind) {
        this.uphillKind = uphillKind;
    }

    public int getUphillSheaves() {
        return uphillSheaves;
    }

    public void setUphillSheaves(int uphillSheaves) {
        this.uphillSheaves = uphillSheaves;
    }

    public Angle getUphillDeviation() {
        return uphillDeviation;
    }

    public void setUphillDeviation(Angle uphillDeviation) {
        this.uphillDeviation = uphillDeviation;
    }

    public AssemblyKind getDownhillKind() {
        return downhillKind;
    }

    public void setDownhillKind(AssemblyKind downhillKind) {
        this.downhillKind = downhillKind;
    }

    public int getDownhillSheaves() {
        return downhillSheaves;
    }

    public void setDownhillSheaves(int downhillSheaves) {
        this.downhillSheaves = downhillSheaves;
    }

    public Angle getDownhillDeviation() {
        return downhillDeviation;
    }

    public void setDownhillDeviation(Angle downhillDeviation) {
        this.downhillDeviation = downhillDeviation;
    }
}
