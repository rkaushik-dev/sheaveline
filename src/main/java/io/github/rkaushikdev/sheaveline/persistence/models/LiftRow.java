package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JavaType;

/**
 * One machine on file, with everything bolted to it.
 *
 * <p>The terminals, towers, carriers and ground shots belong to the machine and go when it goes.
 * The rope does not: a rope moves between machines, so a machine taken off file leaves its rope on
 * file as a spare.
 */
@Entity
@Table(name = "lifts")
@Check(name = "ck_lifts_speed_positive", constraints = "design_speed > 0")
public class LiftRow {

    @Id
    @JavaType(ColumnTypes.LiftCodeKey.class)
    @Column(name = "code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode code;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "lift_class", length = ENUM_LENGTH, nullable = false)
    private LiftClass liftClass;

    @Column(name = "bearing", nullable = false)
    private Bearing bearing;

    @Column(name = "design_speed", nullable = false)
    private Speed designSpeed;

    @Column(name = "commissioned", nullable = false)
    private LocalDate commissioned;

    @OneToMany(mappedBy = "lift", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TerminalRow> terminals = new ArrayList<>();

    @OneToMany(mappedBy = "lift", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TowerRow> towers = new ArrayList<>();

    @OneToMany(mappedBy = "lift", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CarrierRow> carriers = new ArrayList<>();

    @OneToMany(mappedBy = "lift", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SurveyShotRow> shots = new ArrayList<>();

    @OneToMany(mappedBy = "lift")
    private List<HaulRopeRow> ropes = new ArrayList<>();

    /** For the persistence provider. */
    protected LiftRow() {}

    /**
     * A machine with nothing bolted to it yet.
     *
     * @param code the code painted on the terminal
     * @param name what the trail map calls it
     * @param liftClass what kind of machine it is
     * @param bearing the line's bearing up the hill
     * @param designSpeed the haul rope's design speed
     * @param commissioned when it first carried the public
     */
    public LiftRow(
            LiftCode code,
            String name,
            LiftClass liftClass,
            Bearing bearing,
            Speed designSpeed,
            LocalDate commissioned) {
        this.code = code;
        this.name = name;
        this.liftClass = liftClass;
        this.bearing = bearing;
        this.designSpeed = designSpeed;
        this.commissioned = commissioned;
    }

    public LiftCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LiftClass getLiftClass() {
        return liftClass;
    }

    public void setLiftClass(LiftClass liftClass) {
        this.liftClass = liftClass;
    }

    public Bearing getBearing() {
        return bearing;
    }

    public void setBearing(Bearing bearing) {
        this.bearing = bearing;
    }

    public Speed getDesignSpeed() {
        return designSpeed;
    }

    public void setDesignSpeed(Speed designSpeed) {
        this.designSpeed = designSpeed;
    }

    public LocalDate getCommissioned() {
        return commissioned;
    }

    public void setCommissioned(LocalDate commissioned) {
        this.commissioned = commissioned;
    }

    /**
     * The terminals.
     *
     * @return the terminals, read only
     */
    public List<TerminalRow> getTerminals() {
        return Collections.unmodifiableList(terminals);
    }

    /**
     * Bolt a terminal to this machine.
     *
     * @param terminal the terminal, which now points back at this machine
     */
    public void addTerminal(TerminalRow terminal) {
        terminal.placeOn(this);
        terminals.add(terminal);
    }

    /**
     * The towers.
     *
     * @return the towers, read only
     */
    public List<TowerRow> getTowers() {
        return Collections.unmodifiableList(towers);
    }

    /**
     * Stand a tower on this machine's line.
     *
     * @param tower the tower, which now points back at this machine
     */
    public void addTower(TowerRow tower) {
        tower.placeOn(this);
        towers.add(tower);
    }

    /**
     * The carriers.
     *
     * @return the carriers, read only
     */
    public List<CarrierRow> getCarriers() {
        return Collections.unmodifiableList(carriers);
    }

    /**
     * Put a carrier in this machine's fleet.
     *
     * @param carrier the carrier, which now points back at this machine
     */
    public void addCarrier(CarrierRow carrier) {
        carrier.placeOn(this);
        carriers.add(carrier);
    }

    /**
     * The ground shots along the line.
     *
     * @return the shots, read only
     */
    public List<SurveyShotRow> getShots() {
        return Collections.unmodifiableList(shots);
    }

    /**
     * Add a ground shot to this machine's profile.
     *
     * @param shot the shot, which now points back at this machine
     */
    public void addShot(SurveyShotRow shot) {
        shot.placeOn(this);
        shots.add(shot);
    }

    /**
     * The ropes on this machine, as last read. A rope is put on or taken off from its own row.
     *
     * @return the ropes, read only
     */
    public List<HaulRopeRow> getRopes() {
        return Collections.unmodifiableList(ropes);
    }

    /** A machine taken off file leaves its ropes on file, on no machine. */
    @PreRemove
    void leaveTheRopesAsSpares() {
        for (HaulRopeRow rope : ropes) {
            rope.takeOff();
        }
    }
}
