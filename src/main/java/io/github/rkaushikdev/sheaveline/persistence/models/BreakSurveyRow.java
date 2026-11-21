package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ROPE_SERIAL_LENGTH;

import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One broken wire count along a rope: one count a rope a day. */
@Entity
@Table(
        name = "break_surveys",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_break_survey_a_day",
                        columnNames = {"rope_serial", "taken"}))
public class BreakSurveyRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "rope_serial", length = ROPE_SERIAL_LENGTH, nullable = false)
    private RopeSerial ropeSerial;

    @Column(name = "taken", nullable = false)
    private LocalDate taken;

    @Column(name = "inspector", length = 64, nullable = false)
    private String inspector;

    /** The rope, read through {@code rope_serial}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "rope_serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_break_surveys_rope_serial_haul_ropes"))
    private HaulRopeRow rope;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BreakObservationRow> observations = new ArrayList<>();

    /** For the persistence provider. */
    protected BreakSurveyRow() {}

    /**
     * A count with nothing seen yet.
     *
     * @param ropeSerial the rope counted
     * @param taken the day it was counted
     * @param inspector who counted it
     */
    public BreakSurveyRow(RopeSerial ropeSerial, LocalDate taken, String inspector) {
        this.ropeSerial = ropeSerial;
        this.taken = taken;
        this.inspector = inspector;
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

    public LocalDate getTaken() {
        return taken;
    }

    public String getInspector() {
        return inspector;
    }

    /**
     * What was seen along the rope.
     *
     * @return the observations, read only
     */
    public List<BreakObservationRow> getObservations() {
        return Collections.unmodifiableList(observations);
    }

    /**
     * Add an observation to the count.
     *
     * @param observation the observation, which now points back at this count
     */
    public void addObservation(BreakObservationRow observation) {
        observation.seenIn(this);
        observations.add(observation);
    }
}
