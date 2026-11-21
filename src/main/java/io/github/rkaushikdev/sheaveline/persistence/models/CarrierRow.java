package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.plant.CarrierType;
import io.github.rkaushikdev.sheaveline.plant.GripType;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One chair, cabin or tow in a machine's fleet, with its grip.
 *
 * <p>The grip lives on the carrier's row, every column of it nullable, because a carrier off the
 * line in the shop has no grip and a grip has no identity apart from the carrier it holds.
 */
@Entity
@Table(
        name = "carriers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_number_a_fleet",
                        columnNames = {"lift_code", "number"}))
@Check(name = "ck_carriers_carriers_from_one", constraints = "number >= 1")
public class CarrierRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private CarrierType kind;

    @Column(name = "tare", nullable = false)
    private Load tare;

    @Column(name = "hanger", nullable = false)
    private Length hanger;

    @Column(name = "restraining_bar", nullable = false)
    private boolean restrainingBar;

    @Column(name = "in_service", nullable = false)
    private boolean inService = true;

    @Column(name = "grip_kind", length = ENUM_LENGTH)
    private GripType gripKind;

    @Column(name = "grip_model", length = 32)
    private String gripModel;

    @Column(name = "grip_jaws")
    private Integer gripJaws;

    @Column(name = "grip_spring_pack", length = 32)
    private String gripSpringPack;

    @Column(name = "grip_rated_slip")
    private Load gripRatedSlip;

    @Column(name = "grip_installed")
    private LocalDate gripInstalled;

    /** The machine, written through {@code lift_code} and brought in with this row when new. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_carriers_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected CarrierRow() {}

    /**
     * A carrier in service, with no restraining bar and no grip, in no fleet yet.
     *
     * @param number its number in the fleet, from one
     * @param kind what it is
     * @param tare what it weighs empty
     * @param hanger how long its hanger is
     */
    public CarrierRow(int number, CarrierType kind, Load tare, Length hanger) {
        this.number = number;
        this.kind = kind;
        this.tare = tare;
        this.hanger = hanger;
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
     * @return the code, or null until the carrier is in a fleet
     */
    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public CarrierType getKind() {
        return kind;
    }

    public void setKind(CarrierType kind) {
        this.kind = kind;
    }

    public Load getTare() {
        return tare;
    }

    public void setTare(Load tare) {
        this.tare = tare;
    }

    public Length getHanger() {
        return hanger;
    }

    public void setHanger(Length hanger) {
        this.hanger = hanger;
    }

    public boolean isRestrainingBar() {
        return restrainingBar;
    }

    public void setRestrainingBar(boolean restrainingBar) {
        this.restrainingBar = restrainingBar;
    }

    public boolean isInService() {
        return inService;
    }

    public void setInService(boolean inService) {
        this.inService = inService;
    }

    public Optional<GripType> getGripKind() {
        return Optional.ofNullable(gripKind);
    }

    /**
     * Say what kind of grip the carrier has.
     *
     * @param gripKind the kind, or null for a carrier with no grip
     */
    public void setGripKind(GripType gripKind) {
        this.gripKind = gripKind;
    }

    public Optional<String> getGripModel() {
        return Optional.ofNullable(gripModel);
    }

    /**
     * Give the grip's model.
     *
     * @param gripModel the model, or null for none
     */
    public void setGripModel(String gripModel) {
        this.gripModel = gripModel;
    }

    public Optional<Integer> getGripJaws() {
        return Optional.ofNullable(gripJaws);
    }

    /**
     * Give the grip's jaw count.
     *
     * @param gripJaws the jaws, or null for none
     */
    public void setGripJaws(Integer gripJaws) {
        this.gripJaws = gripJaws;
    }

    public Optional<String> getGripSpringPack() {
        return Optional.ofNullable(gripSpringPack);
    }

    /**
     * Give the grip's spring pack.
     *
     * @param gripSpringPack the spring pack, or null for none
     */
    public void setGripSpringPack(String gripSpringPack) {
        this.gripSpringPack = gripSpringPack;
    }

    public Optional<Load> getGripRatedSlip() {
        return Optional.ofNullable(gripRatedSlip);
    }

    /**
     * Give the load the grip is rated to hold before it slips.
     *
     * @param gripRatedSlip the rating, or null for none
     */
    public void setGripRatedSlip(Load gripRatedSlip) {
        this.gripRatedSlip = gripRatedSlip;
    }

    public Optional<LocalDate> getGripInstalled() {
        return Optional.ofNullable(gripInstalled);
    }

    /**
     * Say when the grip went on.
     *
     * @param gripInstalled the day, or null for none
     */
    public void setGripInstalled(LocalDate gripInstalled) {
        this.gripInstalled = gripInstalled;
    }
}
