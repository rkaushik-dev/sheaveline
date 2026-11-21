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
import org.hibernate.annotations.Check;

/**
 * The broken wires counted at one station: in one lay, in six lays, and how many of the one lay's
 * were in the valleys. Six lays hold the one, and the valleys are inside it.
 */
@Entity
@Table(name = "break_observations")
@Check(name = "ck_break_observations_six_holds_one", constraints = "in_six_lays >= in_one_lay")
@Check(name = "ck_break_observations_valley_inside_one", constraints = "valley_breaks <= in_one_lay")
public class BreakObservationRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "survey_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_break_observations_survey_id_break_surveys"))
    private BreakSurveyRow survey;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "in_one_lay", nullable = false)
    private int inOneLay;

    @Column(name = "in_six_lays", nullable = false)
    private int inSixLays;

    @Column(name = "valley_breaks", nullable = false)
    private int valleyBreaks;

    @Column(name = "at_splice", nullable = false)
    private boolean atSplice;

    /** For the persistence provider. */
    protected BreakObservationRow() {}

    /**
     * An observation with no valley breaks, away from a splice, in no count yet.
     *
     * @param station where along the rope
     * @param inOneLay the breaks in the worst lay length
     * @param inSixLays the breaks in the worst six lay lengths
     */
    public BreakObservationRow(Length station, int inOneLay, int inSixLays) {
        this.station = station;
        this.inOneLay = inOneLay;
        this.inSixLays = inSixLays;
    }

    void seenIn(BreakSurveyRow owner) {
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

    public BreakSurveyRow getSurvey() {
        return survey;
    }

    public Length getStation() {
        return station;
    }

    public void setStation(Length station) {
        this.station = station;
    }

    public int getInOneLay() {
        return inOneLay;
    }

    public void setInOneLay(int inOneLay) {
        this.inOneLay = inOneLay;
    }

    public int getInSixLays() {
        return inSixLays;
    }

    public void setInSixLays(int inSixLays) {
        this.inSixLays = inSixLays;
    }

    public int getValleyBreaks() {
        return valleyBreaks;
    }

    public void setValleyBreaks(int valleyBreaks) {
        this.valleyBreaks = valleyBreaks;
    }

    public boolean isAtSplice() {
        return atSplice;
    }

    public void setAtSplice(boolean atSplice) {
        this.atSplice = atSplice;
    }
}
