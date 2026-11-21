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

/** One magnetic test of a rope by a contractor's instrument: one test a rope a day. */
@Entity
@Table(
        name = "magnetic_tests",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_test_a_day",
                        columnNames = {"rope_serial", "taken"}))
public class MagneticTestRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "rope_serial", length = ROPE_SERIAL_LENGTH, nullable = false)
    private RopeSerial ropeSerial;

    @Column(name = "taken", nullable = false)
    private LocalDate taken;

    @Column(name = "contractor", length = 64, nullable = false)
    private String contractor;

    @Column(name = "instrument", length = 32, nullable = false)
    private String instrument;

    /** The rope, read through {@code rope_serial}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "rope_serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_magnetic_tests_rope_serial_haul_ropes"))
    private HaulRopeRow rope;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IndicationRow> indications = new ArrayList<>();

    /** For the persistence provider. */
    protected MagneticTestRow() {}

    /**
     * A test with no indications yet.
     *
     * @param ropeSerial the rope tested
     * @param taken the day it was tested
     * @param contractor who ran the test
     * @param instrument what it was run with
     */
    public MagneticTestRow(RopeSerial ropeSerial, LocalDate taken, String contractor, String instrument) {
        this.ropeSerial = ropeSerial;
        this.taken = taken;
        this.contractor = contractor;
        this.instrument = instrument;
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

    public void setTaken(LocalDate taken) {
        this.taken = taken;
    }

    public String getContractor() {
        return contractor;
    }

    public void setContractor(String contractor) {
        this.contractor = contractor;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    /**
     * What the instrument flagged.
     *
     * @return the indications, read only
     */
    public List<IndicationRow> getIndications() {
        return Collections.unmodifiableList(indications);
    }

    /**
     * Add an indication to the test.
     *
     * @param indication the indication, which now points back at this test
     */
    public void addIndication(IndicationRow indication) {
        indication.flaggedBy(this);
        indications.add(indication);
    }
}
