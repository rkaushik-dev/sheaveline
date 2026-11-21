package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
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

/** One ground shot under a line: how high the ground is at a station. One shot a station. */
@Entity
@Table(
        name = "survey_shots",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_shot_a_station",
                        columnNames = {"lift_code", "station"}))
public class SurveyShotRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "elevation", nullable = false)
    private Length elevation;

    /** The machine, written through {@code lift_code} and brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_survey_shots_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected SurveyShotRow() {}

    /**
     * A shot on no line yet.
     *
     * @param station how far up the line it was taken
     * @param elevation the ground's elevation there
     */
    public SurveyShotRow(Length station, Length elevation) {
        this.station = station;
        this.elevation = elevation;
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
     * @return the code, or null until the shot is on a line
     */
    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public Length getStation() {
        return station;
    }

    public Length getElevation() {
        return elevation;
    }
}
