package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.FILING_NUMBER_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.persistence.ColumnTypes;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.JavaType;

/**
 * One filing with the board, by the number the board gave it.
 *
 * <p>An incident and its filing are a row each rather than one row with a nullable half, because
 * the two are written by different people at different times and the gap between them is the thing
 * the board asks about. The link is the incident's own column, empty until somebody files, and one
 * filing can answer for more than one incident.
 */
@Entity
@Table(name = "filings", indexes = @Index(name = "ix_filings_by_machine", columnList = "lift_code, filed"))
public class FilingRow {

    @Id
    @JavaType(ColumnTypes.FilingNumberKey.class)
    @Column(name = "number", length = FILING_NUMBER_LENGTH, nullable = false)
    private FilingNumber number;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private FilingKind kind;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "filed", nullable = false)
    private LocalDateTime filed;

    @Column(name = "about", length = 1000, nullable = false)
    private String about;

    @Column(name = "filed_by", length = 64, nullable = false)
    private String filedBy;

    @Column(name = "acknowledged")
    private LocalDate acknowledged;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_filings_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "filing")
    private List<IncidentRow> incidents = new ArrayList<>();

    /** For the persistence provider. */
    protected FilingRow() {}

    /**
     * A filing the board has not acknowledged yet.
     *
     * @param number the number the board gave it
     * @param kind what was filed
     * @param liftCode the machine it is about
     * @param filed when it was filed
     * @param about what it says
     * @param filedBy who filed it
     */
    public FilingRow(
            FilingNumber number,
            FilingKind kind,
            LiftCode liftCode,
            LocalDateTime filed,
            String about,
            String filedBy) {
        this.number = number;
        this.kind = kind;
        this.liftCode = liftCode;
        this.filed = filed;
        this.about = about;
        this.filedBy = filedBy;
    }

    public FilingNumber getNumber() {
        return number;
    }

    public FilingKind getKind() {
        return kind;
    }

    public void setKind(FilingKind kind) {
        this.kind = kind;
    }

    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public LocalDateTime getFiled() {
        return filed;
    }

    public void setFiled(LocalDateTime filed) {
        this.filed = filed;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getFiledBy() {
        return filedBy;
    }

    public void setFiledBy(String filedBy) {
        this.filedBy = filedBy;
    }

    public Optional<LocalDate> getAcknowledged() {
        return Optional.ofNullable(acknowledged);
    }

    /**
     * Record when the board acknowledged the filing.
     *
     * @param acknowledged the day, or null for not yet
     */
    public void setAcknowledged(LocalDate acknowledged) {
        this.acknowledged = acknowledged;
    }

    /**
     * The incidents this filing answers for, as last read. An incident is filed from its own row.
     *
     * @return the incidents, read only
     */
    public List<IncidentRow> getIncidents() {
        return Collections.unmodifiableList(incidents);
    }

    /** A filing withdrawn leaves the incidents it answered for on file, unfiled again. */
    @PreRemove
    void leaveTheIncidentsUnfiled() {
        for (IncidentRow incident : incidents) {
            incident.unfile();
        }
    }
}
