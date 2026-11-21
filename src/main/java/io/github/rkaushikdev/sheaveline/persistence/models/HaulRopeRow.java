package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ROPE_SERIAL_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JavaType;

/**
 * One haul rope on file, by its mill serial, on a machine or in the shop as a spare.
 *
 * <p>A rope is filed by its serial rather than by the machine it is on, because a rope moves: it
 * comes off the quad in the spring, gets shortened, sits in the shop for a season and goes on the
 * triple, and its whole history follows it.
 */
@Entity
@Table(name = "haul_ropes")
@Check(name = "ck_haul_ropes_splices_not_negative", constraints = "splices >= 0")
public class HaulRopeRow {

    @Id
    @JavaType(ColumnTypes.RopeSerialKey.class)
    @Column(name = "serial", length = ROPE_SERIAL_LENGTH, nullable = false)
    private RopeSerial serial;

    /** What the rope was made to, brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "spec_id", nullable = false, foreignKey = @ForeignKey(name = "fk_haul_ropes_spec_id_rope_specs"))
    private RopeSpecRow spec;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH)
    private LiftCode liftCode;

    @Column(name = "installed", nullable = false)
    private LocalDate installed;

    @Column(name = "length", nullable = false)
    private Length length;

    @Column(name = "splices", nullable = false)
    private int splices = 1;

    /** The machine, written through {@code lift_code} and brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_haul_ropes_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected HaulRopeRow() {}

    /**
     * A spare rope with one splice in it.
     *
     * @param serial the mill serial
     * @param spec what it was made to
     * @param installed when it was installed
     * @param length how long it is
     */
    public HaulRopeRow(RopeSerial serial, RopeSpecRow spec, LocalDate installed, Length length) {
        this.serial = serial;
        this.spec = spec;
        this.installed = installed;
        this.length = length;
    }

    public RopeSerial getSerial() {
        return serial;
    }

    public RopeSpecRow getSpec() {
        return spec;
    }

    public void setSpec(RopeSpecRow spec) {
        this.spec = spec;
    }

    /**
     * The code of the machine the rope is on.
     *
     * @return the code, or empty for a spare
     */
    public Optional<LiftCode> getLiftCode() {
        return Optional.ofNullable(liftCode);
    }

    /**
     * Put the rope on a machine by its code, or take it off. The machine itself is read again on
     * the next load.
     *
     * @param liftCode the machine's code, or null to leave the rope as a spare
     */
    public void setLiftCode(LiftCode liftCode) {
        this.liftCode = liftCode;
        if (lift != null && !lift.getCode().equals(liftCode)) {
            lift = null;
        }
    }

    /**
     * The machine the rope is on.
     *
     * @return the machine, or empty for a spare
     */
    public Optional<LiftRow> getLift() {
        return Optional.ofNullable(lift);
    }

    /**
     * Put the rope on a machine, which is written with the rope when it is new.
     *
     * @param lift the machine
     */
    public void setLift(LiftRow lift) {
        this.lift = Objects.requireNonNull(lift, "lift");
        this.liftCode = lift.getCode();
    }

    void takeOff() {
        this.lift = null;
        this.liftCode = null;
    }

    public LocalDate getInstalled() {
        return installed;
    }

    public void setInstalled(LocalDate installed) {
        this.installed = installed;
    }

    public Length getLength() {
        return length;
    }

    public void setLength(Length length) {
        this.length = length;
    }

    public int getSplices() {
        return splices;
    }

    public void setSplices(int splices) {
        this.splices = splices;
    }
}
