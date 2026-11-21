package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.TOWER_REF_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.TowerRef;
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

/** One tower in one alignment survey, as the instrument read it. One shot a tower a survey. */
@Entity
@Table(
        name = "tower_shots",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_shot_a_tower",
                        columnNames = {"survey_id", "tower_ref"}))
@Check(name = "ck_tower_shots_shot_station_not_negative", constraints = "station >= 0")
public class TowerShotRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "survey_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_tower_shots_survey_id_alignment_surveys"))
    private AlignmentSurveyRow survey;

    @Column(name = "tower_ref", length = TOWER_REF_LENGTH, nullable = false)
    private TowerRef towerRef;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "crossarm_elevation", nullable = false)
    private Length crossarmElevation;

    @Column(name = "offset_at_top", nullable = false)
    private Length offsetAtTop;

    /** For the persistence provider. */
    protected TowerShotRow() {}

    /**
     * A shot in no survey yet.
     *
     * @param towerRef the tower shot
     * @param station where the instrument put it along the line
     * @param crossarmElevation the elevation of its crossarm
     * @param offsetAtTop how far its top stands off the line
     */
    public TowerShotRow(TowerRef towerRef, Length station, Length crossarmElevation, Length offsetAtTop) {
        this.towerRef = towerRef;
        this.station = station;
        this.crossarmElevation = crossarmElevation;
        this.offsetAtTop = offsetAtTop;
    }

    void takenIn(AlignmentSurveyRow owner) {
        this.survey = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public AlignmentSurveyRow getSurvey() {
        return survey;
    }

    public TowerRef getTowerRef() {
        return towerRef;
    }

    public Length getStation() {
        return station;
    }

    public Length getCrossarmElevation() {
        return crossarmElevation;
    }

    public Length getOffsetAtTop() {
        return offsetAtTop;
    }
}
