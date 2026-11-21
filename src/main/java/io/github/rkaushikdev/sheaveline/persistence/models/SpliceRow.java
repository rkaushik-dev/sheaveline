package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ROPE_SERIAL_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
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
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/** One splice in a rope: where it sits, who made it, and with how many tucks. One a station. */
@Entity
@Table(
        name = "splices",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_splice_a_station",
                        columnNames = {"rope_serial", "station"}))
@Check(name = "ck_splices_four_tucks_at_least", constraints = "tucks >= 4")
public class SpliceRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "rope_serial", length = ROPE_SERIAL_LENGTH, nullable = false)
    private RopeSerial ropeSerial;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "made", nullable = false)
    private LocalDate made;

    @Column(name = "made_by", length = 64, nullable = false)
    private String madeBy;

    @Column(name = "tucks", nullable = false)
    private int tucks;

    @Column(name = "length", nullable = false)
    private Length length;

    @Column(name = "mill", nullable = false)
    private boolean mill;

    /** The rope, read through {@code rope_serial}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "rope_serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_splices_rope_serial_haul_ropes"))
    private HaulRopeRow rope;

    /** For the persistence provider. */
    protected SpliceRow() {}

    /**
     * A splice made in the field rather than at the mill.
     *
     * @param ropeSerial the rope spliced
     * @param station where along the rope the splice sits
     * @param made the day it was made
     * @param madeBy who made it
     * @param tucks how many tucks it has
     * @param length how long it is
     */
    public SpliceRow(RopeSerial ropeSerial, Length station, LocalDate made, String madeBy, int tucks, Length length) {
        this.ropeSerial = ropeSerial;
        this.station = station;
        this.made = made;
        this.madeBy = madeBy;
        this.tucks = tucks;
        this.length = length;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public RopeSerial getRopeSerial() {
        return ropeSerial;
    }

    public HaulRopeRow getRope() {
        return rope;
    }

    public Length getStation() {
        return station;
    }

    public LocalDate getMade() {
        return made;
    }

    public String getMadeBy() {
        return madeBy;
    }

    public int getTucks() {
        return tucks;
    }

    public Length getLength() {
        return length;
    }

    /**
     * Whether the splice was made at the mill.
     *
     * @return true for a mill splice
     */
    public boolean isMill() {
        return mill;
    }

    public void setMill(boolean mill) {
        this.mill = mill;
    }
}
