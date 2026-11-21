package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.DriveType;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.TerminalRole;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One end of a machine: its bullwheel, its drive if it drives, and its brakes.
 *
 * <p>The drive lives on the terminal's own row rather than in a table of its own, because a drive
 * has no identity apart from the terminal it turns.
 */
@Entity
@Table(
        name = "terminals",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_label_a_lift",
                        columnNames = {"lift_code", "label"}))
public class TerminalRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "role", length = ENUM_LENGTH, nullable = false)
    private TerminalRole role;

    @Column(name = "label", length = 64, nullable = false)
    private String label;

    @Column(name = "station", nullable = false)
    private Length station;

    @Column(name = "rope_elevation", nullable = false)
    private Length ropeElevation;

    @Column(name = "bullwheel_diameter", nullable = false)
    private Length bullwheelDiameter;

    @Column(name = "bullwheel_liner", length = 32, nullable = false)
    private String bullwheelLiner;

    @Column(name = "drive_type", length = ENUM_LENGTH)
    private DriveType driveType;

    @Column(name = "rated_horsepower")
    private Integer ratedHorsepower;

    @Convert(converter = ColumnTypes.DecimalColumn.class)
    @Column(name = "gearbox_ratio")
    private BigDecimal gearboxRatio;

    @Column(name = "auxiliary", length = ENUM_LENGTH)
    private DriveType auxiliary;

    @Column(name = "tension_travel")
    private Length tensionTravel;

    /** The machine, written through {@code lift_code} and brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_terminals_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "terminal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BrakeRow> brakes = new ArrayList<>();

    /** For the persistence provider. */
    protected TerminalRow() {}

    /**
     * A terminal that drives nothing and tensions nothing, on no machine yet.
     *
     * @param role what the terminal does
     * @param label what the crew calls it, unique on its machine
     * @param station how far up the line it stands
     * @param ropeElevation the rope's elevation at the bullwheel
     * @param bullwheelDiameter the bullwheel's diameter
     * @param bullwheelLiner what the bullwheel is lined with
     */
    public TerminalRow(
            TerminalRole role,
            String label,
            Length station,
            Length ropeElevation,
            Length bullwheelDiameter,
            String bullwheelLiner) {
        this.role = role;
        this.label = label;
        this.station = station;
        this.ropeElevation = ropeElevation;
        this.bullwheelDiameter = bullwheelDiameter;
        this.bullwheelLiner = bullwheelLiner;
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
     * @return the code, or null until the terminal is on a machine
     */
    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public TerminalRole getRole() {
        return role;
    }

    public void setRole(TerminalRole role) {
        this.role = role;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Length getStation() {
        return station;
    }

    public void setStation(Length station) {
        this.station = station;
    }

    public Length getRopeElevation() {
        return ropeElevation;
    }

    public void setRopeElevation(Length ropeElevation) {
        this.ropeElevation = ropeElevation;
    }

    public Length getBullwheelDiameter() {
        return bullwheelDiameter;
    }

    public void setBullwheelDiameter(Length bullwheelDiameter) {
        this.bullwheelDiameter = bullwheelDiameter;
    }

    public String getBullwheelLiner() {
        return bullwheelLiner;
    }

    public void setBullwheelLiner(String bullwheelLiner) {
        this.bullwheelLiner = bullwheelLiner;
    }

    public Optional<DriveType> getDriveType() {
        return Optional.ofNullable(driveType);
    }

    /**
     * Say what drives the terminal.
     *
     * @param driveType the drive, or null for a terminal that does not drive
     */
    public void setDriveType(DriveType driveType) {
        this.driveType = driveType;
    }

    public Optional<Integer> getRatedHorsepower() {
        return Optional.ofNullable(ratedHorsepower);
    }

    /**
     * Rate the drive.
     *
     * @param ratedHorsepower the rating, or null for none
     */
    public void setRatedHorsepower(Integer ratedHorsepower) {
        this.ratedHorsepower = ratedHorsepower;
    }

    public Optional<BigDecimal> getGearboxRatio() {
        return Optional.ofNullable(gearboxRatio);
    }

    /**
     * Give the gearbox ratio, kept to three places.
     *
     * @param gearboxRatio motor revolutions per wheel revolution, or null for none
     */
    public void setGearboxRatio(BigDecimal gearboxRatio) {
        this.gearboxRatio = gearboxRatio;
    }

    public Optional<DriveType> getAuxiliary() {
        return Optional.ofNullable(auxiliary);
    }

    /**
     * Say what turns the line when the main drive cannot.
     *
     * @param auxiliary the auxiliary drive, or null for none
     */
    public void setAuxiliary(DriveType auxiliary) {
        this.auxiliary = auxiliary;
    }

    public Optional<Length> getTensionTravel() {
        return Optional.ofNullable(tensionTravel);
    }

    /**
     * Give the tension carriage's travel.
     *
     * @param tensionTravel the travel, or null for a terminal that does not tension
     */
    public void setTensionTravel(Length tensionTravel) {
        this.tensionTravel = tensionTravel;
    }

    /**
     * The brakes.
     *
     * @return the brakes, read only
     */
    public List<BrakeRow> getBrakes() {
        return Collections.unmodifiableList(brakes);
    }

    /**
     * Fit a brake to this terminal.
     *
     * @param brake the brake, which now points back at this terminal
     */
    public void addBrake(BrakeRow brake) {
        brake.fitTo(this);
        brakes.add(brake);
    }
}
