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

/**
 * One walk of a rope with the caliper and the lay gauge: one survey a rope a day.
 *
 * <p>Every table in the rope file hangs off the rope's serial rather than off a machine. A rope
 * moves, and its whole history has to follow it rather than staying with the machine it happened to
 * be on when the caliper came out.
 */
@Entity
@Table(
        name = "wear_surveys",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_wear_survey_a_day",
                        columnNames = {"rope_serial", "taken"}))
public class WearSurveyRow {

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
            foreignKey = @ForeignKey(name = "fk_wear_surveys_rope_serial_haul_ropes"))
    private HaulRopeRow rope;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiameterReadingRow> diameters = new ArrayList<>();

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LayReadingRow> lays = new ArrayList<>();

    /** For the persistence provider. */
    protected WearSurveyRow() {}

    /**
     * A survey with no readings in it yet.
     *
     * @param ropeSerial the rope walked
     * @param taken the day it was walked
     * @param inspector who walked it
     */
    public WearSurveyRow(RopeSerial ropeSerial, LocalDate taken, String inspector) {
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
     * The diameters read.
     *
     * @return the readings, read only
     */
    public List<DiameterReadingRow> getDiameters() {
        return Collections.unmodifiableList(diameters);
    }

    /**
     * Add a diameter reading to the survey.
     *
     * @param reading the reading, which now points back at this survey
     */
    public void addDiameter(DiameterReadingRow reading) {
        reading.takenIn(this);
        diameters.add(reading);
    }

    /**
     * The lay lengths read.
     *
     * @return the readings, read only
     */
    public List<LayReadingRow> getLays() {
        return Collections.unmodifiableList(lays);
    }

    /**
     * Add a lay length reading to the survey.
     *
     * @param reading the reading, which now points back at this survey
     */
    public void addLay(LayReadingRow reading) {
        reading.takenIn(this);
        lays.add(reading);
    }
}
