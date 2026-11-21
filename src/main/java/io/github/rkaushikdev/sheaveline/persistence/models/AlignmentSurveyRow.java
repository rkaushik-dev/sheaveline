package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One autumn's total station work on one machine: a survey a machine a day. */
@Entity
@Table(
        name = "alignment_surveys",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_alignment_survey_a_day",
                        columnNames = {"lift_code", "taken"}),
        indexes = @Index(name = "ix_alignment_by_machine", columnList = "lift_code, taken"))
public class AlignmentSurveyRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "taken", nullable = false)
    private LocalDate taken;

    @Column(name = "surveyor", length = 64, nullable = false)
    private String surveyor;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_alignment_surveys_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TowerShotRow> shots = new ArrayList<>();

    /** For the persistence provider. */
    protected AlignmentSurveyRow() {}

    /**
     * A survey with no shots in it yet.
     *
     * @param liftCode the machine surveyed
     * @param taken the day the instrument was set up
     * @param surveyor who ran it
     */
    public AlignmentSurveyRow(LiftCode liftCode, LocalDate taken, String surveyor) {
        this.liftCode = liftCode;
        this.taken = taken;
        this.surveyor = surveyor;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public LocalDate getTaken() {
        return taken;
    }

    public void setTaken(LocalDate taken) {
        this.taken = taken;
    }

    public String getSurveyor() {
        return surveyor;
    }

    public void setSurveyor(String surveyor) {
        this.surveyor = surveyor;
    }

    /**
     * The towers shot.
     *
     * @return the shots, read only
     */
    public List<TowerShotRow> getShots() {
        return Collections.unmodifiableList(shots);
    }

    /**
     * Add a tower to the survey.
     *
     * @param shot the shot, which now points back at this survey
     */
    public void addShot(TowerShotRow shot) {
        shot.takenIn(this);
        shots.add(shot);
    }
}
