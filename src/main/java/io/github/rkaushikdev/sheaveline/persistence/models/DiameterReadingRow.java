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

/** One caliper reading in a wear survey, across the crowns and across the valleys. */
@Entity
@Table(name = "diameter_readings")
public class DiameterReadingRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "survey_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_diameter_readings_survey_id_wear_surveys"))
    private WearSurveyRow survey;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "across_crowns", nullable = false)
    private Length acrossCrowns;

    @Column(name = "across_valleys", nullable = false)
    private Length acrossValleys;

    /** For the persistence provider. */
    protected DiameterReadingRow() {}

    /**
     * A reading in no survey yet.
     *
     * @param station where along the rope it was read
     * @param acrossCrowns the diameter across the strand crowns
     * @param acrossValleys the diameter across the valleys between them
     */
    public DiameterReadingRow(Length station, Length acrossCrowns, Length acrossValleys) {
        this.station = station;
        this.acrossCrowns = acrossCrowns;
        this.acrossValleys = acrossValleys;
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

    public void setStation(Length station) {
        this.station = station;
    }

    public Length getAcrossCrowns() {
        return acrossCrowns;
    }

    public void setAcrossCrowns(Length acrossCrowns) {
        this.acrossCrowns = acrossCrowns;
    }

    public Length getAcrossValleys() {
        return acrossValleys;
    }

    public void setAcrossValleys(Length acrossValleys) {
        this.acrossValleys = acrossValleys;
    }
}
