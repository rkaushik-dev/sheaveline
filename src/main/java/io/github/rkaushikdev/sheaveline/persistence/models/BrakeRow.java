package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.BrakeKind;
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

/** One brake on a terminal. A terminal carries one brake of each kind. */
@Entity
@Table(
        name = "brakes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_brake_a_kind",
                        columnNames = {"terminal_id", "kind"}))
public class BrakeRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "terminal_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_brakes_terminal_id_terminals"))
    private TerminalRow terminal;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private BrakeKind kind;

    @Column(name = "torque", nullable = false)
    private Torque torque;

    @Column(name = "hydraulic", nullable = false)
    private boolean hydraulic = true;

    /** For the persistence provider. */
    protected BrakeRow() {}

    /**
     * A hydraulic brake on no terminal yet.
     *
     * @param kind which brake it is
     * @param torque the torque it is set to
     */
    public BrakeRow(BrakeKind kind, Torque torque) {
        this.kind = kind;
        this.torque = torque;
    }

    void fitTo(TerminalRow owner) {
        this.terminal = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public TerminalRow getTerminal() {
        return terminal;
    }

    public BrakeKind getKind() {
        return kind;
    }

    public Torque getTorque() {
        return torque;
    }

    public boolean isHydraulic() {
        return hydraulic;
    }

    public void setHydraulic(boolean hydraulic) {
        this.hydraulic = hydraulic;
    }
}
