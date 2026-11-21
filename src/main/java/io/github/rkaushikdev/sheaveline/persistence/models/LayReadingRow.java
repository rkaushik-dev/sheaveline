package io.github.rkaushikdev.sheaveline.persistence.models;

import io.github.rkaushikdev.sheaveline.measures.Length;
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

/** One lay length measured in a wear survey. */
@Entity
@Table(name = "lay_readings")
public class LayReadingRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "survey_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_lay_readings_survey_id_wear_surveys"))
    private WearSurveyRow survey;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "measured", nullable = false)
    private Length measured;

    /** For the persistence provider. */
    protected LayReadingRow() {}

    /**
     * A reading in no survey yet.
     *
     * @param station where along the rope it was measured
     * @param measured the lay length
     */
    public LayReadingRow(Length station, Length measured) {
        this.station = station;
        this.measured = measured;
    }

    void takenIn(WearSurveyRow owner) {
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

    public WearSurveyRow getSurvey() {
        return survey;
    }

    public Length getStation() {
        return station;
    }

    public Length getMeasured() {
        return measured;
    }
}
