package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One stop of a machine, against the machine rather than the day: one a machine a moment, and it
 * restarts no earlier than it stopped.
 */
@Entity
@Table(
        name = "stops",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_stop_a_moment",
                        columnNames = {"lift_code", "stopped"}),
        // Asking what is stopped right now is the commonest question on the hill, and it was
        // reading every stop of every season to answer it.
        indexes = @Index(name = "ix_stops_still_open", columnList = "restarted, stopped"))
@Check(name = "ck_stops_restart_after_stop", constraints = "restarted is null or restarted >= stopped")
public class StopRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private StopKind kind;

    @Column(name = "stopped", nullable = false)
    private LocalDateTime stopped;

    @Column(name = "stopped_by", length = 64, nullable = false)
    private String stoppedBy;

    @Column(name = "note", length = 500, nullable = false)
    private String note = "";

    @Column(name = "restarted")
    private LocalDateTime restarted;

    @Column(name = "restarted_by", length = 64)
    private String restartedBy;

    @Column(name = "line_checked", nullable = false)
    private boolean lineChecked;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_stops_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected StopRow() {}

    /**
     * A stop, still stopped, with no note and the line not yet checked.
     *
     * @param liftCode the machine
     * @param kind what stopped it
     * @param stopped when it stopped
     * @param stoppedBy who stopped it
     */
    public StopRow(LiftCode liftCode, StopKind kind, LocalDateTime stopped, String stoppedBy) {
        this.liftCode = liftCode;
        this.kind = kind;
        this.stopped = stopped;
        this.stoppedBy = stoppedBy;
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

    public StopKind getKind() {
        return kind;
    }

    public void setKind(StopKind kind) {
        this.kind = kind;
    }

    public LocalDateTime getStopped() {
        return stopped;
    }

    public void setStopped(LocalDateTime stopped) {
        this.stopped = stopped;
    }

    public String getStoppedBy() {
        return stoppedBy;
    }

    public void setStoppedBy(String stoppedBy) {
        this.stoppedBy = stoppedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Optional<LocalDateTime> getRestarted() {
        return Optional.ofNullable(restarted);
    }

    /**
     * Record the restart.
     *
     * @param restarted when the machine ran again, or null while it is still stopped
     */
    public void setRestarted(LocalDateTime restarted) {
        this.restarted = restarted;
    }

    public Optional<String> getRestartedBy() {
        return Optional.ofNullable(restartedBy);
    }

    /**
     * Record who restarted it.
     *
     * @param restartedBy who restarted it, or null while it is still stopped
     */
    public void setRestartedBy(String restartedBy) {
        this.restartedBy = restartedBy;
    }

    public boolean isLineChecked() {
        return lineChecked;
    }

    public void setLineChecked(boolean lineChecked) {
        this.lineChecked = lineChecked;
    }
}
