package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
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
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One weather hold on a machine, against the machine rather than the day, which ends no earlier
 * than it starts. A hold called at four and cleared at nine the next morning belongs to both days.
 */
@Entity
@Table(name = "holds", indexes = @Index(name = "ix_holds_by_machine", columnList = "lift_code, started"))
@Check(name = "ck_holds_hold_ends_after_it_starts", constraints = "ended is null or ended >= started")
public class HoldRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "reason", length = ENUM_LENGTH, nullable = false)
    private HoldReason reason;

    @Column(name = "started", nullable = false)
    private LocalDateTime started;

    @Column(name = "started_by", length = 64, nullable = false)
    private String startedBy;

    @Column(name = "detail", length = 500, nullable = false)
    private String detail = "";

    @Column(name = "ended")
    private LocalDateTime ended;

    @Column(name = "ended_by", length = 64)
    private String endedBy;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_holds_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected HoldRow() {}

    /**
     * A hold still in force, with no detail.
     *
     * @param liftCode the machine held
     * @param reason why it is held
     * @param started when the hold was called
     * @param startedBy who called it
     */
    public HoldRow(LiftCode liftCode, HoldReason reason, LocalDateTime started, String startedBy) {
        this.liftCode = liftCode;
        this.reason = reason;
        this.started = started;
        this.startedBy = startedBy;
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

    public HoldReason getReason() {
        return reason;
    }

    public LocalDateTime getStarted() {
        return started;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Optional<LocalDateTime> getEnded() {
        return Optional.ofNullable(ended);
    }

    /**
     * Record when the hold was cleared.
     *
     * @param ended the moment, or null while the hold is in force
     */
    public void setEnded(LocalDateTime ended) {
        this.ended = ended;
    }

    public Optional<String> getEndedBy() {
        return Optional.ofNullable(endedBy);
    }

    /**
     * Record who cleared the hold.
     *
     * @param endedBy who cleared it, or null while the hold is in force
     */
    public void setEndedBy(String endedBy) {
        this.endedBy = endedBy;
    }
}
